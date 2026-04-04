package com.brokenhuskysledteam.spacetradersio.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.navigation.NavigationTarget
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AgentsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersApiException
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersError
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Loads the authenticated agent's info on init and displays it.
//
// Handles auth failures (401/403) by clearing the stored token and navigating
// back to auth — this is the validation path for imported tokens that turn out
// to be invalid or expired. Other errors are shown inline with a retry option.
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val agentsApi: AgentsApi,
    private val tokenRepository: TokenRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _navigationEvent = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    init {
        loadAgent()
    }

    fun onEvent(event: DashboardEvent) {
        when (event) {
            is DashboardEvent.RetryClicked -> loadAgent()
            is DashboardEvent.LogoutClicked -> logout()
            is DashboardEvent.ErrorDismissed -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun loadAgent() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val agent = agentsApi.getMyAgent().toDomain()
                _uiState.update { it.copy(agent = agent, isLoading = false) }
            } catch (e: SpaceTradersApiException) {
                when (e.error) {
                    is SpaceTradersError.AuthError -> {
                        tokenRepository.clearToken()
                        _navigationEvent.send(NavigationTarget.Auth)
                    }
                    else -> _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load agent") }
            }
        }
    }

    private fun logout() {
        tokenRepository.clearToken()
        viewModelScope.launch {
            _navigationEvent.send(NavigationTarget.Auth)
        }
    }
}
