package com.brokenhuskysledteam.spacetradersio.ui.systemmap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Waypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.euclideanDistance
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.SystemRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.WaypointStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.NavigateShipUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SystemMapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val systemRepository: SystemRepository,
    private val fleetStateStore: FleetStateStore,
    private val waypointStateStore: WaypointStateStore,
    private val navigateShipUseCase: NavigateShipUseCase
) : ViewModel() {

    private val systemSymbol: String = checkNotNull(savedStateHandle["systemSymbol"])
    private val focusWaypointSymbol: String? = savedStateHandle["focusWaypointSymbol"]
    private val shipSymbol: String? = savedStateHandle["shipSymbol"]

    private val _localState = MutableStateFlow(LocalState())

    val uiState: StateFlow<SystemMapUiState> = combine(
        waypointStateStore.entities,
        fleetStateStore.entities,
        _localState
    ) { waypointMap, fleetMap, local ->
        val ship = shipSymbol?.let { fleetMap[it] }
        val shipSnapshot = ship?.toSnapshot()

        val allWaypoints = waypointMap.values.filter { it.systemSymbol == systemSymbol }
        val filtered = applyFilters(allWaypoints, local.activeTypeFilters, local.activeTraitFilters)

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

    private fun navigate(waypointSymbol: String) {
        val ship = shipSymbol ?: return
        _localState.update { it.copy(isActionInProgress = true, actionResult = null, error = null) }
        viewModelScope.launch {
            try {
                val result = navigateShipUseCase(ship, waypointSymbol)
                val fuelBefore = fleetStateStore.entities.value[ship]?.fuel?.current ?: result.fuel.current
                _localState.update {
                    it.copy(
                        isActionInProgress = false,
                        actionResult = SystemMapActionResult.NavigationStarted(
                            destinationSymbol = waypointSymbol,
                            fuelConsumed = fuelBefore - result.fuel.current,
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

    private data class LocalState(
        val isLoading: Boolean = true,
        val error: String? = null,
        val sortMode: SortMode = SortMode.NAME,
        val distanceOrigin: DistanceOrigin = DistanceOrigin.SYSTEM_CENTER,
        val activeTypeFilters: Set<WaypointType> = emptySet(),
        val activeTraitFilters: Set<WaypointTraitSymbol> = emptySet(),
        val isActionInProgress: Boolean = false,
        val actionResult: SystemMapActionResult? = null
    )
}

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
        result = result.filter { wp -> wp.traits.any { it.symbol in traitFilters } }
    }
    return result
}

private fun buildTree(
    filtered: List<Waypoint>,
    allWaypoints: Collection<Waypoint>,
    originX: Int,
    originY: Int
): List<WaypointNode> {
    val filteredSymbols = filtered.map { it.symbol }.toSet()
    val roots = mutableListOf<WaypointNode>()

    for (wp in filtered) {
        if (wp.orbits == null || wp.orbits !in filteredSymbols) {
            val children = filtered
                .filter { it.orbits == wp.symbol }
                .map { it.toNode(originX, originY, emptyList()) }
            roots.add(wp.toNode(originX, originY, children))
        }
    }
    return roots
}

private fun applySort(nodes: List<WaypointNode>, sortMode: SortMode): List<WaypointNode> {
    val sorted = when (sortMode) {
        SortMode.NAME -> nodes.sortedBy { it.waypoint.symbol }
        SortMode.DISTANCE -> nodes.sortedBy { it.distance }
    }
    return sorted.map { node ->
        node.copy(orbitals = node.orbitals.sortedBy { it.waypoint.symbol })
    }
}

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

private fun Ship.toSnapshot(): ShipSnapshot = ShipSnapshot(
    symbol = symbol,
    waypointSymbol = nav.waypointSymbol,
    x = nav.route.destination.x,
    y = nav.route.destination.y,
    fuelCurrent = fuel.current,
    fuelCapacity = fuel.capacity,
    navStatus = nav.status
)
