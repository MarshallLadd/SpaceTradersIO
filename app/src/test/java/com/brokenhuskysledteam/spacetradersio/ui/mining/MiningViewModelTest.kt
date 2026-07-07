package com.brokenhuskysledteam.spacetradersio.ui.mining

import androidx.lifecycle.SavedStateHandle
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Cooldown
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ExtractResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipCargo
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRoute
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRouteWaypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipRegistration
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Survey
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SurveyResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavFlightMode
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipRole
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.CreateSurveyUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.ExtractResourcesUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.ExtractWithSurveyUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.JettisonCargoUseCase
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
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

private val wp = ShipNavRouteWaypoint("X1-DM91-B7", WaypointType.ASTEROID, "X1-DM91", 0, 0)
private val orbitingShip = Ship(
    symbol = "LADD-1",
    registration = ShipRegistration(ShipRole.COMMAND, "COSMIC"),
    nav = ShipNav("X1-DM91", "X1-DM91-B7", ShipNavStatus.IN_ORBIT, ShipNavFlightMode.CRUISE,
        ShipNavRoute(wp, wp, Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2099-01-01T00:00:00Z"))),
    cargo = ShipCargo(0, 40),
    fuel = ShipFuel(400, 400),
    frameName = "Frigate",
    cooldown = Cooldown("LADD-1", 0, 0, null)
)
private val aSurvey = Survey("sig-1", "X1-DM91-B7", listOf("IRON_ORE"), Instant.parse("2099-01-01T01:00:00Z"), "MODERATE")
private fun extractResult(sym: String, units: Int) = ExtractResult(
    "LADD-1", sym, units, Cooldown("LADD-1", 70, 70, Instant.parse("2099-01-01T00:00:00Z")),
    ShipCargo(units, 40)
)

private class FakeFleetRepo(ship: Ship = orbitingShip) : FleetRepository {
    private val shipFlow = MutableStateFlow<Ship?>(ship)
    override fun observeShips(): Flow<List<Ship>> = MutableStateFlow(emptyList())
    override fun observeShip(shipSymbol: String): Flow<Ship?> = shipFlow
    override suspend fun refreshMyShips(page: Int, limit: Int) {}
    override suspend fun refreshMyShip(shipSymbol: String) {}
    override suspend fun saveShip(ship: Ship) {}
    override suspend fun updateShipNav(shipSymbol: String, nav: ShipNav) {}
    override suspend fun updateShipFuel(shipSymbol: String, fuel: ShipFuel) {}
    override suspend fun updateShipCargo(shipSymbol: String, cargo: ShipCargo) {}
    override suspend fun updateShipCooldown(shipSymbol: String, cooldown: Cooldown) {}
    override suspend fun clearAll() {}
}

private class FakeExtract(var result: ExtractResult = extractResult("IRON_ORE", 3), var throws: Exception? = null) : ExtractResourcesUseCase {
    var called = false
    override suspend fun invoke(shipSymbol: String): ExtractResult { called = true; throws?.let { throw it }; return result }
}
private class FakeExtractSurvey(var result: ExtractResult = extractResult("COPPER_ORE", 5)) : ExtractWithSurveyUseCase {
    var lastSurvey: Survey? = null
    override suspend fun invoke(shipSymbol: String, survey: Survey): ExtractResult { lastSurvey = survey; return result }
}
private class FakeSurvey(var result: SurveyResult = SurveyResult(Cooldown("LADD-1", 60, 60, Instant.parse("2099-01-01T00:00:00Z")), listOf(aSurvey))) : CreateSurveyUseCase {
    override suspend fun invoke(shipSymbol: String): SurveyResult = result
}
private class FakeJettison : JettisonCargoUseCase {
    var lastArgs: Triple<String, String, Int>? = null
    override suspend fun invoke(shipSymbol: String, tradeSymbol: String, units: Int): ShipCargo {
        lastArgs = Triple(shipSymbol, tradeSymbol, units); return ShipCargo(0, 40)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MiningViewModelTest {
    private val td = StandardTestDispatcher()
    @BeforeTest fun s() { Dispatchers.setMain(td) }
    @AfterTest fun t() { Dispatchers.resetMain() }

    private fun vm(
        fleet: FakeFleetRepo = FakeFleetRepo(),
        extract: FakeExtract = FakeExtract(),
        extractSurvey: FakeExtractSurvey = FakeExtractSurvey(),
        survey: FakeSurvey = FakeSurvey(),
        jettison: FakeJettison = FakeJettison()
    ) = MiningViewModel(
        SavedStateHandle(mapOf("shipSymbol" to "LADD-1")),
        fleet, extract, extractSurvey, survey, jettison
    )

    @Test
    fun uiState_reflectsOrbitingShip_andCanAct() = runTest {
        val v = vm()
        td.scheduler.advanceUntilIdle()
        assertTrue(v.uiState.value.inOrbit)
        assertTrue(v.uiState.value.canAct)
    }

    @Test
    fun extractClicked_callsExtract_andSetsExtractedResult() = runTest {
        val extract = FakeExtract(result = extractResult("IRON_ORE", 4))
        val v = vm(extract = extract)
        td.scheduler.advanceUntilIdle()
        v.onEvent(MiningEvent.ExtractClicked)
        td.scheduler.advanceUntilIdle()
        assertTrue(extract.called)
        val r = assertIs<MiningResult.Extracted>(v.uiState.value.result)
        assertEquals("IRON_ORE", r.yieldSymbol)
        assertEquals(4, r.units)
    }

    @Test
    fun surveyClicked_populatesSurveys_andSetsSurveyedResult() = runTest {
        val v = vm()
        td.scheduler.advanceUntilIdle()
        v.onEvent(MiningEvent.SurveyClicked)
        td.scheduler.advanceUntilIdle()
        assertEquals(1, v.uiState.value.surveys.size)
        assertIs<MiningResult.Surveyed>(v.uiState.value.result)
    }

    @Test
    fun extractWithSurvey_passesSurvey() = runTest {
        val es = FakeExtractSurvey()
        val v = vm(extractSurvey = es)
        td.scheduler.advanceUntilIdle()
        v.onEvent(MiningEvent.ExtractWithSurveyClicked(aSurvey))
        td.scheduler.advanceUntilIdle()
        assertEquals(aSurvey, es.lastSurvey)
        assertIs<MiningResult.Extracted>(v.uiState.value.result)
    }

    @Test
    fun jettisonClicked_callsJettison() = runTest {
        val j = FakeJettison()
        val v = vm(jettison = j)
        td.scheduler.advanceUntilIdle()
        v.onEvent(MiningEvent.JettisonClicked("IRON_ORE", 2))
        td.scheduler.advanceUntilIdle()
        assertEquals(Triple("LADD-1", "IRON_ORE", 2), j.lastArgs)
        assertIs<MiningResult.Jettisoned>(v.uiState.value.result)
    }

    @Test
    fun extractFailure_setsFailureResult() = runTest {
        val v = vm(extract = FakeExtract(throws = RuntimeException("ship on cooldown")))
        td.scheduler.advanceUntilIdle()
        v.onEvent(MiningEvent.ExtractClicked)
        td.scheduler.advanceUntilIdle()
        val r = assertIs<MiningResult.Failure>(v.uiState.value.result)
        assertEquals("ship on cooldown", r.message)
    }
}
