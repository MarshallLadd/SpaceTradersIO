package com.brokenhuskysledteam.spacetradersio.ui.mining

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.CargoItem
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Survey

/**
 * UI state for the mining screen (UDF). Cargo and cooldown are observed reactively from the
 * ship; surveys and the last result are ViewModel-local.
 *
 * @property shipSymbol The ship mining.
 * @property inOrbit Whether the ship is IN_ORBIT (extraction/surveying require orbit).
 * @property onCooldown Whether an action cooldown is active (extract/survey disabled).
 * @property cooldownRemainingSeconds Remaining cooldown seconds from the last fetch (display only).
 * @property cargoUnits Current cargo units.
 * @property cargoCapacity Cargo capacity.
 * @property inventory Cargo items (each jettisonable).
 * @property surveys Surveys created this session, usable for targeted extraction.
 * @property isBusy Whether an action is executing.
 * @property result Transient result of the last action.
 * @property error Load error, or `null`.
 */
data class MiningUiState(
    val shipSymbol: String = "",
    val inOrbit: Boolean = false,
    val onCooldown: Boolean = false,
    val cooldownRemainingSeconds: Int = 0,
    val cargoUnits: Int = 0,
    val cargoCapacity: Int = 0,
    val inventory: List<CargoItem> = emptyList(),
    val surveys: List<Survey> = emptyList(),
    val isBusy: Boolean = false,
    val result: MiningResult? = null,
    val error: String? = null
) {
    /** Extraction/survey are actionable only when in orbit, not on cooldown, and idle. */
    val canAct: Boolean get() = inOrbit && !onCooldown && !isBusy
}

/** Transient feedback after a mining action. */
sealed interface MiningResult {
    data class Extracted(val yieldSymbol: String, val units: Int) : MiningResult
    data class Surveyed(val count: Int) : MiningResult
    data class Jettisoned(val tradeSymbol: String, val units: Int) : MiningResult
    data class Failure(val message: String) : MiningResult
}

/** User events for the mining screen. */
sealed interface MiningEvent {
    data object ExtractClicked : MiningEvent
    data object SurveyClicked : MiningEvent
    data class ExtractWithSurveyClicked(val survey: Survey) : MiningEvent
    data class JettisonClicked(val tradeSymbol: String, val units: Int) : MiningEvent
    data object ResultDismissed : MiningEvent
}
