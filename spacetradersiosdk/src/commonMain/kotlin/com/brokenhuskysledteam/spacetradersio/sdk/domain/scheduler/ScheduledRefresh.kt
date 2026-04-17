package com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler

import kotlin.time.Instant

/**
 * Represents a single pending refresh timer registered with [RefreshScheduler].
 *
 * **Pattern:** Immutable timer descriptor. Rather than storing a live [kotlinx.coroutines.Job]
 * per timer, the scheduler stores these lightweight descriptors in a [kotlinx.coroutines.flow.StateFlow]
 * map. This decouples the observable timer state (what is scheduled, when it fires) from
 * the execution mechanism (a single loop coroutine). In a new project, prefer a descriptor
 * model like this whenever you need timers to be both observable and cancellable.
 *
 * **In this project:** Stored in [RefreshScheduler.activeTimers]. ViewModels can collect
 * `activeTimers` to drive countdown displays (e.g. "ship arrives in 3m 42s") without
 * needing any additional state.
 *
 * @property id Unique string identifier for this timer. Used as the map key in
 *   [RefreshScheduler.activeTimers] and as the argument to [RefreshScheduler.cancel].
 *   Ship timers use the ship symbol as a prefix so [RefreshScheduler.cancelByPrefix]
 *   can cancel all timers for a given ship with a single call.
 * @property expiresAt The [Instant] at which the timed operation (e.g. ship transit)
 *   completes on the server. [RefreshScheduler] fires [action] 1 second *after* this
 *   time to give the server a moment to finalise the updated state before re-fetching.
 * @property action The suspend lambda to execute when the timer fires. Runs in the
 *   [RefreshScheduler]'s coroutine scope on [kotlinx.coroutines.Dispatchers.Default].
 *   Exceptions are caught and logged; the timer is removed regardless of outcome.
 *
 * **Test gotcha:** Always provide a far-future [expiresAt] (e.g. `"2099-01-01T01:00:00.000Z"`)
 * in tests. A past [expiresAt] causes [RefreshScheduler.onResume] (and the loop) to treat
 * the timer as immediately expired, re-schedule it, and loop infinitely → OOM.
 */
data class ScheduledRefresh(
    val id: String,
    val expiresAt: Instant,
    val action: suspend () -> Unit
)
