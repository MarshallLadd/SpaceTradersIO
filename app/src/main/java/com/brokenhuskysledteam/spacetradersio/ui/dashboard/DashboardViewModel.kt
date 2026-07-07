package com.brokenhuskysledteam.spacetradersio.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.navigation.NavigationTarget
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersApiException
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersError
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.AgentRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManager
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

/**
 * ViewModel for the dashboard screen. Owns the loading/error state and merges it with the
 * repository's live agent stream into a single [DashboardUiState] snapshot.
 *
 * **Pattern:** `combine()` state merging. In a new project, use this approach whenever the
 * final UI state is the product of multiple independent sources: a hot repository stream
 * (e.g. DB-backed `StateFlow`) plus one or more locally-owned mutable flags (loading, error).
 * Keep the repository flow and the local flags separate — do not fold the loading flag into
 * the repository layer. The `combine` lambda assembles a fresh snapshot any time any
 * source emits.
 *
 * **In this project:** [AgentRepository.observeAgent] is a hot `StateFlow` backed by
 * SQLDelight. When [loadAgent] calls [AgentRepository.refreshAgent], the API result is
 * written to the database, which causes the `StateFlow` to emit the updated value
 * automatically. The ViewModel never manually pushes agent data into `uiState`; it only
 * controls `_isLoading` and `_error`.
 *
 * @param agentRepository Provides the live agent stream and the refresh trigger.
 * @param sessionManager Handles token clearing on logout or auth-error eviction.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val agentRepository: AgentRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    // Local-only state not owned by any repository.
    // These are separate MutableStateFlows so they can be updated atomically and
    // independently — changing isLoading does not risk clobbering the error value.
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    /**
     * The single source of truth for the dashboard UI. Emits a new [DashboardUiState]
     * snapshot whenever the agent data, loading flag, or error string changes.
     *
     * `SharingStarted.Eagerly` is intentional: `WhileSubscribed` would stop the upstream
     * `combine` whenever there are no active collectors (e.g. during a configuration
     * change gap). More importantly, `WhileSubscribed` causes test failures with
     * `StandardTestDispatcher` because the upstream is never collected and `.value` stays
     * at the initial default — see the CLAUDE.md gotcha on this topic.
     */
    val uiState: StateFlow<DashboardUiState> = combine(
        agentRepository.observeAgent(),
        _isLoading,
        _error
    ) { agent, isLoading, error ->
        DashboardUiState(agent = agent, isLoading = isLoading, error = error)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DashboardUiState())

    /**
     * One-shot navigation commands sent to the UI. Using a [Channel] (rather than
     * `SharedFlow`) guarantees exactly-once delivery and prevents re-navigation on
     * configuration changes, because unconsumed items stay buffered in the channel rather
     * than being replayed to new collectors.
     */
    private val _navigationEvent = Channel<NavigationTarget>(Channel.BUFFERED)

    /** Exposed as a `Flow` so the UI cannot send events back through this channel. */
    val navigationEvent = _navigationEvent.receiveAsFlow()

    init {
        // Kick off the initial data load as soon as the ViewModel is created.
        // The default [DashboardUiState] has isLoading=true, so the UI shows a spinner
        // immediately while this coroutine runs.
        loadAgent()
    }

    /**
     * Single entry point for all UI interactions.
     *
     * **Pattern:** Unified event handler. Route every [DashboardEvent] variant here rather
     * than exposing multiple public functions. This keeps the ViewModel's public API minimal
     * and makes it easy to add cross-cutting logic (e.g. analytics, debouncing) in one place.
     *
     * @param event The interaction dispatched from the composable.
     */
    fun onEvent(event: DashboardEvent) {
        when (event) {
            is DashboardEvent.RetryClicked -> loadAgent()
            is DashboardEvent.LogoutClicked -> logout()
            // Clearing the error emits a new combine snapshot with error=null,
            // collapsing the error card in the UI.
            is DashboardEvent.ErrorDismissed -> _error.value = null
            is DashboardEvent.FleetCardClicked -> viewModelScope.launch {
                _navigationEvent.send(NavigationTarget.ShipList)
            }
            is DashboardEvent.ContractsCardClicked -> viewModelScope.launch {
                _navigationEvent.send(NavigationTarget.ContractsScreen)
            }
        }
    }

    /**
     * Triggers an API refresh of the agent data and manages the loading/error state
     * around that call.
     *
     * The repository's [AgentRepository.observeAgent] `StateFlow` is DB-backed: when
     * [AgentRepository.refreshAgent] completes successfully, it writes the API response
     * to SQLDelight, which automatically emits the new value through the `StateFlow`.
     * This ViewModel only needs to flip `_isLoading` — no manual state push is needed.
     *
     * **Auth errors:** A [SpaceTradersError.AuthError] means the stored token is invalid
     * or expired. In that case, the session is cleared immediately and the user is
     * redirected to the Auth screen rather than shown a retryable error card.
     */
    private fun loadAgent() {
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                agentRepository.refreshAgent()
                _isLoading.value = false
            } catch (e: SpaceTradersApiException) {
                when (e.error) {
                    is SpaceTradersError.AuthError -> {
                        // Token is invalid — clear the session and evict the user rather
                        // than presenting a retry option that would fail again.
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

    /**
     * Clears the stored session token and navigates to the Auth screen.
     *
     * `sessionManager.logout()` is called synchronously before launching the navigation
     * coroutine so the token is wiped even if the coroutine is cancelled (e.g. if the
     * ViewModel is cleared before the channel send completes).
     */
    private fun logout() {
        sessionManager.logout()
        viewModelScope.launch {
            _navigationEvent.send(NavigationTarget.Auth)
        }
    }
}
