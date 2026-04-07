package com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RefreshSchedulerTest {

    // A mutable clock stub for testing — wraps a var so tests can control "now"
    private class MutableClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    // Base instant used as anchor for all test timers
    private val baseNow = Instant.fromEpochSeconds(1_000_000)

    private fun TestScope.createScheduler(clock: Clock = MutableClock(baseNow)): RefreshScheduler =
        RefreshScheduler(this, clock)

    @Test
    fun scheduleAddsEntryToActiveTimers() = runTest {
        val scheduler = createScheduler()
        val expiresAt = baseNow + 60.seconds
        scheduler.schedule("transit:SHIP-1", expiresAt) { }
        assertEquals(1, scheduler.activeTimers.value.size)
        assertTrue(scheduler.activeTimers.value.containsKey("transit:SHIP-1"))
    }

    @Test
    fun scheduleWithSameIdReplacesEntry() = runTest {
        val scheduler = createScheduler()
        val first = baseNow + 60.seconds
        val second = baseNow + 30.seconds
        scheduler.schedule("transit:SHIP-1", first) { }
        scheduler.schedule("transit:SHIP-1", second) { }
        assertEquals(1, scheduler.activeTimers.value.size)
        assertEquals(second, scheduler.activeTimers.value["transit:SHIP-1"]?.expiresAt)
    }

    @Test
    fun cancelRemovesEntry() = runTest {
        val scheduler = createScheduler()
        scheduler.schedule("transit:SHIP-1", baseNow + 60.seconds) { }
        scheduler.cancel("transit:SHIP-1")
        assertTrue(scheduler.activeTimers.value.isEmpty())
    }

    @Test
    fun cancelByPrefixRemovesMatchingEntries() = runTest {
        val scheduler = createScheduler()
        val future = baseNow + 60.seconds
        scheduler.schedule("transit:SHIP-1", future) { }
        scheduler.schedule("transit:SHIP-2", future) { }
        scheduler.schedule("cooldown:SHIP-1", future) { }
        scheduler.cancelByPrefix("transit:")
        assertEquals(1, scheduler.activeTimers.value.size)
        assertTrue(scheduler.activeTimers.value.containsKey("cooldown:SHIP-1"))
    }

    @Test
    fun timerFiresAfterExpiryPlusOneSecond() = runTest {
        val clock = MutableClock(baseNow)
        val scheduler = RefreshScheduler(this, clock)
        var fired = false
        val expiresAt = baseNow + 10.seconds

        scheduler.schedule("transit:SHIP-1", expiresAt) { fired = true }

        // Advance virtual time so delay(10s + 1s) in the loop completes
        advanceTimeBy(11_001)
        assertTrue(fired, "Timer should have fired after expiry + 1s")
    }

    @Test
    fun timerIsRemovedAfterFiring() = runTest {
        val clock = MutableClock(baseNow)
        val scheduler = RefreshScheduler(this, clock)
        scheduler.schedule("transit:SHIP-1", baseNow + 5.seconds) { }
        advanceTimeBy(7_000)
        assertTrue(scheduler.activeTimers.value.isEmpty())
    }

    @Test
    fun multipleTimersFireInOrder() = runTest {
        val clock = MutableClock(baseNow)
        val scheduler = RefreshScheduler(this, clock)
        val results = mutableListOf<String>()

        scheduler.schedule("first", baseNow + 5.seconds) { results.add("first") }
        scheduler.schedule("second", baseNow + 10.seconds) { results.add("second") }

        advanceTimeBy(5_000) // 5s elapsed — neither should have fired yet (both need +1s)
        assertEquals(emptyList<String>(), results, "No timer should fire before expiry + 1s")

        advanceTimeBy(2_000) // 7s elapsed — first fires at 6s, so it should have fired
        assertEquals(listOf("first"), results)

        advanceTimeBy(12_000) // second fires at 11s from baseNow; 19s total virtual elapsed
        assertEquals(listOf("first", "second"), results)
    }

    @Test
    fun onResumeFiresAlreadyExpiredTimers() = runTest {
        // Clock is already past expiry + 1s
        val pastClock = MutableClock(baseNow + 10.seconds)
        val scheduler = RefreshScheduler(this, pastClock)
        var fired = false
        // Add a timer that expired 5 seconds ago (baseNow - 5s, but clock is at baseNow + 10s)
        scheduler.schedule("transit:SHIP-1", baseNow - 5.seconds) { fired = true }
        scheduler.onResume()
        advanceTimeBy(100) // let the launched coroutine execute
        assertTrue(fired)
    }

    @Test
    fun onResumeRemovesExpiredEntries() = runTest {
        val pastClock = MutableClock(baseNow + 10.seconds)
        val scheduler = RefreshScheduler(this, pastClock)
        scheduler.schedule("transit:SHIP-1", baseNow - 5.seconds) { }
        scheduler.onResume()
        advanceTimeBy(100)
        assertFalse(scheduler.activeTimers.value.containsKey("transit:SHIP-1"))
    }

    @Test
    fun actionFailureLogsAndRemovesEntry() = runTest {
        val clock = MutableClock(baseNow)
        val scheduler = RefreshScheduler(this, clock)
        scheduler.schedule("transit:SHIP-1", baseNow + 2.seconds) {
            throw RuntimeException("API error")
        }
        advanceTimeBy(4_000)
        assertTrue(scheduler.activeTimers.value.isEmpty())
    }
}
