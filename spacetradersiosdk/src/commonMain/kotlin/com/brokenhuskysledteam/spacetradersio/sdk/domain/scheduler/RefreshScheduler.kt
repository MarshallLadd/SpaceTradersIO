package com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Coroutine-based one-shot timer scheduler for API refresh actions.
 *
 * **Pattern:** Single-loop coroutine timer. Instead of spawning one [kotlinx.coroutines.Job]
 * per timer (which leaks if not carefully cancelled), this class maintains a single
 * "loop" coroutine that wakes up at the nearest upcoming expiry, fires the action, then
 * re-evaluates the remaining timers. When timers are added or cancelled, the loop is
 * restarted via [restartLoop] so it recalculates the correct next wake-up time.
 *
 * This design works across all KMP targets (Android, iOS) because it uses only Kotlin
 * coroutine primitives (`delay`, `launch`) — no platform timer APIs are involved.
 *
 * **Observable timers:** [activeTimers] is a [StateFlow] of all pending [ScheduledRefresh]
 * descriptors. ViewModels can collect this to display countdown values (e.g. "ship arrives
 * in 3m 42s") without any additional state management.
 *
 * **In this project:** One [RefreshScheduler] is created per [com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SpaceTradersSession]
 * and cancelled (via scope cancellation) on logout. Ship repositories call [schedule]
 * when a navigate/orbit/dock action returns an expiry time. The scheduler fires the
 * refresh action (typically a re-fetch of ship state) 1 second after expiry.
 *
 * **Test gotcha:** Always use a far-future `expiresAt` (e.g. `"2099-01-01T01:00:00.000Z"`)
 * in tests. A past expiry causes immediate re-scheduling → infinite loop → OOM. Use
 * `backgroundScope` (not `this`) for the [RefreshScheduler] in `runTest` to avoid
 * `UncompletedCoroutinesError`.
 *
 * @param scope The [CoroutineScope] all internal coroutines are launched in. Must be
 *   cancelled externally (by the session owner) to stop the loop on logout.
 * @param clock Injectable [Clock] for testability; defaults to [Clock.System].
 */
class RefreshScheduler(
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System
) {

    private val _activeTimers = MutableStateFlow<Map<String, ScheduledRefresh>>(emptyMap())

    /**
     * Observable map of all currently pending [ScheduledRefresh] entries.
     *
     * Keyed by [ScheduledRefresh.id]. Emits a new snapshot on every [schedule],
     * [cancel], [cancelByPrefix], or when the loop removes a fired/expired entry.
     * Collect this in a ViewModel to drive countdown UI without extra state.
     */
    val activeTimers: StateFlow<Map<String, ScheduledRefresh>> = _activeTimers.asStateFlow()

    // @Volatile ensures that loopJob writes (cancel + reassign in restartLoop) are
    // visible across threads. The two-step cancel-then-launch is not atomic, but the
    // worst case is a briefly-orphaned coroutine that exits on its next iteration when
    // it finds _activeTimers empty or detects its Job has been superseded.
    @Volatile
    private var loopJob: Job? = null

    /**
     * Registers a one-shot refresh to invoke [action] 1 second after [expiresAt].
     *
     * The 1-second buffer after [expiresAt] gives the SpaceTraders server time to
     * finalise the state change (e.g. marking a ship as DOCKED) before the action
     * re-fetches the updated data.
     *
     * If an entry with the same [id] already exists, it is replaced and the loop
     * restarts to recalculate the nearest wake-up time. The action is not retried on
     * failure — if [action] throws a non-cancellation exception it is logged and the
     * entry is permanently removed. Callers must re-schedule if retry is needed.
     *
     * @param id       Unique identifier for this timer. Use a stable, descriptive value
     *   (e.g. ship symbol + operation type). Ship timers should use the ship symbol as a
     *   prefix so [cancelByPrefix] can cancel all timers for that ship at once.
     * @param expiresAt The server-provided [Instant] at which the timed operation ends.
     * @param action   Suspend lambda to execute when the timer fires. Runs on
     *   [kotlinx.coroutines.Dispatchers.Default] inside [scope].
     */
    fun schedule(id: String, expiresAt: Instant, action: suspend () -> Unit) {
        _activeTimers.update { it + (id to ScheduledRefresh(id, expiresAt, action)) }
        restartLoop()
    }

    /**
     * Cancels the timer with the given [id] and removes it from [activeTimers].
     *
     * Safe to call if [id] does not exist. Restarts the loop so the next wake-up
     * time is recalculated without the removed entry.
     *
     * @param id The [ScheduledRefresh.id] of the timer to cancel.
     */
    fun cancel(id: String) {
        _activeTimers.update { it - id }
        restartLoop()
    }

    /**
     * Cancels all timers whose [ScheduledRefresh.id] starts with [prefix].
     *
     * Useful for bulk cancellation when a ship changes state and all previously
     * scheduled actions for it are no longer relevant. For example, calling
     * `cancelByPrefix("SHIP-ALPHA")` removes timers `"SHIP-ALPHA-transit"`,
     * `"SHIP-ALPHA-cooldown"`, etc. in a single operation.
     *
     * @param prefix The prefix to match against timer IDs.
     */
    fun cancelByPrefix(prefix: String) {
        _activeTimers.update { map -> map.filterKeys { !it.startsWith(prefix) } }
        restartLoop()
    }

    /**
     * Re-evaluates all timers after the app returns to the foreground.
     *
     * Coroutine `delay()` calls are not guaranteed to fire on time when the app is
     * backgrounded — the OS may suspend the process. On resume, this method compares
     * each timer's `expiresAt + 1 second` against the current clock. Any timer that has
     * already passed its fire time is removed from [activeTimers] and its [action] is
     * launched immediately as a separate coroutine. The timer loop is then restarted for
     * any timers that are still in the future.
     */
    fun onResume() {
        val now = clock.now()
        // Identify timers that should have fired while the app was backgrounded.
        val expired = _activeTimers.value.filter { (_, entry) ->
            entry.expiresAt + 1.seconds <= now
        }
        if (expired.isNotEmpty()) {
            // Remove expired entries atomically before launching their actions,
            // so a concurrent restartLoop() call cannot double-fire them.
            _activeTimers.update { it - expired.keys }
            expired.values.forEach { entry ->
                scope.launch {
                    try {
                        entry.action()
                    } catch (e: CancellationException) {
                        // Re-throw cancellation so the coroutine machinery can
                        // propagate scope cancellation correctly.
                        throw e
                    } catch (e: Exception) {
                        Napier.e("RefreshScheduler: action '${entry.id}' failed on resume", e)
                    }
                }
            }
        }
        restartLoop()
    }

    /**
     * Cancels the current loop job and starts a fresh one.
     *
     * Called after any mutation to [activeTimers] so the loop always sleeps until
     * the *current* nearest expiry. If [activeTimers] is empty the new loop exits
     * immediately without suspending.
     */
    private fun restartLoop() {
        // loopJob is @Volatile for visibility across threads. restartLoop() is not
        // fully atomic (cancel + launch is a two-step op), but since _activeTimers
        // mutations are protected by MutableStateFlow.update{}, the worst case of a
        // concurrent restartLoop() call is a briefly-orphaned coroutine that will
        // terminate on its next iteration when it finds _activeTimers empty.
        loopJob?.cancel()
        loopJob = scope.launch { runLoop() }
    }

    /**
     * Core timer loop: sleeps until the nearest expiry, fires the action, then repeats.
     *
     * The loop exits when [activeTimers] is empty. On each iteration it re-reads the
     * current timer map (so additions/cancellations made while sleeping are respected)
     * and recalculates the nearest expiry. After waking from [delay], it performs a
     * re-check (`containsKey`) before firing — the timer may have been cancelled during
     * the sleep.
     */
    private suspend fun runLoop() {
        while (true) {
            val entries = _activeTimers.value
            if (entries.isEmpty()) return

            // Find the timer that fires soonest.
            val nearest = entries.values.minBy { it.expiresAt }
            val now = clock.now()
            val waitDuration = (nearest.expiresAt + 1.seconds) - now

            if (waitDuration.isPositive()) {
                delay(waitDuration)
            }

            // Re-check after delay: the entry may have been cancelled while we slept.
            if (!_activeTimers.value.containsKey(nearest.id)) continue

            // Remove the entry before invoking the action so the timer is not visible
            // in activeTimers during the potentially slow async action.
            _activeTimers.update { it - nearest.id }
            try {
                nearest.action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("RefreshScheduler: action '${nearest.id}' failed", e)
            }
        }
    }
}
