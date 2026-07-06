package com.brokenhuskysledteam.spacetradersio.ui.mounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipMount
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.MountsRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.SystemRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.InstallMountUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RemoveMountUseCase
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
 * ViewModel for the mounts screen. Mounts are read-through (fetched into local state and
 * re-fetched after a modification); the ship is observed reactively to surface installable
 * `MOUNT_*` cargo items and to keep the state fresh after install/remove writes back cargo.
 */
@HiltViewModel
class MountsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mountsRepository: MountsRepository,
    private val fleetRepository: FleetRepository,
    private val systemRepository: SystemRepository,
    private val installMountUseCase: InstallMountUseCase,
    private val removeMountUseCase: RemoveMountUseCase
) : ViewModel() {

    private val shipSymbol: String = checkNotNull(savedStateHandle["shipSymbol"])
    private val systemSymbol: String = checkNotNull(savedStateHandle["systemSymbol"])
    private val waypointSymbol: String = checkNotNull(savedStateHandle["waypointSymbol"])

    private val _localState = MutableStateFlow(LocalState())

    val uiState: StateFlow<MountsUiState> = combine(
        fleetRepository.observeShip(shipSymbol),
        _localState
    ) { ship: Ship?, local ->
        MountsUiState(
            shipSymbol = shipSymbol,
            mounts = local.mounts,
            installable = ship?.cargo?.inventory.orEmpty().filter { it.symbol.startsWith("MOUNT_") },
            hasShipyard = local.hasShipyard,
            isLoading = local.isLoading,
            isModifying = local.isModifying,
            modResult = local.modResult,
            error = local.error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MountsUiState(shipSymbol = shipSymbol))

    init {
        load()
    }

    fun onEvent(event: MountsEvent) {
        when (event) {
            is MountsEvent.RetryClicked -> load()
            is MountsEvent.RemoveClicked -> modify("REMOVE", event.mountSymbol) {
                removeMountUseCase(shipSymbol, event.mountSymbol)
            }
            is MountsEvent.InstallClicked -> modify("INSTALL", event.mountSymbol) {
                installMountUseCase(shipSymbol, event.mountSymbol)
            }
            is MountsEvent.ResultDismissed -> _localState.update { it.copy(modResult = null) }
        }
    }

    private fun load() {
        _localState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val mounts = mountsRepository.getMounts(shipSymbol)
                val hasShipyard = runCatching {
                    systemRepository.getWaypoint(systemSymbol, waypointSymbol)
                        .traits.any { it.symbol == WaypointTraitSymbol.SHIPYARD }
                }.getOrDefault(false)
                _localState.update { it.copy(mounts = mounts, hasShipyard = hasShipyard, isLoading = false) }
            } catch (e: Exception) {
                _localState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load mounts") }
            }
        }
    }

    private fun modify(
        action: String,
        mountSymbol: String,
        block: suspend () -> com.brokenhuskysledteam.spacetradersio.sdk.domain.model.MountModificationResult
    ) {
        _localState.update { it.copy(isModifying = true) }
        viewModelScope.launch {
            try {
                val result = block()
                _localState.update {
                    it.copy(
                        mounts = result.mounts,
                        modResult = MountModResult.Success(action, mountSymbol, result.transaction.totalPrice, result.agent.credits)
                    )
                }
            } catch (e: Exception) {
                _localState.update { it.copy(modResult = MountModResult.Failure(e.message ?: "$action failed")) }
            } finally {
                _localState.update { it.copy(isModifying = false) }
            }
        }
    }

    private data class LocalState(
        val mounts: List<ShipMount> = emptyList(),
        val hasShipyard: Boolean = false,
        val isLoading: Boolean = true,
        val isModifying: Boolean = false,
        val modResult: MountModResult? = null,
        val error: String? = null
    )
}
