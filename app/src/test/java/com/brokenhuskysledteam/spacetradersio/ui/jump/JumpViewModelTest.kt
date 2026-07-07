package com.brokenhuskysledteam.spacetradersio.ui.jump

import androidx.lifecycle.SavedStateHandle
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Cooldown
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.JumpGate
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.JumpResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipCargo
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRoute
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRouteWaypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipRegistration
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SystemPage
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavFlightMode
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipRole
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TravelRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.JumpShipUseCase
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

private val gwp = ShipNavRouteWaypoint("X1-DM91-I52", WaypointType.JUMP_GATE, "X1-DM91", 0, 0)
private val gateShip = Ship(
    "LADD-1", ShipRegistration(ShipRole.COMMAND, "COSMIC"),
    ShipNav("X1-DM91", "X1-DM91-I52", ShipNavStatus.IN_ORBIT, ShipNavFlightMode.CRUISE,
        ShipNavRoute(gwp, gwp, Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2099-01-01T00:00:00Z"))),
    ShipCargo(0, 40), ShipFuel(400, 400), "Frigate", Cooldown("LADD-1", 0, 0, null)
)

private class FakeTravelRepo(var gate: JumpGate = JumpGate("X1-DM91-I52", listOf("X1-AB12-I10")), var throws: Exception? = null) : TravelRepository {
    override suspend fun getJumpGate(systemSymbol: String, waypointSymbol: String): JumpGate { throws?.let { throw it }; return gate }
    override suspend fun getSystems(page: Int, limit: Int): SystemPage = SystemPage(emptyList(), page, 0)
}
private class FakeFleetRepo(ship: Ship = gateShip) : FleetRepository {
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
private class FakeJump(var throws: Exception? = null) : JumpShipUseCase {
    var lastArgs: Pair<String, String>? = null
    override suspend fun invoke(shipSymbol: String, waypointSymbol: String): JumpResult {
        lastArgs = shipSymbol to waypointSymbol; throws?.let { throw it }
        return JumpResult(gateShip.nav, Cooldown("LADD-1", 60, 60, Instant.parse("2099-01-01T00:00:00Z")))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class JumpViewModelTest {
    private val td = StandardTestDispatcher()
    @BeforeTest fun s() { Dispatchers.setMain(td) }
    @AfterTest fun t() { Dispatchers.resetMain() }

    private fun vm(travel: FakeTravelRepo = FakeTravelRepo(), jump: FakeJump = FakeJump()) =
        JumpViewModel(SavedStateHandle(mapOf("shipSymbol" to "LADD-1", "systemSymbol" to "X1-DM91", "waypointSymbol" to "X1-DM91-I52")),
            travel, FakeFleetRepo(), jump)

    @Test
    fun init_loadsConnections() = runTest {
        val v = vm()
        td.scheduler.advanceUntilIdle()
        assertEquals(listOf("X1-AB12-I10"), v.uiState.value.connections)
        assertEquals(true, v.uiState.value.canJump)
    }

    @Test
    fun loadFailure_setsError() = runTest {
        val v = vm(travel = FakeTravelRepo(throws = RuntimeException("no gate")))
        td.scheduler.advanceUntilIdle()
        assertEquals("no gate", v.uiState.value.error)
    }

    @Test
    fun jumpClicked_callsJump_andSetsSuccess() = runTest {
        val jump = FakeJump()
        val v = vm(jump = jump)
        td.scheduler.advanceUntilIdle()
        v.onEvent(JumpEvent.JumpClicked("X1-AB12-I10"))
        td.scheduler.advanceUntilIdle()
        assertEquals("LADD-1" to "X1-AB12-I10", jump.lastArgs)
        val r = assertIs<JumpResultUi.Success>(v.uiState.value.result)
        assertEquals("X1-AB12-I10", r.destination)
    }

    @Test
    fun jumpFailure_setsFailure() = runTest {
        val v = vm(jump = FakeJump(throws = RuntimeException("insufficient antimatter")))
        td.scheduler.advanceUntilIdle()
        v.onEvent(JumpEvent.JumpClicked("X1-AB12-I10"))
        td.scheduler.advanceUntilIdle()
        val r = assertIs<JumpResultUi.Failure>(v.uiState.value.result)
        assertEquals("insufficient antimatter", r.message)
    }
}
