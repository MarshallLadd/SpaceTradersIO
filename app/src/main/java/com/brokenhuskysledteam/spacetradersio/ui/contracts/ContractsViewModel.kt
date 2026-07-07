package com.brokenhuskysledteam.spacetradersio.ui.contracts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractTab
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.ContractRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Contracts screen by combining a paginated, tab-filtered reactive DB query
 * with ephemeral UI state (loading, dialogs, action results).
 *
 * **Pattern: QueryKey + flatMapLatest.** The key challenge for a paginated, tabbed screen
 * is that only some state changes should trigger a new DB subscription (tab, page, limit),
 * while other changes (isLoading, pendingAccept, error) should not. This ViewModel solves
 * that with a private [QueryKey] data class that captures only the query-relevant fields.
 * `_localState` is mapped to a `QueryKey`, deduplicated with `distinctUntilChanged()`, then
 * passed to `flatMapLatest` which re-subscribes to `observeContracts` whenever the key
 * changes. Ephemeral mutations to `_localState` that don't change the `QueryKey` are
 * invisible to the DB layer.
 *
 * To apply this pattern in a new project:
 * 1. Identify which fields drive the query (tab, page, limit) and which are UI-only.
 * 2. Create a `QueryKey` data class holding only the query fields.
 * 3. Extract `QueryKey` from `_localState` with `.map { ... }.distinctUntilChanged()`.
 * 4. Pass the key to `flatMapLatest { key -> repository.observeX(key.field1, ...) }`.
 * 5. `combine()` the resulting flow with `_localState` to build the final `uiState`.
 *
 * **LocalState pattern:** `_localState` holds ViewModel-owned ephemeral state (loading flags,
 * dialog selection, action results, errors). The repository's reactive `observeContracts`
 * flow provides the contract list. Both are merged in the `combine()` call that produces
 * `uiState`. See `ShipDetailViewModel` for the same pattern applied to a detail screen.
 *
 * **SharingStarted.Eagerly:** Used so that `uiState.value` is always current, even in unit
 * tests that read `.value` directly without an active collector. See the `CLAUDE.md` gotcha
 * on `stateIn(WhileSubscribed) + StandardTestDispatcher`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContractsViewModel @Inject constructor(
    private val contractRepository: ContractRepository
) : ViewModel() {

    private val _localState = MutableStateFlow(LocalState())

    // Re-subscribes to the DB query only when tab, page, or limit changes —
    // not on every ephemeral mutation (isLoading, pendingAccept, etc.).
    private val contractsFlow = _localState
        .map { QueryKey(it.selectedTab, it.currentPage, it.limit) }
        .distinctUntilChanged()
        .flatMapLatest { key ->
            val offset = ((key.page - 1) * key.limit).toLong()
            contractRepository.observeContracts(key.tab, key.limit.toLong(), offset)
        }

    val uiState: StateFlow<ContractsUiState> = combine(
        contractsFlow,
        _localState
    ) { contracts, local ->
        ContractsUiState(
            contracts = contracts,
            selectedTab = local.selectedTab,
            currentPage = local.currentPage,
            limit = local.limit,
            serverTotal = local.serverTotal,
            isLoading = local.isLoading,
            isActionInProgress = local.isActionInProgress,
            pendingAccept = local.pendingAccept,
            pendingFulfill = local.pendingFulfill,
            actionResult = local.actionResult,
            error = local.error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ContractsUiState())

    init {
        loadPage()
    }

    fun onEvent(event: ContractsEvent) {
        when (event) {
            is ContractsEvent.RetryClicked -> loadPage()
            is ContractsEvent.TabSelected -> {
                _localState.update { it.copy(selectedTab = event.tab, currentPage = 1) }
                loadPage()
            }
            is ContractsEvent.PageChanged -> {
                _localState.update { it.copy(currentPage = event.page) }
                loadPage()
            }
            is ContractsEvent.LimitChanged -> {
                _localState.update { it.copy(limit = event.limit, currentPage = 1) }
                loadPage()
            }
            is ContractsEvent.AcceptClicked ->
                _localState.update { it.copy(pendingAccept = event.contract) }
            is ContractsEvent.AcceptDismissed ->
                _localState.update { it.copy(pendingAccept = null) }
            is ContractsEvent.AcceptConfirmed -> performAccept()
            is ContractsEvent.FulfillClicked ->
                _localState.update { it.copy(pendingFulfill = event.contract) }
            is ContractsEvent.FulfillDismissed ->
                _localState.update { it.copy(pendingFulfill = null) }
            is ContractsEvent.FulfillConfirmed -> performFulfill()
            is ContractsEvent.ActionResultDismissed ->
                _localState.update { it.copy(actionResult = null) }
        }
    }

    /**
     * Fetches the current page from the network and updates [_localState] with the result.
     *
     * The page number and limit are captured from [_localState] *before* updating it to
     * `isLoading = true`. This is intentional: the `update` call changes `_localState`,
     * and reading `.value` afterwards would see the already-updated copy.
     */
    private fun loadPage() {
        val state = _localState.value
        _localState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val meta = contractRepository.refreshContracts(state.currentPage, state.limit)
                _localState.update { it.copy(isLoading = false, serverTotal = meta.total) }
            } catch (e: Exception) {
                _localState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load contracts") }
            }
        }
    }

    /**
     * Calls `acceptContract` after the user has confirmed the dialog.
     *
     * Two-step pattern: (1) clear `pendingAccept` immediately so the confirmation dialog
     * dismisses before the API call completes; (2) set `actionResult` on success so the
     * outcome dialog appears. The same two-step is used by [performFulfill].
     */
    private fun performAccept() {
        val contract = _localState.value.pendingAccept ?: return
        _localState.update { it.copy(isActionInProgress = true, pendingAccept = null, error = null) }
        viewModelScope.launch {
            try {
                val updated = contractRepository.acceptContract(contract.id)
                _localState.update {
                    it.copy(
                        isActionInProgress = false,
                        actionResult = ContractActionResult.Accepted(
                            contractId = updated.id,
                            upfrontPayment = updated.terms.paymentOnAccepted
                        )
                    )
                }
            } catch (e: Exception) {
                _localState.update { it.copy(isActionInProgress = false, error = e.message ?: "Accept failed") }
            }
        }
    }

    /** Calls `fulfillContract` after the user has confirmed the dialog. See [performAccept]. */
    private fun performFulfill() {
        val contract = _localState.value.pendingFulfill ?: return
        _localState.update { it.copy(isActionInProgress = true, pendingFulfill = null, error = null) }
        viewModelScope.launch {
            try {
                val updated = contractRepository.fulfillContract(contract.id)
                _localState.update {
                    it.copy(
                        isActionInProgress = false,
                        actionResult = ContractActionResult.Fulfilled(
                            contractId = updated.id,
                            reward = updated.terms.paymentOnFulfilled
                        )
                    )
                }
            } catch (e: Exception) {
                _localState.update { it.copy(isActionInProgress = false, error = e.message ?: "Fulfill failed") }
            }
        }
    }

    /**
     * ViewModel-owned ephemeral state, separate from the repository's reactive data stream.
     *
     * **Pattern:** LocalState. Holds everything the ViewModel manages directly: loading flags,
     * dialog contracts, action results, and error messages. The repository's `observeContracts`
     * Flow provides the actual contract list. Both streams are merged in the `combine()` call
     * above to produce the single [uiState] snapshot the composable reads. See
     * `ShipDetailViewModel.LocalState` for the same pattern on a detail screen.
     *
     * **Important:** [selectedTab], [currentPage], and [limit] are also part of this class
     * because they are inputs to the [QueryKey]. Changes to these three fields (and only these
     * three) cause `flatMapLatest` to re-subscribe to a new DB query.
     */
    private data class LocalState(
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
    )

    /**
     * The minimal identity of a DB subscription: which tab is shown, which page, and how many
     * results per page.
     *
     * **Pattern:** Query key for reactive pagination. By extracting only these three fields
     * from [LocalState] and wrapping them in a `data class`, `distinctUntilChanged()` can
     * suppress re-subscriptions caused by unrelated [LocalState] mutations (loading flags,
     * dialog state). Any change to [tab], [page], or [limit] produces a new [QueryKey]
     * value, which triggers `flatMapLatest` to cancel the old subscription and start a new one.
     */
    private data class QueryKey(val tab: ContractTab, val page: Int, val limit: Int)
}
