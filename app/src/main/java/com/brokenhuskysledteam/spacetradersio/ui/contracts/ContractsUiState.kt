package com.brokenhuskysledteam.spacetradersio.ui.contracts

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractTab
import kotlin.math.ceil

/**
 * The single source of truth for everything the Contracts screen needs to render.
 *
 * **Pattern:** UDF UI state. One immutable snapshot is emitted by `ContractsViewModel.uiState`
 * each time anything changes. The composable reads from this snapshot and never holds its own
 * state (with the exception of `ExposedDropdownMenuBox` expansion, which is transient
 * presentation-only state). To apply this pattern in a new project: put every piece of state
 * the screen needs — data, loading flags, dialog visibility, error messages — into a single
 * `data class`. Compute derived values (like page counts) as properties rather than storing them
 * separately; that way they can never drift out of sync with their sources.
 *
 * **Dual-source shape:** Some fields originate from the SQLDelight reactive query
 * ([contracts]), others from the ViewModel's internal `LocalState` ([selectedTab],
 * [currentPage], [limit], [isLoading], etc.). Both streams are merged in the ViewModel's
 * `combine()` call. The split is invisible to the composable — it always reads this unified
 * snapshot.
 *
 * @property contracts The page of contracts currently returned by the local DB query for the
 *   active tab/page/limit combination. May be empty during the initial load.
 * @property selectedTab Which tab is active (`ACTIVE` or `HISTORY`). Drives the tab highlight
 *   and the DB query filter.
 * @property currentPage The 1-based page currently displayed.
 * @property limit Number of contracts per page. Controlled by the page-size dropdown.
 * @property serverTotal The total contract count on the server for this agent (returned by the
 *   last `refreshContracts` call). Used to derive [totalPages].
 * @property isLoading `true` while a `refreshContracts` network call is in flight. The screen
 *   shows a spinner instead of the list when both [isLoading] is `true` and [contracts] is empty.
 * @property isActionInProgress `true` while an accept or fulfill API call is in flight.
 *   Disables action buttons to prevent double-submission.
 * @property pendingAccept Non-null when the user has tapped Accept on a contract and the
 *   confirmation dialog should be shown. Cleared when the dialog is dismissed or confirmed.
 * @property pendingFulfill Non-null when the user has tapped Fulfill on a contract and the
 *   confirmation dialog should be shown.
 * @property actionResult Non-null after a successful accept or fulfill action. Shown in a
 *   one-shot success dialog, then cleared via [ContractsEvent.ActionResultDismissed].
 * @property error Non-null when the last network call failed. Shown inline if [contracts] is
 *   empty (full-page error); ignored otherwise so stale data remains visible.
 * @property totalPages Derived from [serverTotal] and [limit]. Defaults to `1` before the
 *   first successful refresh so the pagination controls render without crashing.
 * @property canGoNextPage `true` when there is at least one more page after [currentPage].
 *   Drives the NEXT button's enabled state.
 * @property canGoPrevPage `true` when [currentPage] is greater than 1.
 *   Drives the PREV button's enabled state.
 */
data class ContractsUiState(
    val contracts: List<Contract> = emptyList(),
    val selectedTab: ContractTab = ContractTab.ACTIVE,
    val currentPage: Int = 1,
    val limit: Int = 10,
    val serverTotal: Int = 0,
    val isLoading: Boolean = true,
    val isActionInProgress: Boolean = false,
    val pendingAccept: Contract? = null,
    val pendingFulfill: Contract? = null,
    val actionResult: ContractActionResult? = null,
    val error: String? = null
) {
    val totalPages: Int get() =
        if (serverTotal > 0) ceil(serverTotal.toDouble() / limit).toInt() else 1
    val canGoNextPage: Boolean get() = currentPage < totalPages
    val canGoPrevPage: Boolean get() = currentPage > 1
}

/**
 * One-shot result of a successful contract action, displayed in a success dialog.
 *
 * **Pattern:** Ephemeral action result sealed type. Using a sealed interface instead of a
 * Boolean `isSuccess` flag lets the UI show tailored content per outcome (upfront payment vs.
 * fulfillment reward) without adding nullable fields to [ContractsUiState]. The result is
 * set in `LocalState` after a successful API call and cleared when the user dismisses the
 * dialog via [ContractsEvent.ActionResultDismissed].
 */
sealed interface ContractActionResult {
    /** The player accepted a contract and received the upfront payment. */
    data class Accepted(val contractId: String, val upfrontPayment: Int) : ContractActionResult
    /** The player fulfilled a contract and received the fulfillment reward. */
    data class Fulfilled(val contractId: String, val reward: Int) : ContractActionResult
}

/**
 * All user interactions the Contracts screen can emit to `ContractsViewModel.onEvent`.
 *
 * **Pattern:** Sealed event interface for UDF. One sealed type covers every possible
 * interaction; the ViewModel handles all of them in a single `when` expression. To apply
 * this pattern in a new project: group events by their effect — some drive a new DB
 * subscription (query-driving), others only mutate ephemeral `LocalState` (UI state).
 *
 * **Query-driving events** (tab, page, limit changes) update `LocalState` fields that are
 * part of the `QueryKey`, which causes `flatMapLatest` to re-subscribe to a new DB query
 * *and* trigger a `loadPage()` network refresh.
 *
 * **Pure UI state events** (dialog open/close) only update `LocalState` fields outside the
 * `QueryKey`, so they never trigger a DB re-subscription.
 */
sealed interface ContractsEvent {
    /** Retries the last failed `loadPage()` call. */
    data object RetryClicked : ContractsEvent
    /** Switches the active tab and resets [ContractsUiState.currentPage] to 1. Query-driving. */
    data class TabSelected(val tab: ContractTab) : ContractsEvent
    /** Navigates to a different page. Query-driving. */
    data class PageChanged(val page: Int) : ContractsEvent
    /** Changes the page size and resets [ContractsUiState.currentPage] to 1. Query-driving. */
    data class LimitChanged(val limit: Int) : ContractsEvent
    /** Opens the accept confirmation dialog for [contract]. */
    data class AcceptClicked(val contract: Contract) : ContractsEvent
    /** Calls `acceptContract` after the user confirms. */
    data object AcceptConfirmed : ContractsEvent
    /** Closes the accept dialog without taking action. */
    data object AcceptDismissed : ContractsEvent
    /** Opens the fulfill confirmation dialog for [contract]. */
    data class FulfillClicked(val contract: Contract) : ContractsEvent
    /** Calls `fulfillContract` after the user confirms. */
    data object FulfillConfirmed : ContractsEvent
    /** Closes the fulfill dialog without taking action. */
    data object FulfillDismissed : ContractsEvent
    /** Clears the success result dialog after the user taps OK. */
    data object ActionResultDismissed : ContractsEvent
}
