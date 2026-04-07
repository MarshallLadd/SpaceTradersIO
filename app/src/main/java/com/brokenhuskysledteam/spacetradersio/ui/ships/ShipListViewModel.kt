package com.brokenhuskysledteam.spacetradersio.ui.ships

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.navigation.NavigationTarget
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShipListViewModel @Inject constructor(
    private val fleetStateStore: FleetStateStore,
    private val fleetRepository: FleetRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ShipListUiState> = combine(
        fleetStateStore.entities,
        _isLoading,
        _error
    ) { ships, isLoading, error ->
        ShipListUiState(
            ships = ships.values.map { it.toSummary() },
            isLoading = isLoading,
            error = error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ShipListUiState())

    private val _navigationEvent = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    init {
        if (fleetStateStore.entities.value.isEmpty()) {
            loadShips()
        }
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
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                fleetRepository.refreshMyShips()
                _isLoading.value = false
            } catch (e: Exception) {
                _isLoading.value = false
                _error.value = e.message ?: "Failed to load ships"
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
