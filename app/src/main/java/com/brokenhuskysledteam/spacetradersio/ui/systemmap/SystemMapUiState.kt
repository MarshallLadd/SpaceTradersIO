package com.brokenhuskysledteam.spacetradersio.ui.systemmap

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType

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

data class WaypointNode(
    val waypoint: WaypointSummary,
    val distance: Double,
    val orbitals: List<WaypointNode>
)

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

data class ShipSnapshot(
    val symbol: String,
    val waypointSymbol: String,
    val x: Int,
    val y: Int,
    val fuelCurrent: Int,
    val fuelCapacity: Int,
    val navStatus: ShipNavStatus
)

enum class SortMode { NAME, DISTANCE }
enum class DistanceOrigin { SYSTEM_CENTER, SHIP_LOCATION }

sealed interface SystemMapActionResult {
    data class NavigationStarted(
        val destinationSymbol: String,
        val fuelConsumed: Int,
        val fuelRemaining: Int
    ) : SystemMapActionResult
}

sealed interface SystemMapEvent {
    data object RetryClicked : SystemMapEvent
    data class SortModeSelected(val mode: SortMode) : SystemMapEvent
    data class DistanceOriginSelected(val origin: DistanceOrigin) : SystemMapEvent
    data class TypeFilterToggled(val type: WaypointType) : SystemMapEvent
    data class TraitFilterToggled(val trait: WaypointTraitSymbol) : SystemMapEvent
    data class NavigateToWaypoint(val waypointSymbol: String) : SystemMapEvent
    data object ActionResultDismissed : SystemMapEvent
}
