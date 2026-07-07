package com.brokenhuskysledteam.spacetradersio.ui.ships

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.CargoItem
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ContractDeliverGood
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavFlightMode
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipRole
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import kotlin.time.Instant

/**
 * Top-level UI state for the ship detail screen.
 *
 * **Pattern:** Unidirectional Data Flow (UDF) state holder. The ViewModel exposes a single
 * [StateFlow] of this type; the composable renders whatever it receives without holding local
 * mutable state.
 *
 * **In this project:** Combines repository-backed ship data ([ship]) with purely ViewModel-local
 * fields ([isLoading], [isActionInProgress], [actionResult], [error]) that are produced by
 * [ShipDetailViewModel]'s `LocalState` and `combine()` pipeline.
 *
 * @property ship Flattened UI model for the ship. `null` while the initial load is in progress
 *   or if the load failed before any data was cached.
 * @property isLoading `true` during the initial network fetch. Drives the full-screen spinner.
 * @property isActionInProgress `true` while any ship action (orbit, dock, refuel) is executing.
 *   Buttons disable themselves while this is `true` to prevent duplicate requests.
 * @property actionResult Transient feedback shown after a successful action completes. `null`
 *   when no action has run or after the user dismisses the result. See [ActionResult].
 * @property error Human-readable error message. Non-null either when the initial load fails
 *   (shown as a full-screen error card) or when an action fails (shown as an inline error card
 *   above the command buttons).
 */
data class ShipDetailUiState(
    val ship: ShipDetail? = null,
    val isLoading: Boolean = true,
    val isActionInProgress: Boolean = false,
    val actionResult: ActionResult? = null,
    val error: String? = null,
    val hasShipyard: Boolean = false,
    val hasMarketplace: Boolean = false,
    val isAsteroid: Boolean = false,
    val isJumpGate: Boolean = false,
    val pendingNegotiate: Boolean = false,
    val activeContracts: List<Contract> = emptyList(),
    val isDeliverDialogOpen: Boolean = false,
    val selectedDeliverContract: Contract? = null,
    val selectedDeliverGood: ContractDeliverGood? = null,
    val deliverUnits: String = ""
)

/**
 * Flattened UI model for the ship detail screen.
 *
 * **Pattern:** Flattened UI model. The SDK domain model [Ship][com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship]
 * has a deeply nested navigation structure: `Ship → ShipNav → ShipNavRoute → ShipNavRouteWaypoint`.
 * Rather than passing the full domain object to composables and forcing them to traverse
 * `ship.nav.route.destination.symbol`, this model hoists every displayed field to a top-level
 * property. Composables stay dumb; all structural decisions live in the mapping function
 * `Ship.toDetail()`. In a new project, apply this pattern whenever a composable would need
 * more than one level of property access to read a value.
 *
 * **In this project:** Produced by the private `Ship.toDetail()` extension in
 * `ShipDetailViewModel.kt`. It is the type of [ShipDetailUiState.ship].
 *
 * @property symbol Unique identifier for this ship (e.g. `"AGENT_NAME-1"`).
 * @property frameName Human-readable ship frame class (e.g. `"Frame Probe"`).
 * @property role The ship's registered role (e.g. `HAULER`, `EXPLORER`). See [ShipRole].
 * @property navStatus Current navigation status: `DOCKED`, `IN_ORBIT`, or `IN_TRANSIT`.
 *   Drives which action buttons are shown. See [ShipNavStatus].
 * @property flightMode Current flight mode (e.g. `CRUISE`, `BURN`, `DRIFT`). Affects fuel
 *   consumption and travel speed.
 * @property systemSymbol Symbol of the star system the ship is currently in
 *   (e.g. `"X1-DF55"`).
 * @property waypointSymbol Symbol of the specific waypoint the ship is at or heading to
 *   (e.g. `"X1-DF55-20250Z"`).
 * @property originSymbol Symbol of the waypoint the ship departed from on the current or most
 *   recent route leg.
 * @property originType Waypoint type of the origin (e.g. `PLANET`, `ASTEROID`). Displayed
 *   alongside [originSymbol] in the navigation card.
 * @property destinationSymbol Symbol of the waypoint the ship is travelling toward on the
 *   current or most recent route leg.
 * @property destinationType Waypoint type of the destination. Displayed alongside
 *   [destinationSymbol] in the navigation card.
 * @property arrivalTime The [Instant] at which the ship will arrive at [destinationSymbol].
 *   **Non-null only when [navStatus] is `IN_TRANSIT`.** The mapping function explicitly sets
 *   this to `null` for docked/orbiting ships so that the UI never shows a stale arrival time
 *   from a previous transit leg.
 * @property departureTime The [Instant] at which the ship began the current transit leg.
 *   **Non-null only when [navStatus] is `IN_TRANSIT`.** Used together with [arrivalTime] to
 *   calculate the progress fraction for the transit progress bar.
 * @property fuelCurrent Current fuel units remaining in the tank.
 * @property fuelCapacity Maximum fuel capacity of this ship's frame.
 * @property cargoUnits Number of cargo units currently loaded.
 * @property cargoCapacity Maximum cargo capacity in units.
 * @property cargoInventory The distinct goods currently in the hold, rendered as an itemised
 *   manifest under the cargo capacity bar. Empty when the hold is empty.
 */
// UI model for the ship detail screen — flattened from the domain model
// for easy display without nested object navigation in composables.
data class ShipDetail(
    val symbol: String,
    val frameName: String,
    val role: ShipRole,
    val navStatus: ShipNavStatus,
    val flightMode: ShipNavFlightMode,
    val systemSymbol: String,
    val waypointSymbol: String,
    // Route fields
    val originSymbol: String,
    val originType: WaypointType,
    val destinationSymbol: String,
    val destinationType: WaypointType,
    val arrivalTime: Instant?,
    val departureTime: Instant?,
    val fuelCurrent: Int,
    val fuelCapacity: Int,
    val cargoUnits: Int,
    val cargoCapacity: Int,
    val cargoInventory: List<CargoItem> = emptyList()
)

/**
 * Transient feedback state shown after a ship command completes successfully.
 *
 * **Pattern:** Transient feedback state. [ActionResult] is `null` by default and is only set
 * after a successful action; the user dismisses it via [ShipDetailEvent.ActionResultDismissed],
 * which returns it to `null`. This is intentionally different from persistent state (like fuel
 * level) which always reflects current reality. In a new project, use this pattern whenever
 * you need a one-time "command executed" confirmation that the user must explicitly acknowledge
 * before it disappears — it pairs naturally with a dismiss button.
 *
 * **In this project:** Rendered by `CommandOutputCard` in amber to visually distinguish it from
 * the green informational panels. Each subtype carries the minimal data needed to confirm what
 * changed so the user does not have to scroll back to verify the update.
 */
// Command output displayed after an action completes. Shown in amber to
// distinguish from static ship data.
sealed interface ActionResult {

    /**
     * The ship successfully entered orbit.
     *
     * @property waypointSymbol The waypoint the ship is now orbiting.
     */
    data class Orbited(val waypointSymbol: String) : ActionResult

    /**
     * The ship successfully docked at a waypoint.
     *
     * @property waypointSymbol The waypoint where the ship is now docked.
     */
    data class Docked(val waypointSymbol: String) : ActionResult

    /**
     * The ship successfully refueled.
     *
     * @property fuelAdded Number of fuel units added during this transaction.
     * @property totalCost Total credits spent on the refuel transaction.
     * @property newCredits Agent's updated credit balance after the purchase.
     */
    data class Refueled(
        val fuelAdded: Int,
        val totalCost: Int,
        val newCredits: Long
    ) : ActionResult

    data class NegotiatedContract(
        val contractId: String,
        val type: String,
        val upfrontPayment: Int
    ) : ActionResult

    data class DeliveredCargo(
        val tradeSymbol: String,
        val unitsFulfilled: Int,
        val unitsRequired: Int
    ) : ActionResult
}

/**
 * User-initiated events for the ship detail screen.
 *
 * **Pattern:** Sealed event interface for UDF. All UI interactions are modelled as a sealed type
 * and funnelled through a single `onEvent` lambda. The ViewModel's `when` expression is then
 * exhaustive by the compiler, preventing silent omissions. In a new project, prefer this over
 * individual callback lambdas once you have more than two or three distinct actions.
 *
 * **In this project:** Consumed by [ShipDetailViewModel.onEvent]. Note that [ViewSystemClicked]
 * is handled by the composable directly via its `onNavigateToSystemMap` callback — the ViewModel
 * still includes an `is ViewSystemClicked -> Unit` arm to satisfy Kotlin's sealed exhaustiveness
 * requirement. See the ViewModel for the reasoning.
 */
sealed interface ShipDetailEvent {

    /** The user tapped the "Orbit" button. Only emitted when the ship is docked. */
    data object OrbitClicked : ShipDetailEvent

    /** The user tapped the "Dock" button. Only emitted when the ship is in orbit. */
    data object DockClicked : ShipDetailEvent

    /** The user tapped the "Refuel" button. Only emitted when the ship is docked. */
    data object RefuelClicked : ShipDetailEvent

    /**
     * The user dismissed the [ActionResult] feedback panel.
     *
     * Causes the ViewModel to clear [ShipDetailUiState.actionResult], hiding the
     * `CommandOutputCard` composable.
     */
    data object ActionResultDismissed : ShipDetailEvent

    /**
     * The user tapped "Retry" after a load failure.
     *
     * Causes the ViewModel to re-invoke `loadShip()`.
     */
    data object RetryClicked : ShipDetailEvent

    /**
     * The user tapped "View System" to navigate to the system map.
     *
     * **Exhaustiveness note:** This event is included in the sealed hierarchy so that
     * the composable can pass the full [ShipDetailEvent] type to `onEvent` without a
     * second parallel callback. The ViewModel's `when` block handles it with
     * `is ViewSystemClicked -> Unit` — it does nothing — because the composable intercepts
     * this event and calls `onNavigateToSystemMap` directly before forwarding it, or the
     * NavHost's callback handles navigation without the ViewModel's involvement. This is the
     * correct pattern when navigation is driven by the composable layer rather than a
     * ViewModel-owned `Channel<NavigationTarget>`.
     *
     * @property systemSymbol The system to open on the map.
     * @property waypointSymbol The waypoint to highlight or centre on.
     * @property shipSymbol The ship to associate with the map view.
     */
    data class ViewSystemClicked(
        val systemSymbol: String,
        val waypointSymbol: String,
        val shipSymbol: String
    ) : ShipDetailEvent

    data class ViewShipyardClicked(
        val systemSymbol: String,
        val waypointSymbol: String
    ) : ShipDetailEvent

    /**
     * The user tapped "Trade" to open the market at the ship's current (docked) waypoint.
     * Navigation is handled by the composable's `onNavigateToMarket` callback, wired in the
     * NavHost — the ViewModel handles it with `-> Unit`.
     */
    data class ViewMarketClicked(
        val systemSymbol: String,
        val waypointSymbol: String,
        val shipSymbol: String
    ) : ShipDetailEvent

    data object NegotiateContractClicked : ShipDetailEvent
    data object NegotiateConfirmed : ShipDetailEvent
    data object NegotiateDismissed : ShipDetailEvent

    data object DeliverCargoClicked : ShipDetailEvent
    data class DeliverContractSelected(val contract: Contract) : ShipDetailEvent
    data class DeliverGoodSelected(val good: ContractDeliverGood) : ShipDetailEvent
    data class DeliverUnitsChanged(val units: String) : ShipDetailEvent
    data object DeliverConfirmed : ShipDetailEvent
    data object DeliverDismissed : ShipDetailEvent
}
