package com.bookorbit.feature.auth

import com.bookorbit.R
import com.bookorbit.ui.UiText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.network.CloudflareAccessException
import com.bookorbit.core.network.UserCaSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.net.ssl.SSLException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServerSetupViewModel @Inject constructor(
    private val session: SessionManager,
    private val repo: AuthRepository,
    private val userCaSettings: UserCaSettings,
) : ViewModel() {

    data class UiState(val loading: Boolean = false, val error: UiText? = null)

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    val currentServerUrl: String? get() = session.serverUrl

    val currentCfClientId: String? get() = session.cfClientId

    fun trustsUserCas(url: String): Boolean = userCaSettings.isEnabledForUrl(url)

    /**
     * Sets the server URL and probes setup-status to confirm it's reachable. On failure the URL is
     * reverted so the user stays on this screen.
     */
    fun connect(
        rawUrl: String,
        trustUserCas: Boolean,
        cfClientId: String,
        cfClientSecret: String,
        onConnected: () -> Unit,
    ) {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { userCaSettings.setEnabledForUrl(trimmed, trustUserCas) }
                // A blank secret next to the already-stored client ID means "keep the stored secret".
                val id = cfClientId.trim()
                val secret = cfClientSecret.trim().ifEmpty {
                    if (id.isNotEmpty() && id == session.cfClientId) session.cfClientSecret.orEmpty() else ""
                }
                session.setCloudflareAccess(id, secret)
                session.setServerUrl(trimmed)
                // An http:// address that the server redirects to https:// breaks sign-in (the redirect
                // turns the POST into a GET), so save the https address when that is what answered.
                repo.probeServer()?.let { upgraded ->
                    withContext(Dispatchers.IO) { userCaSettings.setEnabledForUrl(upgraded, trustUserCas) }
                    session.setServerUrl(upgraded)
                }
                onConnected()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                session.clearServer()
                val causes = generateSequence<Throwable>(e) { it.cause }.toList()
                val access = causes.filterIsInstance<CloudflareAccessException>().firstOrNull()
                _ui.update {
                    it.copy(error = UiText.of(when {
                        access != null && access.credentialsConfigured -> R.string.server_err_cloudflare_rejected
                        access != null -> R.string.server_err_cloudflare_required
                        causes.any { c -> c is SSLException } -> R.string.server_err_certificate
                        else -> R.string.server_err_connect
                    }))
                }
            } finally {
                _ui.update { it.copy(loading = false) }
            }
        }
    }
}
