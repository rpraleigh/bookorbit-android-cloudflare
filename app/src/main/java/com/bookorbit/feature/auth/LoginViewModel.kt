package com.bookorbit.feature.auth

import com.bookorbit.R
import com.bookorbit.ui.UiText
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.OidcProviderPublic
import com.bookorbit.core.network.CloudflareAccessException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repo: AuthRepository,
    private val oidc: OidcManager,
    session: SessionManager,
) : ViewModel() {

    data class UiState(
        val submitting: Boolean = false,
        val oidcLoadingSlug: String? = null,
        val error: UiText? = null,
        val providers: List<OidcProviderPublic> = emptyList(),
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    val serverUrl: String? = session.serverUrl

    init {
        viewModelScope.launch {
            _ui.update { it.copy(providers = repo.oidcProviders()) }
        }
    }

    /** A successful login flips SessionManager state to SignedIn, which swaps in the main shell. */
    fun login(username: String, password: String) {
        if (username.isBlank() || password.isEmpty()) return
        _ui.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                repo.login(username, password)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(error = loginError(e)) }
            } finally {
                _ui.update { it.copy(submitting = false) }
            }
        }
    }

    /** Only a 401 means bad credentials; everything else is reported as what it actually is. */
    private fun loginError(e: Exception): UiText {
        val causes = generateSequence<Throwable>(e) { it.cause }.toList()
        val access = causes.filterIsInstance<CloudflareAccessException>().firstOrNull()
        val http = causes.filterIsInstance<HttpException>().firstOrNull()
        return when {
            access != null && access.credentialsConfigured -> UiText.of(R.string.server_err_cloudflare_rejected)
            access != null -> UiText.of(R.string.server_err_cloudflare_required)
            http != null && http.code() == 401 -> UiText.of(R.string.login_err_invalid)
            http != null -> UiText.of(R.string.login_err_http, http.code())
            causes.any { it is IOException } -> UiText.of(R.string.server_err_connect)
            else -> UiText.of(R.string.login_err_unexpected)
        }
    }

    /** Builds the OIDC auth Intent and hands it to [launch] (an ActivityResultLauncher). */
    fun beginOidc(provider: OidcProviderPublic, launch: (Intent) -> Unit) {
        _ui.update { it.copy(oidcLoadingSlug = provider.slug, error = null) }
        viewModelScope.launch {
            try {
                launch(oidc.buildAuthIntent(provider))
            } catch (e: Exception) {
                _ui.update { it.copy(oidcLoadingSlug = null, error = e.message?.let { m -> UiText.raw(m) } ?: UiText.of(R.string.login_err_oidc)) }
            }
        }
    }

    /** Handles the AppAuth result; commits a successful login or surfaces a non-cancel error. */
    fun completeOidc(data: Intent?) {
        viewModelScope.launch {
            try {
                repo.commitOidc(oidc.completeLogin(data))
            } catch (_: OidcCancelledException) {
                // User dismissed the browser — stay silent.
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.message?.let { m -> UiText.raw(m) } ?: UiText.of(R.string.login_err_oidc)) }
            } finally {
                _ui.update { it.copy(oidcLoadingSlug = null) }
            }
        }
    }
}
