package com.brokenhuskysledteam.spacetradersio.ui.mining

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Survey
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.CreateSurveyUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.ExtractResourcesUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.ExtractWithSurveyUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.JettisonCargoUseCase
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
 * ViewModel for the mining screen. Cargo and cooldown are observed reactively from the ship, so
 * when the RefreshScheduler auto-refreshes the ship after a cooldown clears, the action buttons
 * re-enable automatically. Surveys and the last action result are ViewModel-local.
 */
@HiltViewModel
class MiningViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val fleetRepository: FleetRepository,
    private val extractResourcesUseCase: ExtractResourcesUseCase,
    private val extractWithSurveyUseCase: ExtractWithSurveyUseCase,
    private val createSurveyUseCase: CreateSurveyUseCase,
    private val jettisonCargoUseCase: JettisonCargoUseCase
) : ViewModel() {

    private val shipSymbol: String = checkNotNull(savedStateHandle["shipSymbol"])

    private val _localState = MutableStateFlow(LocalState())

    val uiState: StateFlow<MiningUiState> = combine(
        fleetRepository.observeShip(shipSymbol),
        _localState
    ) { ship: Ship?, local ->
        MiningUiState(
            shipSymbol = shipSymbol,
            inOrbit = ship?.nav?.status == ShipNavStatus.IN_ORBIT,
            onCooldown = ship?.cooldown?.expiration != null,
            cooldownRemainingSeconds = ship?.cooldown?.remainingSeconds ?: 0,
            cargoUnits = ship?.cargo?.units ?: 0,
            cargoCapacity = ship?.cargo?.capacity ?: 0,
            inventory = ship?.cargo?.inventory.orEmpty(),
            surveys = local.surveys,
            isBusy = local.isBusy,
            result = local.result,
            error = local.error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MiningUiState(shipSymbol = shipSymbol))

    init {
        // Ensure we have current ship state (cargo/cooldown) when the screen opens.
        viewModelScope.launch {
            runCatching { fleetRepository.refreshMyShip(shipSymbol) }
                .onFailure { e -> _localState.update { it.copy(error = e.message) } }
        }
    }

    fun onEvent(event: MiningEvent) {
        when (event) {
            is MiningEvent.ExtractClicked -> act {
                val r = extractResourcesUseCase(shipSymbol)
                MiningResult.Extracted(r.yieldSymbol, r.yieldUnits)
            }
            is MiningEvent.SurveyClicked -> act {
                val r = createSurveyUseCase(shipSymbol)
                _localState.update { it.copy(surveys = r.surveys) }
                MiningResult.Surveyed(r.surveys.size)
            }
            is MiningEvent.ExtractWithSurveyClicked -> act {
                val r = extractWithSurveyUseCase(shipSymbol, event.survey)
                MiningResult.Extracted(r.yieldSymbol, r.yieldUnits)
            }
            is MiningEvent.JettisonClicked -> act {
                jettisonCargoUseCase(shipSymbol, event.tradeSymbol, event.units)
                MiningResult.Jettisoned(event.tradeSymbol, event.units)
            }
            is MiningEvent.ResultDismissed -> _localState.update { it.copy(result = null) }
        }
    }

    private fun act(block: suspend () -> MiningResult) {
        _localState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val result = try {
                block()
            } catch (e: Exception) {
                MiningResult.Failure(e.message ?: "Action failed")
            }
            _localState.update { it.copy(isBusy = false, result = result) }
        }
    }

    private data class LocalState(
        val surveys: List<Survey> = emptyList(),
        val isBusy: Boolean = false,
        val result: MiningResult? = null,
        val error: String? = null
    )
}
