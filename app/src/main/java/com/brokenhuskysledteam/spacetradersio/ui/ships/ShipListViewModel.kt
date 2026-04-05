package com.brokenhuskysledteam.spacetradersio.ui.ships

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.navigation.NavigationTarget
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShipListViewModel @Inject constructor(
    private val fleetRepository: FleetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShipListUiState())
    val uiState: StateFlow<ShipListUiState> = _uiState.asStateFlow()

    private val _navigationEvent = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    init {
        loadShips()
    }

    fun onEvent(event: ShipListEvent) {
        when (event) {
            is ShipListEvent.RetryClicked -> loadShips()
            is ShipListEvent.ShipSelected -> viewModelScope.launch {
                _navigationEvent.send(NavigationTarget.ShipDetail(event.symbol))
            }
        }
    }

    private fun loadShips() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val ships = fleetRepository.getMyShips()
                _uiState.update { it.copy(ships = ships.map { ship -> ship.toSummary() }, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load ships")
                }
            }
        }
    }
}

private fun Ship.toSummary(): ShipSummary {
    val inTransit = nav.status == ShipNavStatus.IN_TRANSIT
    return ShipSummary(
        symbol = symbol,
        frameName = frameName,
        status = nav.status,
        waypointSymbol = nav.waypointSymbol,
        systemSymbol = nav.systemSymbol,
        arrivalTime = if (inTransit) nav.route.arrivalTime else null,
        departureTime = if (inTransit) nav.route.departureTime else null
    )
}
