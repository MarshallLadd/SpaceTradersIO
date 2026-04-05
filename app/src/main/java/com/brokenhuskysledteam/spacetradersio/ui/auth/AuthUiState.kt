package com.brokenhuskysledteam.spacetradersio.ui.auth

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.FactionSymbol

// Immutable snapshot of the auth screen's UI state.
// The ViewModel emits new instances via StateFlow on every state change.
data class AuthUiState(
    val selectedTab: AuthTab = AuthTab.NEW_AGENT,
    val callsign: String = "",
    val selectedFaction: FactionSymbol = FactionSymbol.COSMIC,
    // Account token: entered by the user for registration only; never persisted.
    val accountToken: String = "",
    val isRegistering: Boolean = false,
    // Agent token: entered by the user on the Import Token tab to resume an existing agent.
    val agentToken: String = "",
    val isImporting: Boolean = false,
    val error: String? = null
)

enum class AuthTab { NEW_AGENT, IMPORT_TOKEN }

// All user interactions on the auth screen, dispatched to AuthViewModel.onEvent().
// Using a sealed interface keeps the ViewModel's event handling exhaustive.
sealed interface AuthEvent {
    data class TabSelected(val tab: AuthTab) : AuthEvent
    data class CallsignChanged(val value: String) : AuthEvent
    data class FactionSelected(val faction: FactionSymbol) : AuthEvent
    data class AccountTokenChanged(val value: String) : AuthEvent
    data class AgentTokenChanged(val value: String) : AuthEvent
    data object RegisterClicked : AuthEvent
    data object ImportClicked : AuthEvent
    data object ErrorDismissed : AuthEvent
}
