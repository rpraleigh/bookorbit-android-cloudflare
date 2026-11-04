package com.bookorbit.core.auth

import com.bookorbit.core.model.AuthUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Top-level auth state used to gate navigation (server-setup -> login -> main). */
sealed interface SessionState {
    /** Initial state before persisted credentials have been loaded. */
    data object Loading : SessionState

    /** No server configured yet — show the server-setup screen. */
    data object NeedsServer : SessionState

    /** Server known but not authenticated — show login. */
    data object SignedOut : SessionState

    data class SignedIn(val user: AuthUser) : SessionState
}

/**
 * Single source of truth for auth: server URL, access token, and the signed-in user.
 *
 * The access token and server URL are cached in memory (volatile) for synchronous access from the
 * OkHttp interceptors, and mirrored into [SecureStorage] for persistence across launches. The
 * refresh token lives only in the persisted cookie jar.
 */
@Singleton
class SessionManager @Inject constructor(
    private val storage: SecureStorage,
    private val json: Json,
) {
    @Volatile
    var accessToken: String? = null
        private set

    @Volatile
    var serverUrl: String? = null
        private set

    /** Cloudflare Access service token for the configured server; both null when Access is not used. */
    @Volatile
    var cfClientId: String? = null
        private set

    @Volatile
    var cfClientSecret: String? = null
        private set

    val cloudflareAccessEnabled: Boolean
        get() = !cfClientId.isNullOrBlank() && !cfClientSecret.isNullOrBlank()

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** The signed-in user, or null when not authenticated. */
    val currentUser: AuthUser? get() = (_state.value as? SessionState.SignedIn)?.user

    /** Load persisted credentials once at startup and derive the initial [SessionState]. */
    fun bootstrap() {
        serverUrl = storage.getString(SecureStorage.KEY_SERVER_URL)
        accessToken = storage.getString(SecureStorage.KEY_ACCESS_TOKEN)
        cfClientId = storage.getString(SecureStorage.KEY_CF_CLIENT_ID)
        cfClientSecret = storage.getString(SecureStorage.KEY_CF_CLIENT_SECRET)
        val user = storage.getString(SecureStorage.KEY_USER)
            ?.let { runCatching { json.decodeFromString<AuthUser>(it) }.getOrNull() }

        // Data from before servers were kept apart belongs to whichever server this install already uses.
        if (!serverUrl.isNullOrBlank() && !accessToken.isNullOrBlank() && storage.getString(SecureStorage.KEY_DATA_OWNER) == null) {
            storage.putString(SecureStorage.KEY_DATA_OWNER, key(serverUrl!!))
        }

        _state.value = when {
            serverUrl.isNullOrBlank() -> SessionState.NeedsServer
            accessToken.isNullOrBlank() || user == null -> SessionState.SignedOut
            else -> SessionState.SignedIn(user)
        }
    }

    /** Scope the open database was created for (see [dataScope]); null until it is first opened. */
    @Volatile
    var activeDataScope: String? = null

    /**
     * Names the on-device data (database file and download folders) of the configured server. Book ids
     * are per server, so each server keeps its own. The first server used owns the original data and has
     * an empty scope, which keeps existing installs and their downloads exactly where they were.
     */
    val dataScope: String
        get() = DataScope.of(storage.getString(SecureStorage.KEY_DATA_OWNER), serverUrl)

    private fun key(url: String) = DataScope.key(url)

    /** True when the configured server's data is not what the open database holds (needs an app restart). */
    fun dataScopeChanged(): Boolean = activeDataScope?.let { it != dataScope } == true

    fun setServerUrl(url: String) {
        serverUrl = url.trimEnd('/')
        storage.putString(SecureStorage.KEY_SERVER_URL, serverUrl)
        if (_state.value is SessionState.Loading || _state.value is SessionState.NeedsServer) {
            _state.value = SessionState.SignedOut
        }
    }

    /** Stores (or, when either value is blank, clears) the Cloudflare Access service token. */
    fun setCloudflareAccess(clientId: String?, clientSecret: String?) {
        val id = clientId?.trim().orEmpty()
        val secret = clientSecret?.trim().orEmpty()
        if (id.isEmpty() || secret.isEmpty()) {
            cfClientId = null
            cfClientSecret = null
            storage.remove(SecureStorage.KEY_CF_CLIENT_ID)
            storage.remove(SecureStorage.KEY_CF_CLIENT_SECRET)
        } else {
            cfClientId = id
            cfClientSecret = secret
            storage.putString(SecureStorage.KEY_CF_CLIENT_ID, id)
            storage.putString(SecureStorage.KEY_CF_CLIENT_SECRET, secret)
        }
    }

    /** Reverts to the no-server state (used when a setup-status probe fails during onboarding). */
    fun clearServer() {
        serverUrl = null
        storage.remove(SecureStorage.KEY_SERVER_URL)
        setCloudflareAccess(null, null)
        _state.value = SessionState.NeedsServer
    }

    fun signIn(token: String, user: AuthUser) {
        // The first server anyone signs in to owns the original data; later ones get their own.
        if (storage.getString(SecureStorage.KEY_DATA_OWNER) == null) serverUrl?.let { storage.putString(SecureStorage.KEY_DATA_OWNER, key(it)) }
        accessToken = token
        storage.putString(SecureStorage.KEY_ACCESS_TOKEN, token)
        storage.putString(SecureStorage.KEY_USER, json.encodeToString(AuthUser.serializer(), user))
        _state.value = SessionState.SignedIn(user)
    }

    /** Called by [com.bookorbit.core.network.TokenRefresher] after a successful silent refresh. */
    fun updateAccessToken(token: String) {
        accessToken = token
        storage.putString(SecureStorage.KEY_ACCESS_TOKEN, token)
    }

    /** Clears the token/user but keeps the server URL so the user lands back on login. */
    fun signOut() {
        accessToken = null
        storage.remove(SecureStorage.KEY_ACCESS_TOKEN)
        storage.remove(SecureStorage.KEY_USER)
        storage.remove(SecureStorage.KEY_COOKIES)
        if (serverUrl.isNullOrBlank()) {
            _state.value = SessionState.NeedsServer
        } else {
            _state.value = SessionState.SignedOut
        }
    }
}
