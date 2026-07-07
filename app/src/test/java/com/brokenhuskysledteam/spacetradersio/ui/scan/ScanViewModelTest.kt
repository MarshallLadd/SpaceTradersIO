package com.brokenhuskysledteam.spacetradersio.ui.scan

import androidx.lifecycle.SavedStateHandle
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ChartResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Cooldown
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ScanSystemsResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ScanWaypointsResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ScannedSystem
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipCargo
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRoute
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRouteWaypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipRegistration
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Waypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavFlightMode
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipRole
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.ChartWaypointUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.ScanSystemsUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.ScanWaypointsUseCase
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
import kotlin.time.Instant

private val cd = Cooldown("LADD-1", 60, 60, Instant.parse("2099-01-01T00:00:00Z"))
private val aSystem = ScannedSystem("X1-AB12", "RED_STAR", 10, 20, 42)
private val aWaypoint = Waypoint("X1-DM91-C1", WaypointType.PLANET, "X1-DM91", 0, 0, null, emptyList(), emptyList(), false)
private val wp = ShipNavRouteWaypoint("X1-DM91-A1", WaypointType.PLANET, "X1-DM91", 0, 0)
private val ship = Ship(
    "LADD-1", ShipRegistration(ShipRole.COMMAND, "COSMIC"),
    ShipNav("X1-DM91", "X1-DM91-A1", ShipNavStatus.IN_ORBIT, ShipNavFlightMode.CRUISE,
        ShipNavRoute(wp, wp, Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2099-01-01T00:00:00Z"))),
    ShipCargo(0, 40), ShipFuel(400, 400), "Frigate", Cooldown("LADD-1", 0, 0, null)
)

private class FakeFleetRepo : FleetRepository {
    private val f = MutableStateFlow<Ship?>(ship)
    override fun observeShips(): Flow<List<Ship>> = MutableStateFlow(emptyList())
    override fun observeShip(shipSymbol: String): Flow<Ship?> = f
    override suspend fun refreshMyShips(page: Int, limit: Int) {}
    override suspend fun refreshMyShip(shipSymbol: String) {}
    override suspend fun saveShip(ship: Ship) {}
    override suspend fun updateShipNav(shipSymbol: String, nav: ShipNav) {}
    override suspend fun updateShipFuel(shipSymbol: String, fuel: ShipFuel) {}
    override suspend fun updateShipCargo(shipSymbol: String, cargo: ShipCargo) {}
    override suspend fun updateShipCooldown(shipSymbol: String, cooldown: Cooldown) {}
    override suspend fun clearAll() {}
}
private class FakeScanSystems : ScanSystemsUseCase {
    var called = false
    override suspend fun invoke(shipSymbol: String) = ScanSystemsResult(cd, listOf(aSystem)).also { called = true }
}
private class FakeScanWaypoints : ScanWaypointsUseCase {
    override suspend fun invoke(shipSymbol: String) = ScanWaypointsResult(cd, listOf(aWaypoint))
}
private class FakeChart(var throws: Exception? = null) : ChartWaypointUseCase {
    override suspend fun invoke(shipSymbol: String): ChartResult { throws?.let { throw it }; return ChartResult(aWaypoint) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {
    private val td = StandardTestDispatcher()
    @BeforeTest fun s() { Dispatchers.setMain(td) }
    @AfterTest fun t() { Dispatchers.resetMain() }

    private fun vm(
        systems: FakeScanSystems = FakeScanSystems(),
        waypoints: FakeScanWaypoints = FakeScanWaypoints(),
        chart: FakeChart = FakeChart()
    ) = ScanViewModel(SavedStateHandle(mapOf("shipSymbol" to "LADD-1")), FakeFleetRepo(), systems, waypoints, chart)

    @Test
    fun scanSystems_populatesSystems_andResult() = runTest {
        val systems = FakeScanSystems()
        val v = vm(systems = systems)
        td.scheduler.advanceUntilIdle()
        v.onEvent(ScanEvent.ScanSystemsClicked)
        td.scheduler.advanceUntilIdle()
        assertEquals(true, systems.called)
        assertEquals(listOf("X1-AB12"), v.uiState.value.systems.map { it.symbol })
        assertIs<ScanResult.SystemsScanned>(v.uiState.value.result)
    }

    @Test
    fun scanWaypoints_populatesWaypoints() = runTest {
        val v = vm()
        td.scheduler.advanceUntilIdle()
        v.onEvent(ScanEvent.ScanWaypointsClicked)
        td.scheduler.advanceUntilIdle()
        assertEquals(listOf("X1-DM91-C1"), v.uiState.value.waypoints.map { it.symbol })
        assertIs<ScanResult.WaypointsScanned>(v.uiState.value.result)
    }

    @Test
    fun chart_setsChartedResult() = runTest {
        val v = vm()
        td.scheduler.advanceUntilIdle()
        v.onEvent(ScanEvent.ChartClicked)
        td.scheduler.advanceUntilIdle()
        val r = assertIs<ScanResult.Charted>(v.uiState.value.result)
        assertEquals("X1-DM91-C1", r.waypointSymbol)
    }

    @Test
    fun chartFailure_setsFailure() = runTest {
        val v = vm(chart = FakeChart(throws = RuntimeException("already charted")))
        td.scheduler.advanceUntilIdle()
        v.onEvent(ScanEvent.ChartClicked)
        td.scheduler.advanceUntilIdle()
        val r = assertIs<ScanResult.Failure>(v.uiState.value.result)
        assertEquals("already charted", r.message)
    }
}
