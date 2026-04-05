package com.brokenhuskysledteam.spacetradersio.ui.ships

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.DockShipUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.OrbitShipUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RefuelShipUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShipDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val fleetRepository: FleetRepository,
    private val orbitShipUseCase: OrbitShipUseCase,
    private val dockShipUseCase: DockShipUseCase,
    private val refuelShipUseCase: RefuelShipUseCase
) : ViewModel() {

    private val shipSymbol: String = checkNotNull(savedStateHandle["shipSymbol"])

    private val _uiState = MutableStateFlow(ShipDetailUiState())
    val uiState: StateFlow<ShipDetailUiState> = _uiState.asStateFlow()

    init {
        loadShip()
    }

    fun onEvent(event: ShipDetailEvent) {
        when (event) {
            is ShipDetailEvent.OrbitClicked -> performAction {
                val nav = orbitShipUseCase(shipSymbol)
                _uiState.update { state ->
                    state.copy(
                        ship = state.ship?.withNav(nav),
                        actionResult = ActionResult.Orbited(nav.waypointSymbol)
                    )
                }
            }

            is ShipDetailEvent.DockClicked -> performAction {
                val nav = dockShipUseCase(shipSymbol)
                _uiState.update { state ->
                    state.copy(
                        ship = state.ship?.withNav(nav),
                        actionResult = ActionResult.Docked(nav.waypointSymbol)
                    )
                }
            }

            is ShipDetailEvent.RefuelClicked -> performAction {
                val result = refuelShipUseCase(shipSymbol)
                _uiState.update { state ->
                    state.copy(
                        ship = state.ship?.copy(
                            fuelCurrent = result.fuel.current,
                            fuelCapacity = result.fuel.capacity
                        ),
                        actionResult = ActionResult.Refueled(
                            fuelAdded = result.transaction.units,
                            totalCost = result.transaction.totalPrice,
                            newCredits = result.agent.credits
                        )
                    )
                }
            }

            is ShipDetailEvent.ActionResultDismissed -> _uiState.update { it.copy(actionResult = null) }

            is ShipDetailEvent.RetryClicked -> loadShip()
        }
    }

    private fun loadShip() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val ship = fleetRepository.getMyShip(shipSymbol)
                _uiState.update { it.copy(ship = ship.toDetail(), isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load ship")
                }
            }
        }
    }

    // Executes a ship action: sets isActionInProgress, runs the block, then clears it.
    // Errors are surfaced via the error field (not action result).
    private fun performAction(block: suspend () -> Unit) {
        _uiState.update { it.copy(isActionInProgress = true, actionResult = null, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Action failed") }
            } finally {
                _uiState.update { it.copy(isActionInProgress = false) }
            }
        }
    }
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

// Updates the nav-derived fields in ShipDetail when an orbit/dock action
// returns a new ShipNav (without re-fetching the full ship from the API).
private fun ShipDetail.withNav(nav: ShipNav): ShipDetail {
    val inTransit = nav.status == ShipNavStatus.IN_TRANSIT
    return copy(
        navStatus = nav.status,
        flightMode = nav.flightMode,
        systemSymbol = nav.systemSymbol,
        waypointSymbol = nav.waypointSymbol,
        destinationSymbol = nav.route.destination.symbol,
        destinationType = nav.route.destination.type,
        arrivalTime = if (inTransit) nav.route.arrivalTime else null,
        departureTime = if (inTransit) nav.route.departureTime else null
    )
}
