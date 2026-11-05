package com.bookorbit.core.network

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the UI that Cloudflare Access turned a request away, so it can offer to fix the service
 * token. Every OkHttp client shares [CloudflareAccessInterceptor], so a failure on any of them
 * (API calls, covers, audiobook streaming) ends up here.
 *
 * One bad token makes every request fail at once, so events are rate limited: at most one per
 * [MIN_GAP_MS] until [reset] is called (when the user saves a new token).
 */
@Singleton
class CloudflareAccessEvents @Inject constructor() {
    private val _rejections = MutableSharedFlow<Boolean>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Emits `true` when a saved token was rejected, `false` when Access blocked and no token is saved. */
    val rejections: SharedFlow<Boolean> = _rejections.asSharedFlow()

    private val lastEmittedAt = AtomicLong(NEVER)

    fun notifyRejected(credentialsConfigured: Boolean, now: Long = System.currentTimeMillis()) {
        val previous = lastEmittedAt.get()
        if (previous != NEVER && now - previous < MIN_GAP_MS) return
        if (lastEmittedAt.compareAndSet(previous, now)) _rejections.tryEmit(credentialsConfigured)
    }

    /** Allows the next rejection to be reported straight away. */
    fun reset() {
        lastEmittedAt.set(NEVER)
    }

    companion object {
        const val MIN_GAP_MS = 60_000L
        private const val NEVER = Long.MIN_VALUE
    }
}
