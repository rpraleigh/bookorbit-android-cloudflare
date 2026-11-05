package com.bookorbit.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.R
import com.bookorbit.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val repo: AuthRepository,
) : ViewModel() {

    data class UiState(
        val submitting: Boolean = false,
        val error: UiText? = null,
        /** The account exists but the automatic sign-in that followed did not work. */
        val accountCreated: Boolean = false,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    /**
     * Creates the account, then signs in once with the same credentials. A successful sign-in flips the
     * session to SignedIn, which swaps the whole auth flow out, so there is nothing to navigate to here.
     * Nothing is retried: registration allows 3 requests a minute and sign-in 5.
     */
    fun register(username: String, name: String, email: String, password: String) {
        val state = _ui.value
        if (state.submitting || state.accountCreated) return
        _ui.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                try {
                    repo.register(username, name, email.trim(), password)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _ui.update { it.copy(error = registerFailureText(classifyRegisterFailure(e))) }
                    return@launch
                }
                try {
                    repo.login(username, password)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _ui.update { it.copy(accountCreated = true, error = UiText.of(R.string.register_created_sign_in_failed)) }
                }
            } finally {
                _ui.update { it.copy(submitting = false) }
            }
        }
    }
}

internal fun registerFailureText(outcome: RegisterOutcome): UiText = when (outcome.failure) {
    RegisterFailure.TAKEN -> UiText.of(R.string.register_err_taken)
    RegisterFailure.CLOSED -> UiText.of(R.string.register_err_closed)
    RegisterFailure.INVALID -> UiText.of(R.string.register_err_invalid)
    RegisterFailure.THROTTLED -> UiText.of(R.string.register_err_throttled)
    RegisterFailure.CLOUDFLARE_REJECTED -> UiText.of(R.string.server_err_cloudflare_rejected)
    RegisterFailure.CLOUDFLARE_REQUIRED -> UiText.of(R.string.server_err_cloudflare_required)
    RegisterFailure.NETWORK -> UiText.of(R.string.server_err_connect)
    RegisterFailure.HTTP_OTHER -> UiText.of(R.string.register_err_http, outcome.httpCode ?: 0)
    RegisterFailure.UNEXPECTED -> UiText.of(R.string.register_err_unexpected)
}
