package com.brokenhuskysledteam.spacetradersio.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.navigation.NavigationTarget
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RegisterAgentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Drives the auth screen's two flows: new agent registration and token import.
//
// Registration delegates to RegisterAgentUseCase which calls the API and persists
// the token. Token import uses a "store-and-go" approach — the token is saved
// immediately and the dashboard's first API call validates it. If that call
// returns 401/403, DashboardViewModel clears the token and sends the user back.
//
// Navigation signals are sent via a Channel (not SharedFlow) so each event is
// consumed exactly once — no re-delivery on configuration changes.
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val registerAgentUseCase: RegisterAgentUseCase,
    private val tokenRepository: TokenRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _navigationEvent = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    fun onEvent(event: AuthEvent) {
        when (event) {
            is AuthEvent.TabSelected -> _uiState.update { it.copy(selectedTab = event.tab, error = null) }
            is AuthEvent.CallsignChanged -> _uiState.update { it.copy(callsign = event.value) }
            is AuthEvent.FactionSelected -> _uiState.update { it.copy(selectedFaction = event.faction) }
            is AuthEvent.TokenChanged -> _uiState.update { it.copy(token = event.value) }
            is AuthEvent.RegisterClicked -> register()
            is AuthEvent.ImportClicked -> importToken()
            is AuthEvent.ErrorDismissed -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun register() {
        val state = _uiState.value
        if (state.callsign.isBlank()) {
            _uiState.update { it.copy(error = "Callsign cannot be empty") }
            return
        }
        _uiState.update { it.copy(isRegistering = true, error = null) }
        viewModelScope.launch {
            try {
                registerAgentUseCase(state.callsign.trim(), state.selectedFaction)
                _uiState.update { it.copy(isRegistering = false) }
                _navigationEvent.send(NavigationTarget.Dashboard)
            } catch (e: Exception) {
                _uiState.update { it.copy(isRegistering = false, error = e.message ?: "Registration failed") }
            }
        }
    }

    private fun importToken() {
        val state = _uiState.value
        if (state.token.isBlank()) {
            _uiState.update { it.copy(error = "Token cannot be empty") }
            return
        }
        _uiState.update { it.copy(isImporting = true, error = null) }
        tokenRepository.saveToken(state.token.trim())
        _uiState.update { it.copy(isImporting = false) }
        viewModelScope.launch {
            _navigationEvent.send(NavigationTarget.Dashboard)
        }
    }
}
