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

    private data class QueryKey(val tab: ContractTab, val page: Int, val limit: Int)
}
