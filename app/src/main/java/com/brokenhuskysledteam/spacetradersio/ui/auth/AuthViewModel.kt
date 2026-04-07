package com.brokenhuskysledteam.spacetradersio.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.navigation.NavigationTarget
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersApiException
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersError
import com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManager
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

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val registerAgentUseCase: RegisterAgentUseCase,
    private val sessionManager: SessionManager
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
            is AuthEvent.AccountTokenChanged -> _uiState.update { it.copy(accountToken = event.value) }
            is AuthEvent.AgentTokenChanged -> _uiState.update { it.copy(agentToken = event.value) }
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
        if (state.accountToken.isBlank()) {
            _uiState.update { it.copy(error = "Account token cannot be empty") }
            return
        }
        _uiState.update { it.copy(isRegistering = true, error = null) }
        viewModelScope.launch {
            try {
                registerAgentUseCase(state.callsign.trim(), state.selectedFaction, state.accountToken.trim())
                _uiState.update { it.copy(isRegistering = false) }
                _navigationEvent.send(NavigationTarget.Dashboard)
            } catch (e: SpaceTradersApiException) {
                val errorMessage = when (e.error) {
                    is SpaceTradersError.AuthError.RegisterAgentConflictSymbol -> "Callsign already taken"
                    is SpaceTradersError.AuthError.RegisterAgentSymbolReserved -> "Callsign is reserved"
                    else -> e.message ?: "Registration failed"
                }
                _uiState.update { it.copy(isRegistering = false, error = errorMessage) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRegistering = false, error = e.message ?: "Registration failed") }
            }
        }
    }

    private fun importToken() {
        val state = _uiState.value
        if (state.agentToken.isBlank()) {
            _uiState.update { it.copy(error = "Token cannot be empty") }
            return
        }
        _uiState.update { it.copy(isImporting = true, error = null) }
        sessionManager.login(state.agentToken.trim())
        _uiState.update { it.copy(isImporting = false) }
        viewModelScope.launch {
            _navigationEvent.send(NavigationTarget.Dashboard)
        }
    }
}
