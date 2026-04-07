package com.brokenhuskysledteam.spacetradersio.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.navigation.NavigationTarget
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AgentsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersApiException
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersError
import com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManager
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.AgentStateStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val agentsApi: AgentsApi,
    private val agentStateStore: AgentStateStore,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DashboardUiState> = combine(
        agentStateStore.agent,
        _isLoading,
        _error
    ) { agent, isLoading, error ->
        DashboardUiState(agent = agent, isLoading = isLoading, error = error)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DashboardUiState())

    private val _navigationEvent = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    init {
        loadAgent()
    }

    fun onEvent(event: DashboardEvent) {
        when (event) {
            is DashboardEvent.RetryClicked -> loadAgent()
            is DashboardEvent.LogoutClicked -> logout()
            is DashboardEvent.ErrorDismissed -> _error.value = null
            is DashboardEvent.FleetCardClicked -> viewModelScope.launch {
                _navigationEvent.send(NavigationTarget.ShipList)
            }
        }
    }

    private fun loadAgent() {
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val agent = agentsApi.getMyAgent().toDomain()
                agentStateStore.update(agent)
                _isLoading.value = false
            } catch (e: SpaceTradersApiException) {
                when (e.error) {
                    is SpaceTradersError.AuthError -> {
                        sessionManager.logout()
                        _navigationEvent.send(NavigationTarget.Auth)
                    }
                    else -> {
                        _isLoading.value = false
                        _error.value = e.message
                    }
                }
            } catch (e: Exception) {
                _isLoading.value = false
                _error.value = e.message ?: "Failed to load agent"
            }
        }
    }

    private fun logout() {
        sessionManager.logout()
        viewModelScope.launch {
            _navigationEvent.send(NavigationTarget.Auth)
        }
    }
}
