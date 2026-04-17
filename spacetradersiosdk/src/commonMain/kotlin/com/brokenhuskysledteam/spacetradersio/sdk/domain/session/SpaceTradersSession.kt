package com.brokenhuskysledteam.spacetradersio.sdk.domain.session

import com.brokenhuskysledteam.spacetradersio.sdk.data.db.SpaceTradersDatabase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.ContractStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.WaypointStateStore

/**
 * Represents the full set of resources owned by a single authenticated SpaceTraders session.
 *
 * **Pattern:** Session-scoped resource container (interface). Unlike application-scoped
 * singletons (created once, alive for the entire process), session-scoped resources are
 * created fresh on [SessionManager.login] and torn down on [SessionManager.logout]. This
 * guarantees that state from a previous agent never leaks into a new login. In a new
 * project, define an interface like this for any resource group that must be reset between
 * authenticated contexts.
 *
 * **In this project:** [SessionManagerImpl] holds at most one [SpaceTradersSession] at a
 * time. Repositories and ViewModels obtain the session via [SessionManager.requireSession]
 * and access sub-resources (state stores, scheduler, database) through this interface.
 * Using an interface keeps the concrete session implementation hidden from consumers.
 *
 * @property refreshScheduler Coroutine-based timer that fires API refresh actions when
 *   in-flight operations (e.g. ship transit) expire. Tied to the session's coroutine scope.
 * @property contractStateStore In-memory reactive cache of the agent's [com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract]
 *   entities, keyed by contract ID.
 * @property waypointStateStore In-memory reactive cache of fetched [com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Waypoint]
 *   entities, keyed by waypoint symbol.
 * @property database SQLDelight database providing persistent, offline-first storage for
 *   ships, agents, and other data that survives process death.
 * @property isActive `true` while the session's underlying coroutine scope is running;
 *   `false` after [destroy] has been called.
 */
interface SpaceTradersSession {
    val refreshScheduler: RefreshScheduler
    val contractStateStore: ContractStateStore
    val waypointStateStore: WaypointStateStore
    val database: SpaceTradersDatabase
    val isActive: Boolean

    /**
     * Notifies the session that the app has returned to the foreground.
     *
     * Delegates to [RefreshScheduler.onResume] so that any timers whose coroutine
     * delay was missed while the app was backgrounded are immediately evaluated and
     * fired if past their expiry.
     */
    fun onResume()

    /**
     * Tears down all session-scoped resources.
     *
     * Cancels the session's coroutine scope (stopping all active timers), clears
     * persistent database tables for the current agent, and resets the in-memory state
     * stores. Called by [SessionManager.logout] before the session reference is discarded.
     */
    fun destroy()
}
