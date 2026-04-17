package com.brokenhuskysledteam.spacetradersio.ui.auth

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.FactionSymbol

/**
 * Immutable snapshot of everything the Auth screen needs to render itself.
 *
 * **Pattern:** Single-state-object UDF model. Instead of separate [kotlinx.coroutines.flow.StateFlow]
 * fields for each piece of UI state, all state lives in one `data class`. The ViewModel emits a
 * new instance of this class via [kotlinx.coroutines.flow.MutableStateFlow.update] on every
 * change. Composables observe the single flow and recompose only the parts that changed. To apply
 * this in a new project: model one `data class` per screen with sensible defaults, use `copy()`
 * for incremental updates, and keep the class in the same file as the screen's event type.
 *
 * **In this project:** [AuthUiState] is the complete render description for [AuthScreen]. The ViewModel
 * never exposes mutable state directly — the Compose layer can only read this snapshot and send
 * [AuthEvent]s to request changes.
 *
 * @property selectedTab Which of the two auth modes is currently visible.
 * @property callsign The agent name entered by the user on the New Agent tab.
 * @property selectedFaction The starting faction chosen by the user; defaults to [FactionSymbol.COSMIC].
 * @property accountToken The account-level JWT entered for registration. This token is required
 *   by the SpaceTraders API to authorize new agent creation and is never persisted to disk.
 * @property isRegistering `true` while the registration API call is in flight; drives the button
 *   loading indicator and disabled state.
 * @property agentToken The agent-level Bearer token entered on the Import Token tab to resume an
 *   existing agent session.
 * @property isImporting `true` while the import operation is in progress; drives the button
 *   loading indicator and disabled state.
 * @property error A human-readable error string to display in the Snackbar, or `null` when there
 *   is no active error. Set to `null` by [AuthEvent.ErrorDismissed] and on tab change.
 */
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

/**
 * The two authentication modes available on the Auth screen.
 *
 * **In this project:** Tab selection is part of [AuthUiState] so that switching tabs can also
 * clear any active error in the same atomic `copy()` call inside the ViewModel.
 */
enum class AuthTab {
    /** Create a brand-new SpaceTraders agent via the registration API. */
    NEW_AGENT,
    /** Resume an existing agent session by pasting a previously-obtained Bearer token. */
    IMPORT_TOKEN
}

/**
 * All interactions the Auth screen can send to [AuthViewModel].
 *
 * **Pattern:** Sealed-interface event type for UDF. Model every user action as a distinct subtype
 * rather than as individual ViewModel methods. A single `onEvent(AuthEvent)` entry point in the
 * ViewModel then `when`-dispatches to the correct handler. Benefits: (1) the compiler enforces
 * exhaustiveness on the `when` expression, so new events cannot be silently ignored; (2) the
 * Composable only needs a single `(AuthEvent) -> Unit` lambda reference, making it trivially
 * testable and previewable; (3) the full event vocabulary is visible in one place. To apply this
 * in a new project: define a `sealed interface` per screen, one subtype per user action, and keep
 * it alongside the state class.
 *
 * **In this project:** All text-field changes, tab switches, button clicks, and error dismissals
 * flow through this sealed hierarchy into [AuthViewModel.onEvent].
 */
sealed interface AuthEvent {
    /** Fired when the user taps the NEW AGENT or IMPORT TOKEN tab button. */
    data class TabSelected(val tab: AuthTab) : AuthEvent

    /** Fired on every keystroke in the Callsign field. */
    data class CallsignChanged(val value: String) : AuthEvent

    /** Fired when the user picks a faction from the dropdown. */
    data class FactionSelected(val faction: FactionSymbol) : AuthEvent

    /** Fired on every keystroke in the Account Token field on the New Agent tab. */
    data class AccountTokenChanged(val value: String) : AuthEvent

    /** Fired on every keystroke in the Bearer Token field on the Import Token tab. */
    data class AgentTokenChanged(val value: String) : AuthEvent

    /** Fired when the user taps REGISTER on the New Agent tab. */
    data object RegisterClicked : AuthEvent

    /** Fired when the user taps CONNECT on the Import Token tab. */
    data object ImportClicked : AuthEvent

    /** Fired when the user taps Dismiss on the error Snackbar. */
    data object ErrorDismissed : AuthEvent
}
