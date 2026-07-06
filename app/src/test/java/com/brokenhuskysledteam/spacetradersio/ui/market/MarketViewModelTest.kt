package com.brokenhuskysledteam.spacetradersio.ui.market

import androidx.lifecycle.SavedStateHandle
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.CargoItem
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.CargoTradeResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Market
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.MarketTradeGood
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.MarketTransaction
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipCargo
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.MarketTradeGoodType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.SupplyLevel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.AgentRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.MarketRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.BuyCargoUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.SellCargoUseCase
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
import kotlin.time.Instant

private val aGood = MarketTradeGood(
    symbol = "FOOD", type = MarketTradeGoodType.EXCHANGE, tradeVolume = 60,
    supply = SupplyLevel.MODERATE, purchasePrice = 100, sellPrice = 90, activity = "WEAK"
)
private val aMarket = Market(
    symbol = "X1-DM91-A1",
    imports = emptyList(), exports = emptyList(), exchange = emptyList(),
    tradeGoods = listOf(aGood), transactions = emptyList()
)
private val anAgent = Agent(accountId = "acc", symbol = "LADD", headquarters = "X1-DM91-A1", credits = 175000, startingFaction = "COSMIC", shipCount = 2)

private fun tradeResult(units: Int, total: Int, credits: Long) = CargoTradeResult(
    agent = anAgent.copy(credits = credits),
    cargo = ShipCargo(units, 40, listOf(CargoItem("FOOD", "Food", "Food.", units))),
    transaction = MarketTransaction("X1-DM91-A1", "LADD-1", "FOOD", "PURCHASE", units, total / units, total, Instant.parse("2026-07-06T00:00:00Z"))
)

private class FakeMarketRepository(
    var market: Market = aMarket,
    var loadException: Exception? = null
) : MarketRepository {
    var getCount = 0
    override suspend fun getMarket(systemSymbol: String, waypointSymbol: String): Market {
        getCount++
        loadException?.let { throw it }
        return market
    }
}

private class FakeAgentRepository(agent: Agent? = anAgent) : AgentRepository {
    private val _flow = MutableStateFlow(agent)
    override fun observeAgent(): Flow<Agent?> = _flow
    override suspend fun refreshAgent() {}
    override suspend fun saveAgent(agent: Agent) { _flow.value = agent }
    override suspend fun updateCredits(symbol: String, credits: Long) {}
    override suspend fun clearAll() {}
}

private class FakeBuyCargoUseCase(var result: CargoTradeResult = tradeResult(1, 100, 174900), var throws: Exception? = null) : BuyCargoUseCase {
    var lastArgs: Triple<String, String, Int>? = null
    override suspend fun invoke(shipSymbol: String, tradeSymbol: String, units: Int): CargoTradeResult {
        lastArgs = Triple(shipSymbol, tradeSymbol, units)
        throws?.let { throw it }
        return result
    }
}

private class FakeSellCargoUseCase(var result: CargoTradeResult = tradeResult(1, 90, 175090)) : SellCargoUseCase {
    var lastArgs: Triple<String, String, Int>? = null
    override suspend fun invoke(shipSymbol: String, tradeSymbol: String, units: Int): CargoTradeResult {
        lastArgs = Triple(shipSymbol, tradeSymbol, units)
        return result
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MarketViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(testDispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun buildVm(
        market: FakeMarketRepository = FakeMarketRepository(),
        agent: FakeAgentRepository = FakeAgentRepository(),
        buy: FakeBuyCargoUseCase = FakeBuyCargoUseCase(),
        sell: FakeSellCargoUseCase = FakeSellCargoUseCase()
    ) = MarketViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf("systemSymbol" to "X1-DM91", "waypointSymbol" to "X1-DM91-A1", "shipSymbol" to "LADD-1")
        ),
        marketRepository = market,
        agentRepository = agent,
        buyCargoUseCase = buy,
        sellCargoUseCase = sell
    )

    @Test
    fun init_loadsMarketAndCredits() = runTest {
        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(vm.uiState.value.market)
        assertEquals(1, vm.uiState.value.market?.tradeGoods?.size)
        assertEquals(175000L, vm.uiState.value.credits)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun loadFailure_setsError() = runTest {
        val vm = buildVm(FakeMarketRepository(loadException = RuntimeException("no market")))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("no market", vm.uiState.value.error)
        assertNull(vm.uiState.value.market)
    }

    @Test
    fun tradeClicked_setsPendingTradeWithDefaultUnits() = runTest {
        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(MarketEvent.TradeClicked(aGood, TradeSide.BUY))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(aGood, vm.uiState.value.pendingTrade?.good)
        assertEquals(TradeSide.BUY, vm.uiState.value.pendingTrade?.side)
        assertEquals("1", vm.uiState.value.tradeUnits)
    }

    @Test
    fun tradeUnitsChanged_keepsOnlyDigits() = runTest {
        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(MarketEvent.TradeUnitsChanged("1a2b"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("12", vm.uiState.value.tradeUnits)
    }

    @Test
    fun buyConfirmed_callsBuyUseCase_andSetsSuccessResult() = runTest {
        val buy = FakeBuyCargoUseCase(result = tradeResult(2, 200, 174800))
        val vm = buildVm(buy = buy)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(MarketEvent.TradeClicked(aGood, TradeSide.BUY))
        vm.onEvent(MarketEvent.TradeUnitsChanged("2"))
        vm.onEvent(MarketEvent.TradeConfirmed)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(Triple("LADD-1", "FOOD", 2), buy.lastArgs)
        val result = assertIs<TradeResult.Success>(vm.uiState.value.tradeResult)
        assertEquals(TradeSide.BUY, result.side)
        assertEquals(174800L, result.newCredits)
        assertFalse(vm.uiState.value.isTradeInProgress)
        assertNull(vm.uiState.value.pendingTrade)
    }

    @Test
    fun sellConfirmed_callsSellUseCase() = runTest {
        val sell = FakeSellCargoUseCase(result = tradeResult(1, 90, 175090))
        val vm = buildVm(sell = sell)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(MarketEvent.TradeClicked(aGood, TradeSide.SELL))
        vm.onEvent(MarketEvent.TradeConfirmed)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(Triple("LADD-1", "FOOD", 1), sell.lastArgs)
        assertIs<TradeResult.Success>(vm.uiState.value.tradeResult)
    }

    @Test
    fun tradeFailure_setsFailureResult() = runTest {
        val buy = FakeBuyCargoUseCase(throws = RuntimeException("insufficient credits"))
        val vm = buildVm(buy = buy)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(MarketEvent.TradeClicked(aGood, TradeSide.BUY))
        vm.onEvent(MarketEvent.TradeConfirmed)
        testDispatcher.scheduler.advanceUntilIdle()
        val result = assertIs<TradeResult.Failure>(vm.uiState.value.tradeResult)
        assertEquals("insufficient credits", result.message)
    }

    @Test
    fun tradeDismissed_clearsPending() = runTest {
        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(MarketEvent.TradeClicked(aGood, TradeSide.BUY))
        vm.onEvent(MarketEvent.TradeDismissed)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.pendingTrade)
    }

    @Test
    fun tradeResultDismissed_clearsResult() = runTest {
        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(MarketEvent.TradeClicked(aGood, TradeSide.BUY))
        vm.onEvent(MarketEvent.TradeConfirmed)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(MarketEvent.TradeResultDismissed)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.tradeResult)
    }
}
