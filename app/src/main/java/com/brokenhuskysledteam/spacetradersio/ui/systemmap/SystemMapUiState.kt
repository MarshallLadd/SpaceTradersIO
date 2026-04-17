package com.brokenhuskysledteam.spacetradersio.ui.systemmap

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType

/**
 * The complete UI state for the System Map screen — the most complex UiState in this project.
 *
 * **Pattern:** Unidirectional Data Flow (UDF). All mutable view state is bundled here and emitted
 * from a single `StateFlow` in the ViewModel. In a new project, extend this pattern whenever a
 * screen has multiple interrelated display concerns (filters, sort, selection) that must stay
 * consistent with each other on every recomposition.
 *
 * **In this project:** This class carries both the derived presentation data (the filtered/sorted
 * [waypoints] tree) and the pure display-control state ([sortMode], [activeTypeFilters],
 * [selectedShip]). Filter and sort state lives here rather than in the domain layer because it is
 * display-only: the `SystemRepository` and `WaypointStateStore` have no concept of how the UI
 * chooses to present or order waypoints.
 *
 * @property systemSymbol The SpaceTraders symbol of the star system being displayed (e.g. "X1-DF55").
 * @property waypoints The filtered, sorted, tree-structured list of waypoints ready for rendering.
 *   Parents appear at the top level; orbitals are nested inside each [WaypointNode].
 * @property isLoading `true` while the initial waypoint fetch is in progress.
 * @property error Non-null when a network or API error has occurred.
 * @property sortMode The active sort order applied to the [waypoints] list.
 * @property distanceOrigin The reference point used when [sortMode] is [SortMode.DISTANCE].
 * @property activeTypeFilters The set of [WaypointType] values currently restricting the list.
 *   An empty set means "show all types".
 * @property activeTraitFilters The set of [WaypointTraitSymbol] values currently restricting the
 *   list. An empty set means "show all traits".
 * @property selectedShip The ship the user navigated here with, if any. `null` hides Navigate
 *   buttons on each waypoint row.
 * @property focusWaypointSymbol Symbol of the waypoint that should be highlighted as the current
 *   location (e.g. where the selected ship is docked).
 * @property isActionInProgress `true` while a navigation command is in flight to the API.
 * @property actionResult The outcome of the last completed action, shown in [ActionResultCard]
 *   until dismissed.
 */
data class SystemMapUiState(
    val systemSymbol: String = "",
    val waypoints: List<WaypointNode> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val sortMode: SortMode = SortMode.NAME,
    val distanceOrigin: DistanceOrigin = DistanceOrigin.SYSTEM_CENTER,
    val activeTypeFilters: Set<WaypointType> = emptySet(),
    val activeTraitFilters: Set<WaypointTraitSymbol> = emptySet(),
    val selectedShip: ShipSnapshot? = null,
    val focusWaypointSymbol: String? = null,
    val isActionInProgress: Boolean = false,
    val actionResult: SystemMapActionResult? = null
)

/**
 * A node in the waypoint display tree, pairing a parent waypoint with its orbitals.
 *
 * **Pattern:** View-layer tree projection. The domain model stores orbital relationships as a flat
 * list of [Waypoint] objects, each optionally referencing the symbol of the body it orbits. The
 * ViewModel's `buildTree()` function groups these into a two-level hierarchy for rendering. In a
 * new project, build hierarchical display structures in the ViewModel rather than baking them into
 * the repository, keeping domain models flat and storage-agnostic.
 *
 * **In this project:** This is constructed by `Waypoint.toNode()` inside `buildTree()`. Orbitals
 * are always one level deep — the SpaceTraders API does not produce deeper nesting.
 *
 * @property waypoint The display summary for this waypoint.
 * @property distance Pre-computed distance from the active [DistanceOrigin] to this waypoint's
 *   coordinates, expressed in abstract AU units. Cached here so `applySort()` avoids repeated
 *   `sqrt` calls during list sorting.
 * @property orbitals Child waypoints that orbit this body. Empty for most waypoints; non-empty
 *   for gas giants and asteroid fields that have moons or stations in orbit.
 */
data class WaypointNode(
    val waypoint: WaypointSummary,
    val distance: Double,
    val orbitals: List<WaypointNode>
)

/**
 * A lightweight display model for a single waypoint, derived from the domain [Waypoint].
 *
 * **Pattern:** View-layer projection / display model. Separating a display summary from the domain
 * model keeps UI code free of domain logic and lets the ViewModel pre-compute repeated lookups
 * (like "does this waypoint have a marketplace?") once rather than on every recomposition. In a
 * new project, create a summary like this whenever a composable needs multiple boolean flags
 * derived from a raw collection field.
 *
 * **In this project:** Built inside `Waypoint.toNode()` in `SystemMapViewModel`. The boolean
 * convenience fields avoid scattering `WaypointTraitSymbol.MARKETPLACE in traits` checks across
 * composable code.
 *
 * @property symbol The unique SpaceTraders identifier for this waypoint (e.g. "X1-DF55-A1").
 * @property type The classification of this body (planet, gas giant, asteroid field, etc.).
 * @property x X coordinate in the system's 2-D space. Used for distance calculations.
 * @property y Y coordinate in the system's 2-D space. Used for distance calculations.
 * @property traits The full list of [WaypointTraitSymbol] values this waypoint has. Kept on the
 *   summary so composables can enumerate all traits if needed.
 * @property hasMarketplace `true` if [WaypointTraitSymbol.MARKETPLACE] is in [traits].
 *   Pre-computed to avoid repeated list searches in composables.
 * @property hasShipyard `true` if [WaypointTraitSymbol.SHIPYARD] is in [traits].
 *   Pre-computed for the same reason as [hasMarketplace].
 * @property isUncharted `true` if [WaypointTraitSymbol.UNCHARTED] is in [traits]. Uncharted
 *   waypoints cannot be navigated to until surveyed.
 * @property isUnderConstruction `true` if the waypoint is still being built. Sourced directly
 *   from the domain field rather than derived from traits.
 */
data class WaypointSummary(
    val symbol: String,
    val type: WaypointType,
    val x: Int,
    val y: Int,
    val traits: List<WaypointTraitSymbol>,
    val hasMarketplace: Boolean,
    val hasShipyard: Boolean,
    val isUncharted: Boolean,
    val isUnderConstruction: Boolean
)

/**
 * A minimal snapshot of a [Ship] containing only the fields the System Map needs.
 *
 * **Pattern:** Scoped projection / data minimisation. Instead of passing the full domain [Ship]
 * object (which carries cargo, crew, reactor, frame, and mount data) into the UI state, only the
 * fields required by this screen are captured. In a new project, use a snapshot like this whenever
 * a screen needs a subset of a large domain object — it makes the UiState cheaper to copy on
 * every state update and makes the screen's data requirements explicit.
 *
 * **In this project:** Built by `Ship.toSnapshot()` inside the `combine` block in
 * `SystemMapViewModel`. The map screen only needs to know where the ship is, how much fuel it has,
 * and whether it is currently in transit (to disable the Navigate button).
 *
 * @property symbol The ship's unique identifier (e.g. "AGENT_SHIP_1").
 * @property waypointSymbol The symbol of the waypoint the ship is currently at or en route to.
 * @property x The X coordinate of the ship's current or destination waypoint. Used when
 *   [DistanceOrigin.SHIP_LOCATION] is active.
 * @property y The Y coordinate of the ship's current or destination waypoint.
 * @property fuelCurrent Fuel units currently in the tank. Displayed in the header and used to
 *   calculate fuel consumed after a navigation command.
 * @property fuelCapacity Maximum fuel capacity. Displayed in the header alongside [fuelCurrent].
 * @property navStatus Whether the ship is [ShipNavStatus.IN_TRANSIT], [ShipNavStatus.IN_ORBIT],
 *   or [ShipNavStatus.DOCKED]. Navigate buttons are hidden while the ship is in transit.
 */
data class ShipSnapshot(
    val symbol: String,
    val waypointSymbol: String,
    val x: Int,
    val y: Int,
    val fuelCurrent: Int,
    val fuelCapacity: Int,
    val navStatus: ShipNavStatus
)

/**
 * Determines the order in which [WaypointNode] entries are presented in the list.
 *
 * **In this project:** Toggled by the [SortBar] composable. When [DISTANCE] is active, a second
 * row appears letting the user choose the [DistanceOrigin] reference point.
 */
enum class SortMode {
    /** Sort waypoints alphabetically by their symbol. */
    NAME,

    /** Sort waypoints by Euclidean distance from the active [DistanceOrigin]. */
    DISTANCE
}

/**
 * The reference point used when sorting waypoints by [SortMode.DISTANCE].
 *
 * **In this project:** Defaults to [SYSTEM_CENTER] (coordinates 0, 0 in SpaceTraders space).
 * Switching to [SHIP_LOCATION] re-sorts relative to the selected ship's current position, which
 * is useful for planning the most fuel-efficient sequence of stops.
 */
enum class DistanceOrigin {
    /** Distance is measured from the origin of the star system (0, 0). */
    SYSTEM_CENTER,

    /** Distance is measured from the selected ship's current waypoint coordinates. */
    SHIP_LOCATION
}

/**
 * Represents the outcome of a user-initiated action on the System Map screen.
 *
 * **Pattern:** Sealed action result. Rather than a generic success/error string, each action
 * produces a typed result that carries exactly the data the UI needs to render feedback. In a new
 * project, use a sealed interface here instead of a plain string so the `when` expression in the
 * composable is exhaustive and new action types can be added safely.
 *
 * **In this project:** Stored in [SystemMapUiState.actionResult] and rendered by [ActionResultCard].
 * The user explicitly dismisses it via [SystemMapEvent.ActionResultDismissed].
 */
sealed interface SystemMapActionResult {

    /**
     * Emitted when a navigation command has been successfully accepted by the API.
     *
     * **In this project:** The [fuelConsumed] value is derived by subtracting the post-navigate
     * fuel level from the pre-navigate snapshot captured immediately before the API call. This
     * avoids a race condition where the ship's updated state from the API response might differ
     * from what the user saw in the UI at the moment they pressed Navigate.
     *
     * @property destinationSymbol The symbol of the waypoint the ship is now en route to.
     * @property fuelConsumed Units of fuel burned by this navigation leg.
     * @property fuelRemaining Fuel remaining in the tank after the leg starts.
     */
    data class NavigationStarted(
        val destinationSymbol: String,
        val fuelConsumed: Int,
        val fuelRemaining: Int
    ) : SystemMapActionResult
}

/**
 * All user interactions that the System Map screen can produce.
 *
 * **Pattern:** Sealed event interface (UDF). Every user gesture is expressed as a typed event
 * routed through a single `onEvent` callback rather than individual lambdas. In a new project,
 * this keeps the composable signature stable as interactions grow — you only ever add a new
 * `data class` here and a branch in the ViewModel's `when`.
 *
 * **In this project:** Consumed by `SystemMapViewModel.onEvent()`.
 */
sealed interface SystemMapEvent {

    /** The user tapped Retry after a load failure. Re-triggers the waypoint fetch. */
    data object RetryClicked : SystemMapEvent

    /**
     * The user tapped a sort mode button in [SortBar].
     *
     * @property mode The sort order to activate.
     */
    data class SortModeSelected(val mode: SortMode) : SystemMapEvent

    /**
     * The user chose a new distance reference point in [SortBar].
     * Only meaningful when [SortMode.DISTANCE] is active.
     *
     * @property origin The new reference point for distance calculations.
     */
    data class DistanceOriginSelected(val origin: DistanceOrigin) : SystemMapEvent

    /**
     * The user tapped a waypoint-type chip in [FilterChipRow].
     * If [type] is already active it is removed; otherwise it is added.
     *
     * @property type The waypoint type to toggle in the active filter set.
     */
    data class TypeFilterToggled(val type: WaypointType) : SystemMapEvent

    /**
     * The user tapped a trait chip in [FilterChipRow].
     * If [trait] is already active it is removed; otherwise it is added.
     *
     * @property trait The waypoint trait to toggle in the active filter set.
     */
    data class TraitFilterToggled(val trait: WaypointTraitSymbol) : SystemMapEvent

    /**
     * The user pressed the Navigate button on a waypoint row.
     *
     * @property waypointSymbol The destination waypoint's symbol.
     */
    data class NavigateToWaypoint(val waypointSymbol: String) : SystemMapEvent

    /** The user dismissed the [ActionResultCard] after a completed action. */
    data object ActionResultDismissed : SystemMapEvent
}
