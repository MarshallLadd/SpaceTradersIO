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

/**
 * ViewModel for the Auth screen. Holds UI state, validates user input, and orchestrates
 * registration and token-import flows.
 *
 * **Pattern:** [@HiltViewModel][HiltViewModel] UDF ViewModel. Annotate with `@HiltViewModel` and
 * inject dependencies via `@Inject constructor` — Hilt generates a factory automatically, so
 * `hiltViewModel()` in the Composable resolves the correct instance without any manual factory.
 * Back the public [StateFlow] with a private [MutableStateFlow] and expose it via
 * [MutableStateFlow.asStateFlow] so the UI layer cannot mutate state directly. Emit one-shot
 * navigation commands through a [Channel] (not [kotlinx.coroutines.flow.SharedFlow]) to prevent
 * re-delivery on configuration change. To apply this in a new project: follow this same structure
 * for every screen ViewModel.
 *
 * **In this project:** [AuthViewModel] is the single source of truth for the Auth screen. It
 * accepts all user interactions via the [onEvent] method, updates [uiState] accordingly, and
 * sends navigation commands to [navigationEvent] on successful authentication.
 *
 * @param registerAgentUseCase Use case that calls the SpaceTraders registration endpoint and
 *   initialises the SDK session with the returned agent token.
 * @param sessionManager SDK session coordinator used to authenticate an existing token on import.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val registerAgentUseCase: RegisterAgentUseCase,
    private val sessionManager: SessionManager
) : ViewModel() {

    /**
     * Backing mutable state, private to this ViewModel.
     * All mutations go through [MutableStateFlow.update] for thread-safe atomic reads.
     */
    private val _uiState = MutableStateFlow(AuthUiState())

    /**
     * Read-only view of the UI state observed by the Compose layer.
     * Exposed as [StateFlow] so collectors always get the latest value immediately on subscription.
     */
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /**
     * One-shot navigation commands sent after a successful auth action.
     *
     * A [Channel] is used instead of [kotlinx.coroutines.flow.SharedFlow] because [Channel]
     * delivers each element exactly once. If the user rotates the screen while a navigation
     * command is in-flight, [kotlinx.coroutines.flow.SharedFlow] could re-deliver it and trigger
     * a duplicate navigation. [Channel.BUFFERED] ensures the send call does not suspend even if
     * the collector is momentarily inactive.
     */
    private val _navigationEvent = Channel<NavigationTarget>(Channel.BUFFERED)

    /**
     * Flow of one-shot navigation commands collected by [AuthScreen] inside a [LaunchedEffect].
     * Exposed as a plain [kotlinx.coroutines.flow.Flow] to hide the send-side of the channel.
     */
    val navigationEvent = _navigationEvent.receiveAsFlow()

    /**
     * Single entry point for all user interactions on the Auth screen.
     *
     * **Pattern:** Single-event-dispatch method. All [AuthEvent] subtypes are handled here via an
     * exhaustive `when`. Simple field updates are inlined as `_uiState.update { it.copy(...) }`;
     * async operations delegate to private suspend-launching functions. This keeps the dispatch
     * table readable and prevents the Composable from calling ViewModel methods directly.
     *
     * @param event The user interaction to process.
     */
    fun onEvent(event: AuthEvent) {
        when (event) {
            // Clear any active error when the user switches tabs so stale messages don't persist.
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

    /**
     * Validates registration inputs and calls the SpaceTraders registration API.
     *
     * **Pattern:** viewModelScope.launch + try/catch for async actions with loading state.
     * Set a loading flag before the suspend call, clear it in both the success and error paths
     * so the UI never gets stuck in a loading state. Catch [SpaceTradersApiException] first to
     * map known API error codes to user-friendly strings, then catch the base [Exception] as a
     * fallback for network or unexpected errors.
     *
     * Validation is intentionally synchronous (no coroutine needed) so the error appears
     * immediately without a frame delay.
     */
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
        // Optimistically set loading state before launching the coroutine so the button
        // disables and the progress indicator appears on the very next recomposition.
        _uiState.update { it.copy(isRegistering = true, error = null) }
        viewModelScope.launch {
            try {
                // trim() strips accidental leading/trailing whitespace that is common when
                // pasting tokens or typing callsigns on a mobile keyboard.
                registerAgentUseCase(state.callsign.trim(), state.selectedFaction, state.accountToken.trim())
                _uiState.update { it.copy(isRegistering = false) }
                // Navigate on success. send() is safe to call from a coroutine without suspend
                // because the channel is BUFFERED — it won't block even if the collector is not
                // yet active.
                _navigationEvent.send(NavigationTarget.Dashboard)
            } catch (e: SpaceTradersApiException) {
                // Map known conflict codes to friendly strings; fall through to the raw message
                // for any other API error code so the user still sees something actionable.
                val errorMessage = when (e.error) {
                    is SpaceTradersError.AuthError.RegisterAgentConflictSymbol -> "Callsign already taken"
                    is SpaceTradersError.AuthError.RegisterAgentSymbolReserved -> "Callsign is reserved"
                    else -> e.message ?: "Registration failed"
                }
                _uiState.update { it.copy(isRegistering = false, error = errorMessage) }
            } catch (e: Exception) {
                // Catch-all for network errors, timeouts, or any other unexpected failure.
                _uiState.update { it.copy(isRegistering = false, error = e.message ?: "Registration failed") }
            }
        }
    }

    /**
     * Validates the pasted token and starts a session without a network call.
     *
     * Token import is intentionally synchronous: [SessionManager.login] stores the token locally
     * and initialises the in-memory session. No API verification occurs at this point — if the
     * token is invalid or expired, the first authenticated API call on the Dashboard will fail
     * and surface the error there. This matches the SpaceTraders API's design: tokens don't
     * expire via a validation endpoint, they simply fail on use.
     *
     * The navigation send is still wrapped in [viewModelScope.launch] because [Channel.send] is
     * a suspend function, even though the rest of this path is synchronous.
     */
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
