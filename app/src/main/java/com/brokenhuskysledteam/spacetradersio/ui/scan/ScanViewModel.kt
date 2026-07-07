package com.brokenhuskysledteam.spacetradersio.ui.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ScannedSystem
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Waypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.ChartWaypointUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.ScanSystemsUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.ScanWaypointsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the scan screen. The scan cooldown is observed from the ship (so scan buttons
 * disable during cooldown and re-enable when the scheduler refreshes the ship). Scan results are
 * ViewModel-local.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val fleetRepository: FleetRepository,
    private val scanSystemsUseCase: ScanSystemsUseCase,
    private val scanWaypointsUseCase: ScanWaypointsUseCase,
    private val chartWaypointUseCase: ChartWaypointUseCase
) : ViewModel() {

    private val shipSymbol: String = checkNotNull(savedStateHandle["shipSymbol"])

    private val _localState = MutableStateFlow(LocalState())

    val uiState: StateFlow<ScanUiState> = combine(
        fleetRepository.observeShip(shipSymbol),
        _localState
    ) { ship: Ship?, local ->
        ScanUiState(
            shipSymbol = shipSymbol,
            onCooldown = ship?.cooldown?.expiration != null,
            isBusy = local.isBusy,
            systems = local.systems,
            waypoints = local.waypoints,
            result = local.result,
            error = local.error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ScanUiState(shipSymbol = shipSymbol))

    init {
        viewModelScope.launch {
            runCatching { fleetRepository.refreshMyShip(shipSymbol) }
                .onFailure { e -> _localState.update { it.copy(error = e.message) } }
        }
    }

    fun onEvent(event: ScanEvent) {
        when (event) {
            is ScanEvent.ScanSystemsClicked -> act {
                val r = scanSystemsUseCase(shipSymbol)
                _localState.update { it.copy(systems = r.systems) }
                ScanResult.SystemsScanned(r.systems.size)
            }
            is ScanEvent.ScanWaypointsClicked -> act {
                val r = scanWaypointsUseCase(shipSymbol)
                _localState.update { it.copy(waypoints = r.waypoints) }
                ScanResult.WaypointsScanned(r.waypoints.size)
            }
            is ScanEvent.ChartClicked -> act {
                val r = chartWaypointUseCase(shipSymbol)
                ScanResult.Charted(r.waypoint.symbol)
            }
            is ScanEvent.ResultDismissed -> _localState.update { it.copy(result = null) }
        }
    }

    private fun act(block: suspend () -> ScanResult) {
        _localState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val result = try {
                block()
            } catch (e: Exception) {
                ScanResult.Failure(e.message ?: "Action failed")
            }
            _localState.update { it.copy(isBusy = false, result = result) }
        }
    }

    private data class LocalState(
        val isBusy: Boolean = false,
        val systems: List<ScannedSystem> = emptyList(),
        val waypoints: List<Waypoint> = emptyList(),
        val result: ScanResult? = null,
        val error: String? = null
    )
}
