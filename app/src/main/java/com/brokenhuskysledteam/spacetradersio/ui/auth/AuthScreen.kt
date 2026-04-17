package com.brokenhuskysledteam.spacetradersio.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brokenhuskysledteam.spacetradersio.navigation.NavigationTarget
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.FactionSymbol
import com.brokenhuskysledteam.spacetradersio.ui.components.ScanlineOverlay
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalButton
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalCard
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalTextField
import com.brokenhuskysledteam.spacetradersio.ui.theme.TerminalDarkGray

/**
 * Stateful entry-point composable for the Auth screen.
 *
 * **Pattern:** Stateful/stateless composable split. This composable is the *stateful* half: it
 * owns ViewModel acquisition, state collection, and side-effect handling. It immediately delegates
 * all rendering to [AuthScreenContent], which is the *stateless* half. To apply this in a new
 * project: keep one thin stateful wrapper per screen that handles ViewModel wiring, and push all
 * layout and rendering into a stateless sibling that accepts only plain data and lambda callbacks.
 *
 * **In this project:** [AuthScreen] is the composable registered in the navigation graph. It is
 * the only place in the Auth screen that knows about [AuthViewModel] — [AuthScreenContent] and
 * all its children are completely decoupled from the ViewModel, making them trivially testable
 * and previewable.
 *
 * @param onNavigateToDashboard Callback invoked when authentication succeeds; the NavHost uses
 *   this to pop the auth destination and push the dashboard.
 * @param viewModel Hilt-provided [AuthViewModel]; defaulted via [hiltViewModel] so callers
 *   don't need to pass it explicitly.
 */
@Composable
fun AuthScreen(
    onNavigateToDashboard: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    // collectAsStateWithLifecycle stops collection when the lifecycle drops below STARTED
    // (e.g. the screen is in the back stack). This prevents unnecessary recompositions and
    // avoids processing UI updates while the composable is not visible.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // LaunchedEffect(Unit) launches a coroutine that lives as long as this composable is in the
    // composition. The Unit key means it is never restarted. The coroutine collects the Channel
    // as a Flow — each emitted NavigationTarget triggers exactly one navigation action.
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { target ->
            when (target) {
                NavigationTarget.Dashboard -> onNavigateToDashboard()
                // Other navigation targets are not valid from the Auth screen; ignore them.
                else -> {}
            }
        }
    }

    AuthScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent
    )
}

/**
 * Stateless content composable for the Auth screen.
 *
 * **Pattern:** Stateless composable. This composable receives an immutable [AuthUiState] snapshot
 * and a single `(AuthEvent) -> Unit` lambda. It owns no state of its own (beyond local UI
 * mechanics like dropdown expanded state) and issues no side effects. This makes it a pure
 * function of its inputs: given the same [uiState], it always renders the same UI. To apply this
 * in a new project: every screen's primary content composable should follow this shape —
 * `content(uiState: MyUiState, onEvent: (MyEvent) -> Unit)`.
 *
 * **In this project:** Styled as a retro system login terminal with a [ScanlineOverlay] for the
 * green-on-black aesthetic. The two auth modes ([NewAgentTab] and [ImportTokenTab]) are swapped
 * based on [AuthUiState.selectedTab].
 *
 * @param uiState The current render snapshot from [AuthViewModel].
 * @param onEvent Callback that routes user interactions back to [AuthViewModel.onEvent].
 */
@Composable
fun AuthScreenContent(
    uiState: AuthUiState,
    onEvent: (AuthEvent) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                // verticalScroll allows the form to remain usable on small screens or when the
                // soft keyboard pushes content upward.
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "SPACETRADERS TERMINAL",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "// SYSTEM ACCESS v2.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Tab selector — two TerminalButtons acting as a toggle. The active tab is indicated
            // by primary colour text; inactive tabs use the outline (dimmed) colour. Tab state
            // lives in AuthUiState, not in local remember{}, so it survives configuration changes.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TerminalButton(
                    onClick = { onEvent(AuthEvent.TabSelected(AuthTab.NEW_AGENT)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "NEW AGENT",
                        color = if (uiState.selectedTab == AuthTab.NEW_AGENT)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    )
                }
                TerminalButton(
                    onClick = { onEvent(AuthEvent.TabSelected(AuthTab.IMPORT_TOKEN)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "IMPORT TOKEN",
                        color = if (uiState.selectedTab == AuthTab.IMPORT_TOKEN)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Swap tab content based on state. The when is exhaustive over the AuthTab enum so
            // the compiler will flag any future tab values that are not handled here.
            when (uiState.selectedTab) {
                AuthTab.NEW_AGENT -> NewAgentTab(uiState = uiState, onEvent = onEvent)
                AuthTab.IMPORT_TOKEN -> ImportTokenTab(uiState = uiState, onEvent = onEvent)
            }

            // Snackbar is used for error display because errors are transient and non-blocking:
            // the user can correct their input and retry without dismissing a dialog. Inline
            // error text was considered but Snackbar better matches the terminal aesthetic and
            // keeps the form layout stable (no layout shift when an error appears/disappears).
            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Snackbar(
                    containerColor = TerminalDarkGray,
                    contentColor = MaterialTheme.colorScheme.error,
                    action = {
                        TerminalButton(
                            text = "Dismiss",
                            onClick = { onEvent(AuthEvent.ErrorDismissed) }
                        )
                    }
                ) {
                    Text(uiState.error)
                }
            }
        }

        // ScanlineOverlay draws translucent horizontal lines over the entire screen to simulate
        // a CRT monitor effect. It is placed last so it renders on top of all content.
        ScanlineOverlay()
    }
}

/**
 * Registration form tab content, wrapped in a [TerminalCard].
 *
 * Renders the callsign field, account token field, faction dropdown, and register button. All
 * user interactions are forwarded through [onEvent] — this composable holds no business state.
 *
 * The faction dropdown's expanded/collapsed state is intentionally held in local `remember` (not
 * in [AuthUiState]) because it is pure UI mechanics with no business significance — it does not
 * need to survive configuration changes or be observable by the ViewModel.
 *
 * @param uiState Current auth screen state snapshot.
 * @param onEvent Callback to forward user interactions to the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewAgentTab(uiState: AuthUiState, onEvent: (AuthEvent) -> Unit) {
    // Local state for the dropdown's open/closed visual state only. This is an example of state
    // that is correct to keep local: it has no effect on anything outside this composable.
    var factionExpanded by remember { mutableStateOf(false) }

    TerminalCard(title = "Register Agent") {
        TerminalTextField(
            value = uiState.callsign,
            onValueChange = { onEvent(AuthEvent.CallsignChanged(it)) },
            label = "Callsign",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        TerminalTextField(
            value = uiState.accountToken,
            onValueChange = { onEvent(AuthEvent.AccountTokenChanged(it)) },
            label = "Account Token",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ExposedDropdownMenuBox is the Material 3 component for a select/picker control.
        // readOnly = true on the TextField prevents the soft keyboard from opening; the user
        // interacts with the dropdown exclusively via tap.
        ExposedDropdownMenuBox(
            expanded = factionExpanded,
            onExpandedChange = { factionExpanded = it }
        ) {
            TerminalTextField(
                value = uiState.selectedFaction.name,
                onValueChange = {},
                readOnly = true,
                label = "Faction",
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = factionExpanded) },
                // menuAnchor(PrimaryNotEditable) is required by Material 3's ExposedDropdownMenu
                // API to correctly associate this TextField as the anchor for the dropdown menu.
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = factionExpanded,
                onDismissRequest = { factionExpanded = false }
            ) {
                FactionSymbol.entries.forEach { faction ->
                    DropdownMenuItem(
                        text = { Text(faction.name) },
                        onClick = {
                            onEvent(AuthEvent.FactionSelected(faction))
                            factionExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // The button is disabled while isRegistering is true to prevent duplicate submissions.
        // The content switches between a progress indicator and the label text based on the same
        // flag, giving the user clear visual feedback that the request is in flight.
        TerminalButton(
            onClick = { onEvent(AuthEvent.RegisterClicked) },
            enabled = !uiState.isRegistering,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isRegistering) {
                CircularProgressIndicator()
            } else {
                Text("REGISTER")
            }
        }
    }
}

/**
 * Token import tab content, wrapped in a [TerminalCard].
 *
 * Renders a single Bearer Token field and a connect button. Used by players who already have an
 * agent token from a previous session or from the SpaceTraders web UI.
 *
 * @param uiState Current auth screen state snapshot.
 * @param onEvent Callback to forward user interactions to the ViewModel.
 */
@Composable
private fun ImportTokenTab(uiState: AuthUiState, onEvent: (AuthEvent) -> Unit) {
    TerminalCard(title = "Import Token") {
        TerminalTextField(
            value = uiState.agentToken,
            onValueChange = { onEvent(AuthEvent.AgentTokenChanged(it)) },
            label = "Bearer Token",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Mirrors the register button's loading pattern: disable + show progress while in flight.
        TerminalButton(
            onClick = { onEvent(AuthEvent.ImportClicked) },
            enabled = !uiState.isImporting,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isImporting) {
                CircularProgressIndicator()
            } else {
                Text("CONNECT")
            }
        }
    }
}
