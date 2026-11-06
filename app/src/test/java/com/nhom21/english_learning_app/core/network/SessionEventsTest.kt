package com.nhom21.english_learning_app.core.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SessionEvents] verifying the 4 critical production scenarios requested by PM:
 * 1. Multiple concurrent 401 requests.
 * 2. Inactive collector (app in background).
 * 3. Recreated collector (screen rotation/recomposition).
 * 4. User logs in again (session reset and re-arming).
 * 5. Multicast fan-out comparison vs Channel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionEventsTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var sessionEvents: SessionEvents

    @Before
    fun setUp() {
        sessionEvents = SessionEvents()
    }

    @Test
    fun initialState_isNotExpired() {
        assertFalse(sessionEvents.isSessionExpired)
    }

    @Test
    fun scenario1_multipleConcurrent401Requests_emitsExactlyOnce() = testScope.runTest {
        val emittedEvents = mutableListOf<Unit>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sessionEvents.sessionExpired.collect { emittedEvents.add(it) }
        }

        // Simulate 20 concurrent threads/requests hitting 401 simultaneously
        val deferredResults = (1..20).map {
            async {
                sessionEvents.notifySessionExpired()
            }
        }
        val results = deferredResults.awaitAll()

        // Exactly one call returns true (the first to win compareAndSet), others return false
        val successCount = results.count { it }
        val suppressedCount = results.count { !it }

        assertEquals(1, successCount)
        assertEquals(19, suppressedCount)
        assertEquals(1, emittedEvents.size)
        assertTrue(sessionEvents.isSessionExpired)

        job.cancel()
    }

    @Test
    fun scenario2_inactiveCollector_marksSessionExpiredWithoutErrorOrBlock() {
        // App is in background: no active collectors attached
        val triggered = sessionEvents.notifySessionExpired()

        assertTrue(triggered)
        assertTrue(sessionEvents.isSessionExpired)

        // Subsequent notification is suppressed even without collectors
        val secondTrigger = sessionEvents.notifySessionExpired()
        assertFalse(secondTrigger)
    }

    @Test
    fun scenario3_recreatedCollector_doesNotReceiveStaleReplay() = testScope.runTest {
        val initialEvents = mutableListOf<Unit>()
        val initialJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sessionEvents.sessionExpired.collect { initialEvents.add(it) }
        }

        // 401 occurs, initial collector receives the event
        sessionEvents.notifySessionExpired()
        assertEquals(1, initialEvents.size)

        // Activity onDestroy / screen rotation: initial collector is cancelled
        initialJob.cancel()

        // Activity onCreate: new collector attaches
        val recreatedCollectorEvents = mutableListOf<Unit>()
        val recreatedJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sessionEvents.sessionExpired.collect { recreatedCollectorEvents.add(it) }
        }

        // With replay = 0, new collector does NOT receive the old event
        assertEquals(0, recreatedCollectorEvents.size)
        assertTrue(sessionEvents.isSessionExpired)

        recreatedJob.cancel()
    }

    @Test
    fun scenario4_userLogsInAgain_resetsSessionAndReArmsForNewSession() = testScope.runTest {
        // Step 1: Session expires
        sessionEvents.notifySessionExpired()
        assertTrue(sessionEvents.isSessionExpired)

        // Step 2: User successfully logs in again -> resetSession() is called
        sessionEvents.resetSession()
        assertFalse(sessionEvents.isSessionExpired)

        // Step 3: New collector attaches in the new authenticated session
        val newSessionEvents = mutableListOf<Unit>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sessionEvents.sessionExpired.collect { newSessionEvents.add(it) }
        }

        // No stale events replayed to the new session
        assertEquals(0, newSessionEvents.size)

        // Step 4: Later, another 401 occurs in the new session
        val triggered = sessionEvents.notifySessionExpired()
        assertTrue(triggered)
        assertEquals(1, newSessionEvents.size)
        assertTrue(sessionEvents.isSessionExpired)

        job.cancel()
    }

    @Test
    fun scenario5_multicastFanout_deliversToAllSubscribersSimultaneously() = testScope.runTest {
        val navCollector = mutableListOf<Unit>()
        val analyticsCollector = mutableListOf<Unit>()
        val syncWorkerCollector = mutableListOf<Unit>()

        val jobNav = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sessionEvents.sessionExpired.collect { navCollector.add(it) }
        }
        val jobAnalytics = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sessionEvents.sessionExpired.collect { analyticsCollector.add(it) }
        }
        val jobSync = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sessionEvents.sessionExpired.collect { syncWorkerCollector.add(it) }
        }

        sessionEvents.notifySessionExpired()

        // All 3 observers receive the event (unlike Channel which would starve 2 of them)
        assertEquals(1, navCollector.size)
        assertEquals(1, analyticsCollector.size)
        assertEquals(1, syncWorkerCollector.size)

        jobNav.cancel()
        jobAnalytics.cancel()
        jobSync.cancel()
    }
}
