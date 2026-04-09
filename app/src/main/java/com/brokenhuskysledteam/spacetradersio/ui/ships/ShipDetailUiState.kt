package com.brokenhuskysledteam.spacetradersio.ui.ships

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavFlightMode
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipRole
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import kotlin.time.Instant

data class ShipDetailUiState(
    val ship: ShipDetail? = null,
    val isLoading: Boolean = true,
    val isActionInProgress: Boolean = false,
    val actionResult: ActionResult? = null,
    val error: String? = null
)

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
    val cargoCapacity: Int
)

// Command output displayed after an action completes. Shown in amber to
// distinguish from static ship data.
sealed interface ActionResult {
    data class Orbited(val waypointSymbol: String) : ActionResult
    data class Docked(val waypointSymbol: String) : ActionResult
    data class Refueled(
        val fuelAdded: Int,
        val totalCost: Int,
        val newCredits: Long
    ) : ActionResult
}

sealed interface ShipDetailEvent {
    data object OrbitClicked : ShipDetailEvent
    data object DockClicked : ShipDetailEvent
    data object RefuelClicked : ShipDetailEvent
    data object ActionResultDismissed : ShipDetailEvent
    data object RetryClicked : ShipDetailEvent
    data class ViewSystemClicked(
        val systemSymbol: String,
        val waypointSymbol: String,
        val shipSymbol: String
    ) : ShipDetailEvent
}
