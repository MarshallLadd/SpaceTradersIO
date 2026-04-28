package com.brokenhuskysledteam.spacetradersio.ui.contracts

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractTab
import kotlin.math.ceil

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

sealed interface ContractActionResult {
    data class Accepted(val contractId: String, val upfrontPayment: Int) : ContractActionResult
    data class Fulfilled(val contractId: String, val reward: Int) : ContractActionResult
}

sealed interface ContractsEvent {
    data object RetryClicked : ContractsEvent
    data class TabSelected(val tab: ContractTab) : ContractsEvent
    data class PageChanged(val page: Int) : ContractsEvent
    data class LimitChanged(val limit: Int) : ContractsEvent
    data class AcceptClicked(val contract: Contract) : ContractsEvent
    data object AcceptConfirmed : ContractsEvent
    data object AcceptDismissed : ContractsEvent
    data class FulfillClicked(val contract: Contract) : ContractsEvent
    data object FulfillConfirmed : ContractsEvent
    data object FulfillDismissed : ContractsEvent
    data object ActionResultDismissed : ContractsEvent
}
