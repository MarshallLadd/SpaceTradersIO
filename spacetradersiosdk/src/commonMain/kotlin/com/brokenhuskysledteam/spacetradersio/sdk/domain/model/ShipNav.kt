package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavFlightMode
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus

/**
 * Represents the complete navigation state of a ship at a moment in time.
 *
 * **Pattern:** Focused sub-model. Part of the [Ship] decomposed model tree. Navigation
 * state changes more frequently than any other ship sub-model — orbit, dock, and navigate
 * calls all return an updated [ShipNav]. Isolating nav into its own type keeps those
 * updates surgical: `ship.copy(nav = updatedNav)` leaves cargo, fuel, and registration
 * untouched.
 *
 * **In this project:** `ShipNav` is the gating condition for nearly every ship action.
 * The ViewModel inspects [status] before enabling action buttons — for example, trading and
 * refuelling require `DOCKED`, launching a navigate call requires `IN_ORBIT`, and no
 * commands can be issued while `IN_TRANSIT`. [route] provides the departure/arrival
 * timestamps that power the in-transit countdown timer.
 *
 * @property systemSymbol The star system the ship is currently in, in `<sector>-<id>`
 *   format (e.g. `"X1-OE"`). Used for system-scoped API calls such as listing waypoints.
 * @property waypointSymbol The specific waypoint the ship is at or last docked/orbited,
 *   in `<system>-<waypoint>` format (e.g. `"X1-OE-PM"`). Used for waypoint-scoped API
 *   calls such as docking, orbiting, and marketplace access.
 * @property status The ship's current activity state. Determines which actions are
 *   available:
 *   - `DOCKED` — ship is landed; trade, refuel, and repair are available.
 *   - `IN_ORBIT` — ship is in orbit; navigate and scan are available.
 *   - `IN_TRANSIT` — ship is travelling; no commands accepted until arrival.
 *   See [ShipNavStatus] for the full enum.
 * @property flightMode The speed/fuel trade-off mode the ship will use on its next
 *   navigate call. Can be changed via `PATCH /my/ships/{symbol}/nav` while in orbit.
 *   See [ShipNavFlightMode] for cost and speed characteristics of each mode.
 * @property route The ship's most recent (or active) navigation route, including origin,
 *   destination, departure time, and arrival time. When [status] is `IN_TRANSIT` the
 *   route is actively in progress; otherwise it describes the last completed journey.
 *   See [ShipNavRoute].
 */
data class ShipNav(
    val systemSymbol: String,
    val waypointSymbol: String,
    // Gate all ship actions on this value — check status before issuing any command.
    val status: ShipNavStatus,
    val flightMode: ShipNavFlightMode,
    val route: ShipNavRoute
)
