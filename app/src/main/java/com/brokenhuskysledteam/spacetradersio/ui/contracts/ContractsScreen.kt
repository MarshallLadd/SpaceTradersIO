package com.brokenhuskysledteam.spacetradersio.ui.contracts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractTab
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalButton
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalCard

/**
 * Stateful entry point for the Contracts screen. Owns the [ContractsViewModel] and
 * collects [ContractsUiState] as lifecycle-aware state.
 *
 * **Pattern:** Stateful / stateless composable split. This function is the only composable
 * that references the ViewModel directly. It immediately delegates all rendering to
 * [ContractsScreenContent], which is a pure function of its parameters and can be used in
 * Compose Previews without a ViewModel. To apply this pattern in a new project: keep one
 * stateful wrapper that wires the ViewModel, and put all visual logic in a stateless
 * `ScreenContent` function.
 *
 * See `AuthScreen`, `DashboardScreen`, `ShipListScreen`, and `ShipDetailScreen` for the
 * same split applied across every screen in this project.
 */
@Composable
fun ContractsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ContractsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ContractsScreenContent(uiState = uiState, onEvent = viewModel::onEvent, onNavigateBack = onNavigateBack)
}

/**
 * Stateless Contracts screen. Renders entirely from [uiState] and emits user interactions
 * as [ContractsEvent] values via [onEvent].
 *
 * **Three-layer dialog system:** The three optional dialogs (accept confirmation, fulfill
 * confirmation, action result) are rendered *outside* the main `Column` so they float above
 * all content as overlays. Each is controlled by a nullable field in [ContractsUiState]:
 * - [ContractsUiState.pendingAccept] — non-null while the accept dialog is open.
 * - [ContractsUiState.pendingFulfill] — non-null while the fulfill dialog is open.
 * - [ContractsUiState.actionResult] — non-null while the one-shot success dialog is open.
 * All three are cleared by emitting the corresponding dismiss event, which sets the field
 * back to `null` in the ViewModel's `LocalState`.
 */
@Composable
fun ContractsScreenContent(
    uiState: ContractsUiState,
    onEvent: (ContractsEvent) -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TerminalButton(text = "< BACK", onClick = onNavigateBack)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "CONTRACTS",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val tabs = listOf(ContractTab.ACTIVE to "ACTIVE", ContractTab.HISTORY to "HISTORY")
        PrimaryTabRow(selectedTabIndex = tabs.indexOfFirst { it.first == uiState.selectedTab }) {
            tabs.forEach { (tab, label) ->
                Tab(
                    selected = uiState.selectedTab == tab,
                    onClick = { onEvent(ContractsEvent.TabSelected(tab)) },
                    text = {
                        Text(
                            label,
                            color = if (uiState.selectedTab == tab)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        PaginationControls(uiState = uiState, onEvent = onEvent)

        Spacer(modifier = Modifier.height(8.dp))

        when {
            uiState.isLoading && uiState.contracts.isEmpty() -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            uiState.error != null && uiState.contracts.isEmpty() -> {
                TerminalCard(title = "ERROR") {
                    Text(uiState.error, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    TerminalButton(text = "RETRY", onClick = { onEvent(ContractsEvent.RetryClicked) })
                }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(uiState.contracts, key = { it.id }) { contract ->
                        ContractItem(contract = contract, onEvent = onEvent)
                    }
                }
            }
        }
    }

    if (uiState.pendingAccept != null) {
        AcceptConfirmationDialog(
            contract = uiState.pendingAccept,
            onConfirm = { onEvent(ContractsEvent.AcceptConfirmed) },
            onDismiss = { onEvent(ContractsEvent.AcceptDismissed) }
        )
    }

    if (uiState.pendingFulfill != null) {
        FulfillConfirmationDialog(
            contract = uiState.pendingFulfill,
            onConfirm = { onEvent(ContractsEvent.FulfillConfirmed) },
            onDismiss = { onEvent(ContractsEvent.FulfillDismissed) }
        )
    }

    if (uiState.actionResult != null) {
        ActionResultDialog(
            result = uiState.actionResult,
            onDismiss = { onEvent(ContractsEvent.ActionResultDismissed) }
        )
    }
}

/**
 * Prev/Next buttons and a page-size dropdown for navigating the paginated contract list.
 *
 * The PREV and NEXT buttons emit [ContractsEvent.PageChanged] and are enabled only when
 * [ContractsUiState.canGoPrevPage] / [ContractsUiState.canGoNextPage] are `true`.
 * The `ExposedDropdownMenuBox` emits [ContractsEvent.LimitChanged] when the user selects
 * a different page size, which also resets the current page to 1 in the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaginationControls(
    uiState: ContractsUiState,
    onEvent: (ContractsEvent) -> Unit
) {
    val pageSizes = listOf(5, 10, 15, 20)
    var expanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TerminalButton(
            text = "< PREV",
            onClick = { onEvent(ContractsEvent.PageChanged(uiState.currentPage - 1)) },
            enabled = uiState.canGoPrevPage
        )
        Text(
            text = "PG ${uiState.currentPage}/${uiState.totalPages}",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall
        )
        TerminalButton(
            text = "NEXT >",
            onClick = { onEvent(ContractsEvent.PageChanged(uiState.currentPage + 1)) },
            enabled = uiState.canGoNextPage
        )
        Spacer(modifier = Modifier.weight(1f))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = "${uiState.limit}/PG",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).width(110.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    focusedTextColor = MaterialTheme.colorScheme.primary,
                    unfocusedTextColor = MaterialTheme.colorScheme.primary
                )
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                pageSizes.forEach { size ->
                    DropdownMenuItem(
                        text = { Text("$size / PAGE", color = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            onEvent(ContractsEvent.LimitChanged(size))
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Renders a single contract as a terminal-styled card with status, faction, payment terms,
 * delivery progress rows, and a context-sensitive action button.
 *
 * **Action button logic:**
 * - `UNACCEPTED` contracts show an ACCEPT button (opens the confirmation dialog).
 * - `ACTIVE` contracts show a FULFILL button *only* when all deliver-goods have
 *   `unitsFulfilled >= unitsRequired`. The `allDelivered` check prevents premature fulfillment.
 * - All other statuses (`FULFILLED`, `EXPIRED`, `CANCELLED`) show no button.
 */
@Composable
private fun ContractItem(contract: Contract, onEvent: (ContractsEvent) -> Unit) {
    val allDelivered = contract.terms.deliverGoods.isNotEmpty() &&
        contract.terms.deliverGoods.all { it.unitsFulfilled >= it.unitsRequired }

    TerminalCard(title = "[${contract.type.name}] ${contract.id}") {
        ContractDataRow("STATUS", contract.status.name)
        ContractDataRow("FACTION", contract.factionSymbol)
        ContractDataRow("UPFRONT", "${contract.terms.paymentOnAccepted} CR")
        ContractDataRow("REWARD", "${contract.terms.paymentOnFulfilled} CR")

        contract.terms.deliverGoods.forEach { good ->
            ContractDataRow(
                label = good.tradeSymbol,
                value = "${good.unitsFulfilled}/${good.unitsRequired} @ ${good.destinationSymbol}"
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (contract.status) {
            ContractStatus.UNACCEPTED -> TerminalButton(
                text = "ACCEPT",
                onClick = { onEvent(ContractsEvent.AcceptClicked(contract)) },
                modifier = Modifier.fillMaxWidth()
            )
            ContractStatus.ACTIVE -> if (allDelivered) {
                TerminalButton(
                    text = "FULFILL",
                    onClick = { onEvent(ContractsEvent.FulfillClicked(contract)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            else -> Unit
        }
    }
}

@Composable
private fun ContractDataRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label:",
            modifier = Modifier.width(100.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun AcceptConfirmationDialog(
    contract: Contract,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ACCEPT CONTRACT?", color = MaterialTheme.colorScheme.primary) },
        text = {
            Column {
                Text("Upfront payment: ${contract.terms.paymentOnAccepted} CR",
                    color = MaterialTheme.colorScheme.primary)
                Text("Deadline: ${contract.terms.deadline}",
                    color = MaterialTheme.colorScheme.primary)
                Text("Reward: ${contract.terms.paymentOnFulfilled} CR",
                    color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = { TerminalButton(text = "ACCEPT", onClick = onConfirm) },
        dismissButton = { TerminalButton(text = "CANCEL", onClick = onDismiss) }
    )
}

@Composable
private fun FulfillConfirmationDialog(
    contract: Contract,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("FULFILL CONTRACT?", color = MaterialTheme.colorScheme.primary) },
        text = {
            Text("Claim reward: ${contract.terms.paymentOnFulfilled} CR",
                color = MaterialTheme.colorScheme.primary)
        },
        confirmButton = { TerminalButton(text = "FULFILL", onClick = onConfirm) },
        dismissButton = { TerminalButton(text = "CANCEL", onClick = onDismiss) }
    )
}

@Composable
private fun ActionResultDialog(
    result: ContractActionResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SUCCESS", color = MaterialTheme.colorScheme.tertiary) },
        text = {
            when (result) {
                is ContractActionResult.Accepted ->
                    Text("Contract accepted. +${result.upfrontPayment} CR deposited.",
                        color = MaterialTheme.colorScheme.primary)
                is ContractActionResult.Fulfilled ->
                    Text("Contract fulfilled. +${result.reward} CR deposited.",
                        color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = { TerminalButton(text = "OK", onClick = onDismiss) }
    )
}
