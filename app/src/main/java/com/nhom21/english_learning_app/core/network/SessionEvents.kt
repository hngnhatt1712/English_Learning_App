package com.nhom21.english_learning_app.core.network

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Event bus for session-related lifecycle events per decision D-A15.
 *
 * Robustly addresses 4 critical production scenarios:
 * 1. Multiple concurrent 401 requests: Deduplicated atomically via [AtomicBoolean]; emits only once.
 * 2. Inactive collector (app in background): State is persisted in [isSessionExpired] and token
 *    is synchronously invalidated by TokenManager; UI checks state upon resuming.
 * 3. Recreated collector (screen rotation/recomposition): replay = 0 prevents re-emitting stale
 *    events to new subscribers.
 * 4. User logs in again: [resetSession] clears expiration state and re-arms emission for the new session.
 */
@Singleton
class SessionEvents @Inject constructor() {
    private val _isSessionExpired = AtomicBoolean(false)

    // Decision D-A15: replay = 0 ensures new collectors do NOT receive stale events.
    // extraBufferCapacity = 1 with DROP_OLDEST prevents blocking OkHttp interceptor threads.
    private val _sessionExpired = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    /**
     * Synchronously checks if the current session has been marked as expired.
     */
    val isSessionExpired: Boolean
        get() = _isSessionExpired.get()

    /**
     * Idempotently emits a session expired notification to active listeners.
     * If multiple concurrent 401 requests occur, only the first call triggers an emission.
     *
     * @return true if this call changed the state and emitted, false if already expired.
     */
    fun notifySessionExpired(): Boolean {
        if (_isSessionExpired.compareAndSet(false, true)) {
            _sessionExpired.tryEmit(Unit)
            return true
        }
        return false
    }

    /**
     * Resets the session expiration state. Must be called when the user successfully
     * logs in again to re-arm the session expiration trigger for the new session.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun resetSession() {
        _isSessionExpired.set(false)
        _sessionExpired.resetReplayCache()
    }
}

