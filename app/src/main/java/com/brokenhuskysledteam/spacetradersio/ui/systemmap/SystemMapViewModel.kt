package com.brokenhuskysledteam.spacetradersio.ui.systemmap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Waypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.euclideanDistance
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.SystemRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.WaypointStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.NavigateShipUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the System Map screen.
 *
 * **Pattern:** Three-stream `combine` UDF. This ViewModel drives its UI state by combining
 * three independent reactive streams into a single [SystemMapUiState]. To apply in a new
 * project: identify every independent source of truth that affects the screen, expose each as a
 * `StateFlow`, then combine them in one `combine` block so any single upstream change triggers
 * exactly one re-render.
 *
 * **In this project:** The three streams are:
 * 1. `waypointStateStore.entities` — a `StateFlow<Map<String, Waypoint>>` backed by the
 *    SQLDelight database. Note that [WaypointStateStore] has no `observeAll()` method; the raw
 *    `.entities` property is used directly to access the full waypoint map.
 * 2. `fleetRepository.observeShips()` — a `Flow<List<Ship>>` from the fleet DB, mapped to a
 *    `Map<String, Ship>` for O(1) symbol lookup.
 * 3. `_localState` — a [MutableStateFlow] holding transient UI state (sort mode, active
 *    filters, loading/error flags, action results) that has no persistent backing store.
 *
 * Because the waypoint store and fleet repository are both database-backed, changes made by
 * background refreshes automatically flow through to the UI without any manual re-fetch.
 *
 * @param savedStateHandle Provides `systemSymbol`, `focusWaypointSymbol`, and `shipSymbol`
 *   navigation arguments injected by the Compose nav graph.
 * @param systemRepository Fetches all waypoints for a system from the SpaceTraders API and
 *   writes them into [WaypointStateStore].
 * @param fleetRepository Observes the local ship database so the selected ship's position and
 *   fuel are always current.
 * @param waypointStateStore Session-scoped in-memory + DB store that holds waypoints for the
 *   active session; updated by [systemRepository] and observed by [uiState].
 * @param navigateShipUseCase Sends a navigation command to the API and persists the updated
 *   nav state to the fleet DB.
 */
@HiltViewModel
class SystemMapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val systemRepository: SystemRepository,
    private val fleetRepository: FleetRepository,
    private val waypointStateStore: WaypointStateStore,
    private val navigateShipUseCase: NavigateShipUseCase
) : ViewModel() {

    /** Symbol of the star system being displayed, required nav argument. */
    private val systemSymbol: String = checkNotNull(savedStateHandle["systemSymbol"])

    /**
     * Optional waypoint symbol that should be highlighted as the ship's current location.
     * Passed from the calling screen (e.g., the ship detail screen).
     */
    private val focusWaypointSymbol: String? = savedStateHandle["focusWaypointSymbol"]

    /**
     * Optional symbol of the ship that was in context when the user opened this screen.
     * When present, the Navigate button is shown on eligible waypoint cards.
     */
    private val shipSymbol: String? = savedStateHandle["shipSymbol"]

    /**
     * Transient UI state that does not belong in any persistent store.
     *
     * Separated from the database-backed streams so that filter/sort preferences and loading
     * flags can be updated synchronously without touching the DB or the API.
     */
    private val _localState = MutableStateFlow(LocalState())

    /**
     * The single source of truth for the System Map UI, derived by combining all three upstream
     * streams.
     *
     * Each time any of the three streams emits, the lambda re-runs:
     * - Looks up the selected ship from the fleet map using [shipSymbol].
     * - Filters the waypoint map to only those belonging to [systemSymbol].
     * - Applies active type and trait filters via [applyFilters].
     * - Resolves the distance origin coordinates (system center or ship location).
     * - Builds a parent/orbital tree via [buildTree].
     * - Sorts the tree via [applySort].
     *
     * Uses [SharingStarted.Eagerly] so that `.value` is always populated, which is required for
     * the fuel-before-navigation snapshot in [navigate] and for ViewModel tests that read
     * `.value` directly without an active subscriber.
     */
    val uiState: StateFlow<SystemMapUiState> = combine(
        waypointStateStore.entities,
        fleetRepository.observeShips().map { ships -> ships.associateBy { it.symbol } },
        _localState
    ) { waypointMap, fleetMap, local ->
        val ship = shipSymbol?.let { fleetMap[it] }
        val shipSnapshot = ship?.toSnapshot()

        val allWaypoints = waypointMap.values.filter { it.systemSymbol == systemSymbol }
        val filtered = applyFilters(allWaypoints, local.activeTypeFilters, local.activeTraitFilters)

        // Determine the (x, y) reference point for distance calculations.
        // When SHIP_LOCATION is selected but no ship is resolved (e.g., shipSymbol is null),
        // fall back gracefully to system center (0, 0).
        val originX = when (local.distanceOrigin) {
            DistanceOrigin.SYSTEM_CENTER -> 0
            DistanceOrigin.SHIP_LOCATION -> shipSnapshot?.x ?: 0
        }
        val originY = when (local.distanceOrigin) {
            DistanceOrigin.SYSTEM_CENTER -> 0
            DistanceOrigin.SHIP_LOCATION -> shipSnapshot?.y ?: 0
        }

        val tree = buildTree(filtered, allWaypoints, originX, originY)
        val sorted = applySort(tree, local.sortMode)

        SystemMapUiState(
            systemSymbol = systemSymbol,
            waypoints = sorted,
            isLoading = local.isLoading,
            error = local.error,
            sortMode = local.sortMode,
            distanceOrigin = local.distanceOrigin,
            activeTypeFilters = local.activeTypeFilters,
            activeTraitFilters = local.activeTraitFilters,
            selectedShip = shipSnapshot,
            focusWaypointSymbol = focusWaypointSymbol,
            isActionInProgress = local.isActionInProgress,
            actionResult = local.actionResult
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SystemMapUiState())

    init {
        loadWaypoints()
    }

    /**
     * Dispatches a [SystemMapEvent] from the UI layer.
     *
     * Filter toggle events use a copy-with-mutable-set pattern: convert the immutable
     * `Set` to a `MutableSet`, add or remove the item, then replace the field via `copy`.
     * This preserves immutability of [LocalState] while allowing multi-select toggling.
     *
     * @param event The UI event to handle.
     */
    fun onEvent(event: SystemMapEvent) {
        when (event) {
            is SystemMapEvent.RetryClicked -> loadWaypoints()
            is SystemMapEvent.SortModeSelected -> _localState.update { it.copy(sortMode = event.mode) }
            is SystemMapEvent.DistanceOriginSelected -> _localState.update { it.copy(distanceOrigin = event.origin) }
            is SystemMapEvent.TypeFilterToggled -> _localState.update { state ->
                val updated = state.activeTypeFilters.toMutableSet()
                if (event.type in updated) updated.remove(event.type) else updated.add(event.type)
                state.copy(activeTypeFilters = updated)
            }
            is SystemMapEvent.TraitFilterToggled -> _localState.update { state ->
                val updated = state.activeTraitFilters.toMutableSet()
                if (event.trait in updated) updated.remove(event.trait) else updated.add(event.trait)
                state.copy(activeTraitFilters = updated)
            }
            is SystemMapEvent.NavigateToWaypoint -> navigate(event.waypointSymbol)
            is SystemMapEvent.ActionResultDismissed -> _localState.update { it.copy(actionResult = null) }
        }
    }

    /**
     * Fetches all waypoints for [systemSymbol] from the API and writes them into
     * [WaypointStateStore].
     *
     * The repository handles pagination internally — the caller receives all pages in a single
     * call. Because [waypointStateStore] is a reactive store, the `combine` lambda in [uiState]
     * will automatically re-run once the store is populated, so no explicit "refresh" signal is
     * needed.
     */
    private fun loadWaypoints() {
        _localState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                systemRepository.getSystemWaypoints(systemSymbol)
                _localState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _localState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load waypoints")
                }
            }
        }
    }

    /**
     * Sends a navigation command for the selected ship to [waypointSymbol].
     *
     * Fuel consumption is calculated as the difference between the ship's fuel level
     * *before* the API call (captured from the current [uiState] snapshot) and the fuel
     * level returned in the API response. Using the pre-call snapshot avoids a race where
     * the DB update from the use case fires before the fuel delta is computed.
     *
     * On success, the [SystemMapActionResult.NavigationStarted] result is stored in
     * [_localState] so the UI can display destination and fuel summary in [ActionResultCard].
     *
     * @param waypointSymbol The destination waypoint the ship should navigate to.
     */
    private fun navigate(waypointSymbol: String) {
        val ship = shipSymbol ?: return
        // Capture fuel before navigation using the current UI state snapshot (pre-navigate DB state).
        val fuelBefore = uiState.value.selectedShip?.fuelCurrent
        _localState.update { it.copy(isActionInProgress = true, actionResult = null, error = null) }
        viewModelScope.launch {
            try {
                val result = navigateShipUseCase(ship, waypointSymbol)
                _localState.update {
                    it.copy(
                        isActionInProgress = false,
                        actionResult = SystemMapActionResult.NavigationStarted(
                            destinationSymbol = waypointSymbol,
                            fuelConsumed = (fuelBefore ?: result.fuel.current) - result.fuel.current,
                            fuelRemaining = result.fuel.current
                        )
                    )
                }
            } catch (e: Exception) {
                _localState.update {
                    it.copy(isActionInProgress = false, error = e.message ?: "Navigation failed")
                }
            }
        }
    }

    /**
     * Transient UI-only state that is not backed by any persistent store.
     *
     * **Pattern:** Local-state separation. Keeping ephemeral UI state in a dedicated private
     * data class (rather than individual `MutableStateFlow` properties) ensures that all
     * transient fields are updated atomically via `copy`, eliminating partial-update races.
     * To apply in a new project: declare a private `data class LocalState(...)` inside the
     * ViewModel and expose it as a single `MutableStateFlow<LocalState>`.
     *
     * **In this project:** Holds sort mode, distance origin, multi-select filter sets, loading
     * and error flags, and the last action result. None of these survive process death or session
     * changes — they reset to defaults each time the screen is opened.
     */
    private data class LocalState(
        /** Whether the initial waypoint fetch is in progress. */
        val isLoading: Boolean = true,
        /** Non-null when the most recent fetch or navigation command produced an error. */
        val error: String? = null,
        /** Current sort mode applied to the waypoint tree. Defaults to alphabetical. */
        val sortMode: SortMode = SortMode.NAME,
        /** Reference point used when sorting by distance. Defaults to system center. */
        val distanceOrigin: DistanceOrigin = DistanceOrigin.SYSTEM_CENTER,
        /**
         * Active waypoint type filter set. Empty means "show all types".
         * Uses a `Set` so toggling is an O(1) membership test rather than a list scan.
         */
        val activeTypeFilters: Set<WaypointType> = emptySet(),
        /**
         * Active waypoint trait filter set. Empty means "show all traits".
         * A waypoint passes this filter if it has *any* trait in this set (OR semantics).
         */
        val activeTraitFilters: Set<WaypointTraitSymbol> = emptySet(),
        /** Whether a ship navigation command is currently in-flight. */
        val isActionInProgress: Boolean = false,
        /** The most recent completed action result to display, or null if none / dismissed. */
        val actionResult: SystemMapActionResult? = null
    )
}

/**
 * Filters a collection of [Waypoint] objects by the active type and trait filter sets.
 *
 * **Pattern:** Multi-select filter with OR-within-category semantics. When [typeFilters] is
 * non-empty, only waypoints whose [Waypoint.type] is a member of the set are included. When
 * [traitFilters] is non-empty, only waypoints that have *at least one* trait whose symbol
 * appears in the set are included. Both filters compose with AND: a waypoint must pass both.
 * An empty set for either filter means "no restriction" — pass all.
 *
 * To apply in a new project: represent each multi-select dimension as a `Set<EnumType>` and
 * guard each filter step with `if (set.isNotEmpty())` so a cleared filter is a no-op.
 *
 * @param waypoints The full list of waypoints to filter (already scoped to one system).
 * @param typeFilters Active [WaypointType] filter set; empty = show all types.
 * @param traitFilters Active [WaypointTraitSymbol] filter set; empty = show all traits.
 * @return A new list containing only waypoints that satisfy all active filters.
 */
private fun applyFilters(
    waypoints: Collection<Waypoint>,
    typeFilters: Set<WaypointType>,
    traitFilters: Set<WaypointTraitSymbol>
): List<Waypoint> {
    var result = waypoints.toList()
    if (typeFilters.isNotEmpty()) {
        result = result.filter { it.type in typeFilters }
    }
    if (traitFilters.isNotEmpty()) {
        // OR semantics: the waypoint qualifies if any of its traits is in the active set.
        result = result.filter { wp -> wp.traits.any { it.symbol in traitFilters } }
    }
    return result
}

/**
 * Builds a parent/orbital [WaypointNode] tree from a flat list of filtered waypoints.
 *
 * **Pattern:** Flat-list-to-tree grouping. The SpaceTraders API returns waypoints as a flat
 * list where each orbital waypoint has an `orbits` field set to the symbol of its parent.
 * This function groups them into a two-level tree (roots + children) without recursion, which
 * is safe because the API only ever has one level of orbital nesting. To apply in a new project:
 * identify parent nodes by checking whether a waypoint's own symbol appears in any other
 * waypoint's `orbits` field, or equivalently whether its `orbits` field is null/not in the
 * filtered set.
 *
 * A waypoint is treated as a **root** when either:
 * - Its `orbits` field is `null` (it does not orbit anything), OR
 * - Its parent's symbol is not present in the filtered set (the parent was filtered out).
 *
 * The second condition prevents dangling orbitals: if a filter removes the parent planet, its
 * moon is promoted to root so it still appears in the results.
 *
 * Distance from ([originX], [originY]) is computed for each node at construction time so
 * that [applySort] can sort by distance without recomputing.
 *
 * @param filtered Waypoints that passed the active filters; these become the tree nodes.
 * @param allWaypoints The unfiltered waypoint list for the system (unused currently but
 *   available for future orbital-parent lookups that cross filter boundaries).
 * @param originX X coordinate of the distance reference point.
 * @param originY Y coordinate of the distance reference point.
 * @return A flat list of root [WaypointNode] objects, each carrying their orbital children.
 */
private fun buildTree(
    filtered: List<Waypoint>,
    allWaypoints: Collection<Waypoint>,
    originX: Int,
    originY: Int
): List<WaypointNode> {
    // Pre-compute a set of filtered symbols for O(1) parent-presence checks.
    val filteredSymbols = filtered.map { it.symbol }.toSet()
    val roots = mutableListOf<WaypointNode>()

    for (wp in filtered) {
        if (wp.orbits == null || wp.orbits !in filteredSymbols) {
            // This waypoint is a root: collect all filtered waypoints that orbit it.
            val children = filtered
                .filter { it.orbits == wp.symbol }
                .map { it.toNode(originX, originY, emptyList()) }
            roots.add(wp.toNode(originX, originY, children))
        }
    }
    return roots
}

/**
 * Sorts a list of [WaypointNode] roots and normalizes orbital order.
 *
 * Root nodes are sorted by the active [SortMode]:
 * - [SortMode.NAME] — alphabetical by waypoint symbol.
 * - [SortMode.DISTANCE] — ascending distance from the chosen origin (pre-computed in
 *   [buildTree]). When [DistanceOrigin.SHIP_LOCATION] is active, distances reflect proximity
 *   to the ship's current waypoint coordinates rather than system center (0, 0).
 *
 * Orbitals within each root are always sorted alphabetically by symbol, regardless of the
 * active sort mode, to keep moons in a stable order.
 *
 * @param nodes The root nodes produced by [buildTree].
 * @param sortMode The active sort criterion.
 * @return A new sorted list with orbitals also sorted.
 */
private fun applySort(nodes: List<WaypointNode>, sortMode: SortMode): List<WaypointNode> {
    val sorted = when (sortMode) {
        SortMode.NAME -> nodes.sortedBy { it.waypoint.symbol }
        SortMode.DISTANCE -> nodes.sortedBy { it.distance }
    }
    // Always sort orbitals alphabetically for stable display order.
    return sorted.map { node ->
        node.copy(orbitals = node.orbitals.sortedBy { it.waypoint.symbol })
    }
}

/**
 * Converts a domain [Waypoint] into a display-ready [WaypointNode].
 *
 * Pre-computes boolean flags (`hasMarketplace`, `hasShipyard`, `isUncharted`) from the trait
 * list so the UI does not need to re-scan traits on every recomposition.
 *
 * @receiver The domain model waypoint to convert.
 * @param originX X coordinate of the distance reference point.
 * @param originY Y coordinate of the distance reference point.
 * @param children Pre-built orbital child nodes for this waypoint.
 * @return A [WaypointNode] ready for display in [SystemMapUiState.waypoints].
 */
private fun Waypoint.toNode(originX: Int, originY: Int, children: List<WaypointNode>): WaypointNode {
    val traitSymbols = traits.map { it.symbol }
    return WaypointNode(
        waypoint = WaypointSummary(
            symbol = symbol,
            type = type,
            x = x,
            y = y,
            traits = traitSymbols,
            hasMarketplace = WaypointTraitSymbol.MARKETPLACE in traitSymbols,
            hasShipyard = WaypointTraitSymbol.SHIPYARD in traitSymbols,
            isUncharted = WaypointTraitSymbol.UNCHARTED in traitSymbols,
            isUnderConstruction = isUnderConstruction
        ),
        distance = euclideanDistance(originX, originY, x, y),
        orbitals = children
    )
}

/**
 * Converts a domain [Ship] into the minimal [ShipSnapshot] needed by the System Map UI.
 *
 * Uses `nav.route.destination` coordinates rather than a separate position field because
 * the SpaceTraders API expresses a ship's current location as the destination of its last
 * (or current) route leg.
 *
 * @receiver The domain model ship to snapshot.
 * @return A [ShipSnapshot] containing only the fields the System Map needs.
 */
private fun Ship.toSnapshot(): ShipSnapshot = ShipSnapshot(
    symbol = symbol,
    waypointSymbol = nav.waypointSymbol,
    x = nav.route.destination.x,
    y = nav.route.destination.y,
    fuelCurrent = fuel.current,
    fuelCapacity = fuel.capacity,
    navStatus = nav.status
)
