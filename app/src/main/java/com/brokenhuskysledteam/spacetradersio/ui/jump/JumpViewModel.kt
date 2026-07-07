package com.brokenhuskysledteam.spacetradersio.ui.jump

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TravelRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.JumpShipUseCase
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
 * ViewModel for the jump screen. Loads the jump gate's connections (read-through) and observes
 * the ship so the jump action disables while a cooldown is active (and re-enables when it clears).
 */
@HiltViewModel
class JumpViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val travelRepository: TravelRepository,
    private val fleetRepository: FleetRepository,
    private val jumpShipUseCase: JumpShipUseCase
) : ViewModel() {

    private val shipSymbol: String = checkNotNull(savedStateHandle["shipSymbol"])
    private val systemSymbol: String = checkNotNull(savedStateHandle["systemSymbol"])
    private val waypointSymbol: String = checkNotNull(savedStateHandle["waypointSymbol"])

    private val _localState = MutableStateFlow(LocalState())

    val uiState: StateFlow<JumpUiState> = combine(
        fleetRepository.observeShip(shipSymbol),
        _localState
    ) { ship: Ship?, local ->
        JumpUiState(
            shipSymbol = shipSymbol,
            gateSymbol = waypointSymbol,
            connections = local.connections,
            onCooldown = ship?.cooldown?.expiration != null,
            isLoading = local.isLoading,
            isJumping = local.isJumping,
            result = local.result,
            error = local.error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, JumpUiState(shipSymbol = shipSymbol, gateSymbol = waypointSymbol))

    init {
        load()
    }

    fun onEvent(event: JumpEvent) {
        when (event) {
            is JumpEvent.RetryClicked -> load()
            is JumpEvent.JumpClicked -> jump(event.destination)
            is JumpEvent.ResultDismissed -> _localState.update { it.copy(result = null) }
        }
    }

    private fun load() {
        _localState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val gate = travelRepository.getJumpGate(systemSymbol, waypointSymbol)
                _localState.update { it.copy(connections = gate.connections, isLoading = false) }
            } catch (e: Exception) {
                _localState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load jump gate") }
            }
        }
    }

    private fun jump(destination: String) {
        _localState.update { it.copy(isJumping = true) }
        viewModelScope.launch {
            val result = try {
                jumpShipUseCase(shipSymbol, destination)
                JumpResultUi.Success(destination)
            } catch (e: Exception) {
                JumpResultUi.Failure(e.message ?: "Jump failed")
            }
            _localState.update { it.copy(isJumping = false, result = result) }
        }
    }

    private data class LocalState(
        val connections: List<String> = emptyList(),
        val isLoading: Boolean = true,
        val isJumping: Boolean = false,
        val result: JumpResultUi? = null,
        val error: String? = null
    )
}
