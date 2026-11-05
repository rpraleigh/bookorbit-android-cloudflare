package com.bookorbit.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudflareAccessEventsTest {

    private fun collect(events: CloudflareAccessEvents, block: () -> Unit): List<Boolean> {
        val seen = mutableListOf<Boolean>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch { events.rejections.collect { seen.add(it) } }
        block()
        job.cancel()
        return seen
    }

    @Test
    fun repeatedRejectionsWithinTheGapProduceOneEvent() {
        val events = CloudflareAccessEvents()
        val seen = collect(events) {
            events.notifyRejected(true, now = 1_000)
            events.notifyRejected(true, now = 1_000 + CloudflareAccessEvents.MIN_GAP_MS - 1)
        }
        assertEquals(listOf(true), seen)
    }

    @Test
    fun anotherEventIsAllowedAfterTheGap() {
        val events = CloudflareAccessEvents()
        val seen = collect(events) {
            events.notifyRejected(true, now = 1_000)
            events.notifyRejected(false, now = 1_000 + CloudflareAccessEvents.MIN_GAP_MS)
        }
        assertEquals(listOf(true, false), seen)
    }

    @Test
    fun resetAllowsTheNextEventImmediately() {
        val events = CloudflareAccessEvents()
        val seen = collect(events) {
            events.notifyRejected(true, now = 1_000)
            events.reset()
            events.notifyRejected(true, now = 1_001)
        }
        assertEquals(listOf(true, true), seen)
    }
}
