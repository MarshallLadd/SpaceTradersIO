package com.brokenhuskysledteam.spacetradersio.ui.shipyard

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Shipyard
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipyardShip

data class ShipyardUiState(
    val waypointSymbol: String = "",
    val shipyard: Shipyard? = null,
    val isRefreshing: Boolean = true,
    val isPurchasing: Boolean = false,
    val pendingPurchase: ShipyardShip? = null,
    val purchaseResult: PurchaseResult? = null,
    val error: String? = null
)

sealed interface PurchaseResult {
    data class Success(
        val shipSymbol: String,
        val creditsSpent: Int,
        val remainingCredits: Long
    ) : PurchaseResult
    data class Failure(val message: String) : PurchaseResult
}

sealed interface ShipyardEvent {
    data object RetryClicked : ShipyardEvent
    data class PurchaseShipClicked(val ship: ShipyardShip) : ShipyardEvent
    data object PurchaseConfirmed : ShipyardEvent
    data object PurchaseDismissed : ShipyardEvent
    data object PurchaseResultDismissed : ShipyardEvent
}
