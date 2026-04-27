package com.brokenhuskysledteam.spacetradersio.ui.shipyard

import androidx.lifecycle.SavedStateHandle
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Shipyard
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipyardShip
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.SupplyLevel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.ShipyardRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val aShip = ShipyardShip(
    type = ShipType.SHIP_MINING_DRONE, name = "Mining Drone", description = "Mines ore.",
    purchasePrice = 50000, supply = SupplyLevel.MODERATE, frameName = "Drone Frame",
    engineSpeed = 10, reactorPowerOutput = 3, crewRequired = 0, crewCapacity = 0
)

private val aShipyard = Shipyard(symbol = "X1-DF55-20250Z", modificationsFee = 1000, ships = listOf(aShip))

private class FakeShipyardRepository(
    initialShipyard: Shipyard? = aShipyard,
    var purchaseException: Exception? = null
) : ShipyardRepository {
    private val _flow = MutableStateFlow(initialShipyard)
    var refreshCalled = false

    override fun observeShipyard(waypointSymbol: String): Flow<Shipyard?> = _flow
    override suspend fun refreshShipyard(systemSymbol: String, waypointSymbol: String) { refreshCalled = true }
    override suspend fun purchaseShip(shipType: ShipType, waypointSymbol: String) {
        purchaseException?.let { throw it }
    }
    override suspend fun clearAll() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShipyardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() { Dispatchers.setMain(testDispatcher) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    private fun buildVm(repo: FakeShipyardRepository = FakeShipyardRepository()): ShipyardViewModel =
        ShipyardViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf("systemSymbol" to "X1-DF55", "waypointSymbol" to "X1-DF55-20250Z")
            ),
            shipyardRepository = repo
        )

    @Test
    fun init_triggersRefresh() = runTest {
        val repo = FakeShipyardRepository()
        buildVm(repo)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(repo.refreshCalled)
    }

    @Test
    fun init_isRefreshingTrue_thenFalseAfterLoad() = runTest {
        val vm = buildVm()
        assertTrue(vm.uiState.value.isRefreshing)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun observeShipyard_emitsShipyard_uiStateUpdated() = runTest {
        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(vm.uiState.value.shipyard)
        assertEquals(1, vm.uiState.value.shipyard?.ships?.size)
    }

    @Test
    fun purchaseShipClicked_setsPendingPurchase() = runTest {
        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(ShipyardEvent.PurchaseShipClicked(aShip))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(aShip, vm.uiState.value.pendingPurchase)
    }

    @Test
    fun purchaseDismissed_clearsPendingPurchase() = runTest {
        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(ShipyardEvent.PurchaseShipClicked(aShip))
        vm.onEvent(ShipyardEvent.PurchaseDismissed)
        assertNull(vm.uiState.value.pendingPurchase)
    }

    @Test
    fun purchaseConfirmed_success_setsPurchaseResultSuccess() = runTest {
        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(ShipyardEvent.PurchaseShipClicked(aShip))
        vm.onEvent(ShipyardEvent.PurchaseConfirmed)
        testDispatcher.scheduler.advanceUntilIdle()
        assertIs<PurchaseResult.Success>(vm.uiState.value.purchaseResult)
        assertNull(vm.uiState.value.pendingPurchase)
        assertFalse(vm.uiState.value.isPurchasing)
    }

    @Test
    fun purchaseConfirmed_failure_setsPurchaseResultFailure() = runTest {
        val repo = FakeShipyardRepository(purchaseException = RuntimeException("Insufficient credits"))
        val vm = buildVm(repo)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(ShipyardEvent.PurchaseShipClicked(aShip))
        vm.onEvent(ShipyardEvent.PurchaseConfirmed)
        testDispatcher.scheduler.advanceUntilIdle()
        assertIs<PurchaseResult.Failure>(vm.uiState.value.purchaseResult)
        assertEquals("Insufficient credits", (vm.uiState.value.purchaseResult as PurchaseResult.Failure).message)
    }

    @Test
    fun purchaseResultDismissed_clearsPurchaseResult() = runTest {
        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(ShipyardEvent.PurchaseShipClicked(aShip))
        vm.onEvent(ShipyardEvent.PurchaseConfirmed)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(ShipyardEvent.PurchaseResultDismissed)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.purchaseResult)
    }

    @Test
    fun retryClicked_triggersRefreshAgain() = runTest {
        val repo = FakeShipyardRepository()
        val vm = buildVm(repo)
        testDispatcher.scheduler.advanceUntilIdle()
        repo.refreshCalled = false
        vm.onEvent(ShipyardEvent.RetryClicked)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(repo.refreshCalled)
    }

    @Test
    fun shipyard_nullShips_fogOfWar_uiStateReflects() = runTest {
        val fogRepo = FakeShipyardRepository(initialShipyard = aShipyard.copy(ships = null))
        val vm = buildVm(fogRepo)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.shipyard?.ships)
    }
}
