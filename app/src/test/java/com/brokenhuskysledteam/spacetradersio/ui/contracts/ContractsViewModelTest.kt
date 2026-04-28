package com.brokenhuskysledteam.spacetradersio.ui.contracts

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ContractMeta
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ContractTerms
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractTab
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.ContractRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ContractsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val unacceptedContract = Contract(
        id = "C-001", factionSymbol = "COSMIC", type = ContractType.PROCUREMENT,
        accepted = false, fulfilled = false,
        deadlineToAccept = Instant.parse("2099-01-01T00:00:00Z"),
        terms = ContractTerms(
            deadline = Instant.parse("2099-01-01T00:00:00Z"),
            paymentOnAccepted = 100, paymentOnFulfilled = 900
        ),
        status = ContractStatus.UNACCEPTED
    )

    private inner class FakeContractRepository(
        initialContracts: List<Contract> = listOf(unacceptedContract)
    ) : ContractRepository {
        val _contracts = MutableStateFlow(initialContracts)
        var refreshCallCount = 0
        var acceptedId: String? = null
        var fulfilledId: String? = null

        override fun observeContracts(tab: ContractTab, limit: Long, offset: Long): Flow<List<Contract>> =
            _contracts

        override suspend fun refreshContracts(page: Int, limit: Int): ContractMeta {
            refreshCallCount++
            return ContractMeta(total = _contracts.value.size, page = page, limit = limit)
        }

        override suspend fun acceptContract(contractId: String): Contract {
            acceptedId = contractId
            val accepted = _contracts.value.first { it.id == contractId }
                .copy(accepted = true, status = ContractStatus.ACTIVE)
            _contracts.value = _contracts.value.map { if (it.id == contractId) accepted else it }
            return accepted
        }

        override suspend fun fulfillContract(contractId: String): Contract {
            fulfilledId = contractId
            val fulfilled = _contracts.value.first { it.id == contractId }
                .copy(fulfilled = true, status = ContractStatus.FULFILLED)
            _contracts.value = _contracts.value.map { if (it.id == contractId) fulfilled else it }
            return fulfilled
        }

        override suspend fun upsertContract(contract: Contract) {
            _contracts.value = _contracts.value.map { if (it.id == contract.id) contract else it }
        }
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeViewModel(repo: FakeContractRepository = FakeContractRepository()) =
        ContractsViewModel(repo)

    @Test
    fun initial_load_triggers_refresh_and_populates_contracts() = runTest(testDispatcher) {
        val repo = FakeContractRepository()
        val vm = makeViewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repo.refreshCallCount)
        assertEquals(listOf(unacceptedContract), vm.uiState.value.contracts)
        assertEquals(false, vm.uiState.value.isLoading)
    }

    @Test
    fun tab_change_resets_page_to_1_and_triggers_refresh() = runTest(testDispatcher) {
        val repo = FakeContractRepository()
        val vm = makeViewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(ContractsEvent.TabSelected(ContractTab.HISTORY))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ContractTab.HISTORY, vm.uiState.value.selectedTab)
        assertEquals(1, vm.uiState.value.currentPage)
    }

    @Test
    fun limit_change_resets_page_to_1_and_triggers_refresh() = runTest(testDispatcher) {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(ContractsEvent.LimitChanged(5))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(5, vm.uiState.value.limit)
        assertEquals(1, vm.uiState.value.currentPage)
    }

    @Test
    fun accept_clicked_sets_pendingAccept() = runTest(testDispatcher) {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(ContractsEvent.AcceptClicked(unacceptedContract))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(vm.uiState.value.pendingAccept)
        assertEquals("C-001", vm.uiState.value.pendingAccept?.id)
    }

    @Test
    fun accept_dismissed_clears_pendingAccept() = runTest(testDispatcher) {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(ContractsEvent.AcceptClicked(unacceptedContract))
        vm.onEvent(ContractsEvent.AcceptDismissed)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.pendingAccept)
    }

    @Test
    fun accept_confirmed_calls_repository_and_shows_result() = runTest(testDispatcher) {
        val repo = FakeContractRepository()
        val vm = makeViewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(ContractsEvent.AcceptClicked(unacceptedContract))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(ContractsEvent.AcceptConfirmed)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("C-001", repo.acceptedId)
        assertNull(vm.uiState.value.pendingAccept)
        val result = vm.uiState.value.actionResult
        assertNotNull(result)
        assert(result is ContractActionResult.Accepted)
        assertEquals(100, (result as ContractActionResult.Accepted).upfrontPayment)
    }

    @Test
    fun page_navigation_updates_currentPage_and_triggers_refresh() = runTest(testDispatcher) {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(ContractsEvent.PageChanged(2))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.uiState.value.currentPage)
    }

    @Test
    fun action_result_dismissed_clears_actionResult() = runTest(testDispatcher) {
        val repo = FakeContractRepository()
        val vm = makeViewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(ContractsEvent.AcceptClicked(unacceptedContract))
        vm.onEvent(ContractsEvent.AcceptConfirmed)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(ContractsEvent.ActionResultDismissed)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.actionResult)
    }

    @Test
    fun fulfill_clicked_sets_pendingFulfill() = runTest(testDispatcher) {
        val activeContract = unacceptedContract.copy(accepted = true, status = ContractStatus.ACTIVE)
        val repo = FakeContractRepository(listOf(activeContract))
        val vm = makeViewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(ContractsEvent.FulfillClicked(activeContract))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(vm.uiState.value.pendingFulfill)
        assertEquals("C-001", vm.uiState.value.pendingFulfill?.id)
    }

    @Test
    fun fulfill_confirmed_calls_repository_and_shows_result() = runTest(testDispatcher) {
        val activeContract = unacceptedContract.copy(accepted = true, status = ContractStatus.ACTIVE)
        val repo = FakeContractRepository(listOf(activeContract))
        val vm = makeViewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(ContractsEvent.FulfillClicked(activeContract))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(ContractsEvent.FulfillConfirmed)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("C-001", repo.fulfilledId)
        assertNull(vm.uiState.value.pendingFulfill)
        val result = vm.uiState.value.actionResult
        assertNotNull(result)
        assert(result is ContractActionResult.Fulfilled)
        assertEquals(900, (result as ContractActionResult.Fulfilled).reward)
    }
}
