package com.brokenhuskysledteam.spacetradersio.ui.auth

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.FactionSymbol

data class AuthUiState(
    val selectedTab: AuthTab = AuthTab.NEW_AGENT,
    val callsign: String = "",
    val selectedFaction: FactionSymbol = FactionSymbol.COSMIC,
    val isRegistering: Boolean = false,
    val token: String = "",
    val isImporting: Boolean = false,
    val error: String? = null
)

enum class AuthTab { NEW_AGENT, IMPORT_TOKEN }

sealed interface AuthEvent {
    data class TabSelected(val tab: AuthTab) : AuthEvent
    data class CallsignChanged(val value: String) : AuthEvent
    data class FactionSelected(val faction: FactionSymbol) : AuthEvent
    data class TokenChanged(val value: String) : AuthEvent
    data object RegisterClicked : AuthEvent
    data object ImportClicked : AuthEvent
    data object ErrorDismissed : AuthEvent
}
