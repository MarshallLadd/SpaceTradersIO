package com.brokenhuskysledteam.spacetradersio.ui.shipyard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipyardShip
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.ShipyardRepository
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
class ShipyardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val shipyardRepository: ShipyardRepository
) : ViewModel() {

    private val systemSymbol: String = checkNotNull(savedStateHandle["systemSymbol"])
    private val waypointSymbol: String = checkNotNull(savedStateHandle["waypointSymbol"])

    private val _localState = MutableStateFlow(LocalState())

    val uiState: StateFlow<ShipyardUiState> = combine(
        shipyardRepository.observeShipyard(waypointSymbol),
        _localState
    ) { shipyard, local ->
        ShipyardUiState(
            waypointSymbol = waypointSymbol,
            shipyard = shipyard,
            isRefreshing = local.isRefreshing,
            isPurchasing = local.isPurchasing,
            pendingPurchase = local.pendingPurchase,
            purchaseResult = local.purchaseResult,
            error = local.error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ShipyardUiState(waypointSymbol = waypointSymbol))

    init {
        refresh()
    }

    fun onEvent(event: ShipyardEvent) {
        when (event) {
            is ShipyardEvent.RetryClicked -> refresh()
            is ShipyardEvent.PurchaseShipClicked -> _localState.update { it.copy(pendingPurchase = event.ship) }
            is ShipyardEvent.PurchaseDismissed -> _localState.update { it.copy(pendingPurchase = null) }
            is ShipyardEvent.PurchaseConfirmed -> confirmPurchase()
            is ShipyardEvent.PurchaseResultDismissed -> _localState.update { it.copy(purchaseResult = null) }
        }
    }

    private fun refresh() {
        _localState.update { it.copy(isRefreshing = true, error = null) }
        viewModelScope.launch {
            try {
                shipyardRepository.refreshShipyard(systemSymbol, waypointSymbol)
            } catch (e: Exception) {
                _localState.update { it.copy(error = e.message ?: "Failed to load shipyard") }
            } finally {
                _localState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private fun confirmPurchase() {
        val ship = _localState.value.pendingPurchase ?: return
        _localState.update { it.copy(isPurchasing = true, pendingPurchase = null) }
        viewModelScope.launch {
            try {
                shipyardRepository.purchaseShip(ship.type, waypointSymbol)
                _localState.update {
                    it.copy(
                        purchaseResult = PurchaseResult.Success(
                            shipSymbol = "${ship.type.name}-NEW",
                            creditsSpent = ship.purchasePrice,
                            remainingCredits = 0L
                        )
                    )
                }
            } catch (e: Exception) {
                _localState.update {
                    it.copy(purchaseResult = PurchaseResult.Failure(e.message ?: "Purchase failed"))
                }
            } finally {
                _localState.update { it.copy(isPurchasing = false) }
            }
        }
    }

    private data class LocalState(
        val isRefreshing: Boolean = true,
        val isPurchasing: Boolean = false,
        val pendingPurchase: ShipyardShip? = null,
        val purchaseResult: PurchaseResult? = null,
        val error: String? = null
    )
}
