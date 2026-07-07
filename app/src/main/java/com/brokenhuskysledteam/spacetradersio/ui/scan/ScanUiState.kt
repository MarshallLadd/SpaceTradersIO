package com.brokenhuskysledteam.spacetradersio.ui.scan

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ScannedSystem
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Waypoint

/**
 * UI state for the scan screen (UDF). Cooldown is observed from the ship; scan results and the
 * last action are ViewModel-local.
 *
 * @property shipSymbol The scanning ship.
 * @property onCooldown Whether a cooldown blocks scanning (observed from the ship).
 * @property isBusy Whether a scan/chart is executing.
 * @property systems The most recent system-scan results.
 * @property waypoints The most recent waypoint-scan results (with revealed traits).
 * @property result Transient result of the last action.
 * @property error Error, or `null`.
 */
data class ScanUiState(
    val shipSymbol: String = "",
    val onCooldown: Boolean = false,
    val isBusy: Boolean = false,
    val systems: List<ScannedSystem> = emptyList(),
    val waypoints: List<Waypoint> = emptyList(),
    val result: ScanResult? = null,
    val error: String? = null
) {
    /** Scans are actionable when not on cooldown and idle. (Charting has no cooldown.) */
    val canScan: Boolean get() = !onCooldown && !isBusy
}

/** Transient feedback after a scan/chart. */
sealed interface ScanResult {
    data class SystemsScanned(val count: Int) : ScanResult
    data class WaypointsScanned(val count: Int) : ScanResult
    data class Charted(val waypointSymbol: String) : ScanResult
    data class Failure(val message: String) : ScanResult
}

/** User events for the scan screen. */
sealed interface ScanEvent {
    data object ScanSystemsClicked : ScanEvent
    data object ScanWaypointsClicked : ScanEvent
    data object ChartClicked : ScanEvent
    data object ResultDismissed : ScanEvent
}
