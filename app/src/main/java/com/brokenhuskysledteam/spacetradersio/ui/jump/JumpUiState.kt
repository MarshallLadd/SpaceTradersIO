package com.brokenhuskysledteam.spacetradersio.ui.jump

/**
 * UI state for the jump screen (UDF).
 *
 * @property shipSymbol The ship jumping.
 * @property gateSymbol The jump gate the ship is at.
 * @property connections Connected jump-gate waypoints (each a jump destination).
 * @property onCooldown Whether a cooldown blocks jumping (observed from the ship).
 * @property isLoading `true` while loading the gate connections.
 * @property isJumping `true` while a jump is executing.
 * @property result Transient result of the last jump.
 * @property error Load error, or `null`.
 */
data class JumpUiState(
    val shipSymbol: String = "",
    val gateSymbol: String = "",
    val connections: List<String> = emptyList(),
    val onCooldown: Boolean = false,
    val isLoading: Boolean = true,
    val isJumping: Boolean = false,
    val result: JumpResultUi? = null,
    val error: String? = null
) {
    val canJump: Boolean get() = !onCooldown && !isJumping
}

/** Transient feedback after a jump. */
sealed interface JumpResultUi {
    data class Success(val destination: String) : JumpResultUi
    data class Failure(val message: String) : JumpResultUi
}

/** User events for the jump screen. */
sealed interface JumpEvent {
    data object RetryClicked : JumpEvent
    data class JumpClicked(val destination: String) : JumpEvent
    data object ResultDismissed : JumpEvent
}
