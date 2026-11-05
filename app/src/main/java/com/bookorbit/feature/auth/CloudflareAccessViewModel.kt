package com.bookorbit.feature.auth

import androidx.lifecycle.ViewModel
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.network.CloudflareAccessEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Edits the Cloudflare Access service token after setup: from Settings at any time, and as a prompt
 * when Access rejects the saved token (see [CloudflareAccessEvents]).
 */
@HiltViewModel
class CloudflareAccessViewModel @Inject constructor(
    private val session: SessionManager,
    private val events: CloudflareAccessEvents,
) : ViewModel() {

    private val _clientId = MutableStateFlow(session.cfClientId)

    /** The saved Client ID, or null when no token is saved. The secret is never exposed. */
    val clientId: StateFlow<String?> = _clientId.asStateFlow()

    /** `true` = a saved token was rejected, `false` = Access blocked and no token is saved. */
    val rejections: SharedFlow<Boolean> = events.rejections

    /**
     * Saves the token. A blank secret next to the already-saved Client ID keeps the saved secret,
     * unless [requireNewSecret] is set (the saved one was just rejected, so keeping it helps nobody).
     * Returns false, saving nothing, when no usable secret is available.
     */
    fun save(rawId: String, rawSecret: String, requireNewSecret: Boolean): Boolean {
        val id = rawId.trim()
        if (id.isEmpty()) return false
        var secret = rawSecret.trim()
        if (secret.isEmpty() && !requireNewSecret && id == session.cfClientId) {
            secret = session.cfClientSecret.orEmpty()
        }
        if (secret.isEmpty()) return false
        session.setCloudflareAccess(id, secret)
        events.reset()
        _clientId.value = session.cfClientId
        return true
    }

    fun remove() {
        session.setCloudflareAccess(null, null)
        events.reset()
        _clientId.value = null
    }
}
