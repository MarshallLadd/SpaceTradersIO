package com.brokenhuskysledteam.spacetradersio.sdk.domain.session

import com.brokenhuskysledteam.spacetradersio.sdk.data.db.SpaceTradersDatabase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.ContractStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.WaypointStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive

/**
 * Production implementation of [SpaceTradersSession].
 *
 * **Pattern:** Session-scoped coroutine scope. Every session creates its own
 * [CoroutineScope] (passed in from [SessionManagerImpl]) so that all session-owned
 * coroutines — primarily the [RefreshScheduler]'s timer loop — can be cancelled
 * atomically by calling `scope.cancel()` during [destroy]. This is the standard KMP
 * pattern for bounding coroutine lifetime to a logical lifecycle unit (here: a login
 * session) rather than to the application process.
 *
 * **In this project:** All session-scoped resources ([refreshScheduler],
 * [contractStateStore], [waypointStateStore]) are created eagerly in the constructor.
 * They hold no state until used, so eager creation adds negligible overhead while
 * keeping the session fully operational immediately after login.
 *
 * @param scope The coroutine scope whose lifetime equals the session lifetime.
 *   Created with `SupervisorJob() + Dispatchers.Default` by [SessionManagerImpl.login].
 *   Cancelled by [destroy].
 * @param database SQLDelight database reference, shared with the application scope so
 *   it outlives individual sessions (the database itself is not destroyed on logout —
 *   only specific table contents are cleared).
 */
class SpaceTradersSessionImpl(
    private val scope: CoroutineScope,
    override val database: SpaceTradersDatabase
) : SpaceTradersSession {

    // Each of these objects is given the session scope so their internal coroutines
    // (e.g. the RefreshScheduler timer loop) are automatically cancelled when the
    // session ends, preventing memory leaks or stale callbacks after logout.
    override val refreshScheduler = RefreshScheduler(scope)
    override val contractStateStore = ContractStateStore()
    override val waypointStateStore = WaypointStateStore()

    /** Reflects the coroutine scope's liveness; becomes `false` after [destroy]. */
    override val isActive: Boolean get() = scope.isActive

    /**
     * Re-evaluates all scheduled timers after the app returns to the foreground.
     *
     * Coroutine `delay()` calls may not fire while the process is backgrounded on some
     * platforms. Delegating to [RefreshScheduler.onResume] ensures any expired timers
     * are fired immediately and the loop is restarted for any remaining timers.
     */
    override fun onResume() {
        refreshScheduler.onResume()
    }

    /**
     * Cancels the session scope and removes all persisted agent data from the database.
     *
     * Cancelling [scope] propagates cancellation to [RefreshScheduler]'s internal loop
     * job, stopping all pending timers. Database tables for ships and agents are cleared
     * so that stale data from the previous agent is not shown to the next user after a
     * fresh login. The [database] object itself is not closed — it is application-scoped.
     *
     * Note: [contractStateStore] and [waypointStateStore] are in-memory only and are
     * implicitly discarded when the [SpaceTradersSessionImpl] instance is GC'd; explicit
     * clearing is not required because no references to them outlive the session.
     */
    override fun destroy() {
        scope.cancel()
        // Clear persisted tables so a subsequent login sees a clean slate.
        database.shipQueries.deleteAllShips()
        database.agentQueries.deleteAll()
    }
}
