package com.brokenhuskysledteam.spacetradersio.ui.ships

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.DockShipUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.OrbitShipUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RefuelShipUseCase
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
class ShipDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val fleetStateStore: FleetStateStore,
    private val fleetRepository: FleetRepository,
    private val orbitShipUseCase: OrbitShipUseCase,
    private val dockShipUseCase: DockShipUseCase,
    private val refuelShipUseCase: RefuelShipUseCase
) : ViewModel() {

    private val shipSymbol: String = checkNotNull(savedStateHandle["shipSymbol"])

    private val _localState = MutableStateFlow(LocalState())

    val uiState: StateFlow<ShipDetailUiState> = combine(
        fleetStateStore.observe(shipSymbol),
        _localState
    ) { ship, local ->
        ShipDetailUiState(
            ship = ship?.toDetail(),
            isLoading = local.isLoading,
            isActionInProgress = local.isActionInProgress,
            actionResult = local.actionResult,
            error = local.error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ShipDetailUiState())

    init {
        if (fleetStateStore.entities.value[shipSymbol] == null) {
            loadShip()
        }
    }

    fun onEvent(event: ShipDetailEvent) {
        when (event) {
            is ShipDetailEvent.OrbitClicked -> performAction {
                val nav = orbitShipUseCase(shipSymbol)
                _localState.update { it.copy(actionResult = ActionResult.Orbited(nav.waypointSymbol)) }
            }

            is ShipDetailEvent.DockClicked -> performAction {
                val nav = dockShipUseCase(shipSymbol)
                _localState.update { it.copy(actionResult = ActionResult.Docked(nav.waypointSymbol)) }
            }

            is ShipDetailEvent.RefuelClicked -> performAction {
                val result = refuelShipUseCase(shipSymbol)
                _localState.update {
                    it.copy(
                        actionResult = ActionResult.Refueled(
                            fuelAdded = result.transaction.units,
                            totalCost = result.transaction.totalPrice,
                            newCredits = result.agent.credits
                        )
                    )
                }
            }

            is ShipDetailEvent.ActionResultDismissed -> _localState.update { it.copy(actionResult = null) }

            is ShipDetailEvent.RetryClicked -> loadShip()
        }
    }

    private fun loadShip() {
        _localState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                fleetRepository.getMyShip(shipSymbol)
                _localState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _localState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load ship")
                }
            }
        }
    }

    private fun performAction(block: suspend () -> Unit) {
        _localState.update { it.copy(isActionInProgress = true, actionResult = null, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _localState.update { it.copy(error = e.message ?: "Action failed") }
            } finally {
                _localState.update { it.copy(isActionInProgress = false) }
            }
        }
    }

    private data class LocalState(
        val isLoading: Boolean = false,
        val isActionInProgress: Boolean = false,
        val actionResult: ActionResult? = null,
        val error: String? = null
    )
}

private fun Ship.toDetail(): ShipDetail {
    val inTransit = nav.status == ShipNavStatus.IN_TRANSIT
    return ShipDetail(
        symbol = symbol,
        frameName = frameName,
        role = registration.role,
        navStatus = nav.status,
        flightMode = nav.flightMode,
        systemSymbol = nav.systemSymbol,
        waypointSymbol = nav.waypointSymbol,
        destinationSymbol = nav.route.destination.symbol,
        destinationType = nav.route.destination.type,
        arrivalTime = if (inTransit) nav.route.arrivalTime else null,
        departureTime = if (inTransit) nav.route.departureTime else null,
        fuelCurrent = fuel.current,
        fuelCapacity = fuel.capacity,
        cargoUnits = cargo.units,
        cargoCapacity = cargo.capacity
    )
}
