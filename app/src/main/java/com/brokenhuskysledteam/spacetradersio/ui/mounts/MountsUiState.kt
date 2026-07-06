package com.brokenhuskysledteam.spacetradersio.ui.mounts

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.CargoItem
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipMount

/**
 * UI state for the mounts screen (UDF).
 *
 * @property shipSymbol The ship whose mounts are shown.
 * @property mounts The installed mounts.
 * @property installable Cargo items that are mounts (symbol starts with `MOUNT_`) and can be
 *   installed when docked at a shipyard.
 * @property hasShipyard Whether the ship's current waypoint has a shipyard (install/remove gate).
 * @property isLoading `true` during the initial mounts fetch.
 * @property isModifying `true` while an install/remove request is executing.
 * @property modResult Transient result of the last install/remove.
 * @property error Human-readable error, or `null`.
 */
data class MountsUiState(
    val shipSymbol: String = "",
    val mounts: List<ShipMount> = emptyList(),
    val installable: List<CargoItem> = emptyList(),
    val hasShipyard: Boolean = false,
    val isLoading: Boolean = true,
    val isModifying: Boolean = false,
    val modResult: MountModResult? = null,
    val error: String? = null
)

/** Transient feedback after an install/remove. */
sealed interface MountModResult {
    data class Success(
        val action: String,
        val mountSymbol: String,
        val fee: Int,
        val newCredits: Long
    ) : MountModResult

    data class Failure(val message: String) : MountModResult
}

/** User events for the mounts screen. */
sealed interface MountsEvent {
    data object RetryClicked : MountsEvent
    data class RemoveClicked(val mountSymbol: String) : MountsEvent
    data class InstallClicked(val mountSymbol: String) : MountsEvent
    data object ResultDismissed : MountsEvent
}
