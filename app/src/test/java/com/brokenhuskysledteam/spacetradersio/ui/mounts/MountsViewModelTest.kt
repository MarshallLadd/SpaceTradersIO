package com.brokenhuskysledteam.spacetradersio.ui.mounts

import androidx.lifecycle.SavedStateHandle
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.CargoItem
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Cooldown
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.MountModificationResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.MountRequirements
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipCargo
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipModificationTransaction
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipMount
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRoute
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRouteWaypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipRegistration
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Waypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavFlightMode
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipRole
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Cooldown as CooldownModel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.MountsRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.SystemRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.InstallMountUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RemoveMountUseCase
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val aMount = ShipMount("MOUNT_MINING_LASER_II", "Mining Laser II", "x", 5, MountRequirements(2, 2, null), emptyList())
private val anAgent = Agent("acc", "LADD", "X1-DM91-A1", 168000, "COSMIC", 2)
private fun modResult(mounts: List<ShipMount>) = MountModificationResult(
    agent = anAgent,
    mounts = mounts,
    cargo = ShipCargo(0, 40),
    transaction = ShipModificationTransaction("X1-DM91-A1", "LADD-1", "MOUNT_MINING_LASER_I", 3600, Instant.parse("2026-07-06T00:00:00Z"))
)

private val wp = ShipNavRouteWaypoint("X1-DM91-A1", WaypointType.PLANET, "X1-DM91", 0, 0)
private val testShip = Ship(
    symbol = "LADD-1",
    registration = ShipRegistration(ShipRole.COMMAND, "COSMIC"),
    nav = ShipNav("X1-DM91", "X1-DM91-A1", ShipNavStatus.DOCKED, ShipNavFlightMode.CRUISE,
        ShipNavRoute(wp, wp, Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2099-01-01T00:00:00Z"))),
    cargo = ShipCargo(1, 40, listOf(CargoItem("MOUNT_MINING_LASER_I", "Mining Laser I", "z", 1))),
    fuel = ShipFuel(400, 400),
    frameName = "Frigate",
    cooldown = CooldownModel("LADD-1", 0, 0, null)
)

private class FakeMountsRepository(var mounts: List<ShipMount> = listOf(aMount), var throws: Exception? = null) : MountsRepository {
    override suspend fun getMounts(shipSymbol: String): List<ShipMount> {
        throws?.let { throw it }
        return mounts
    }
}

private class FakeFleetRepo(ship: Ship? = testShip) : FleetRepository {
    private val shipFlow = MutableStateFlow(ship)
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

private class FakeSystemRepo : SystemRepository {
    override suspend fun getSystemWaypoints(systemSymbol: String): List<Waypoint> = emptyList()
    override suspend fun getWaypoint(systemSymbol: String, waypointSymbol: String): Waypoint =
        throw RuntimeException("no waypoint") // → hasShipyard defaults to false
    override fun observeWaypoint(waypointSymbol: String): Flow<Waypoint?> = MutableStateFlow(null)
}

private class FakeInstall(var result: MountModificationResult = modResult(listOf(aMount))) : InstallMountUseCase {
    var lastArgs: Pair<String, String>? = null
    override suspend fun invoke(shipSymbol: String, mountSymbol: String): MountModificationResult {
        lastArgs = shipSymbol to mountSymbol; return result
    }
}
private class FakeRemove(var result: MountModificationResult = modResult(emptyList()), var throws: Exception? = null) : RemoveMountUseCase {
    var lastArgs: Pair<String, String>? = null
    override suspend fun invoke(shipSymbol: String, mountSymbol: String): MountModificationResult {
        lastArgs = shipSymbol to mountSymbol; throws?.let { throw it }; return result
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MountsViewModelTest {
    private val td = StandardTestDispatcher()
    @BeforeTest fun s() { Dispatchers.setMain(td) }
    @AfterTest fun t() { Dispatchers.resetMain() }

    private fun vm(
        mounts: FakeMountsRepository = FakeMountsRepository(),
        install: FakeInstall = FakeInstall(),
        remove: FakeRemove = FakeRemove()
    ) = MountsViewModel(
        SavedStateHandle(mapOf("shipSymbol" to "LADD-1", "systemSymbol" to "X1-DM91", "waypointSymbol" to "X1-DM91-A1")),
        mounts, FakeFleetRepo(), FakeSystemRepo(), install, remove
    )

    @Test
    fun init_loadsMounts_andSurfacesInstallableCargo() = runTest {
        val v = vm()
        td.scheduler.advanceUntilIdle()
        assertEquals(1, v.uiState.value.mounts.size)
        assertEquals(listOf("MOUNT_MINING_LASER_I"), v.uiState.value.installable.map { it.symbol })
    }

    @Test
    fun loadFailure_setsError() = runTest {
        val v = vm(mounts = FakeMountsRepository(throws = RuntimeException("boom")))
        td.scheduler.advanceUntilIdle()
        assertEquals("boom", v.uiState.value.error)
    }

    @Test
    fun removeClicked_callsRemove_andSetsSuccessAndNewMounts() = runTest {
        val remove = FakeRemove(result = modResult(emptyList()))
        val v = vm(remove = remove)
        td.scheduler.advanceUntilIdle()
        v.onEvent(MountsEvent.RemoveClicked("MOUNT_MINING_LASER_II"))
        td.scheduler.advanceUntilIdle()
        assertEquals("LADD-1" to "MOUNT_MINING_LASER_II", remove.lastArgs)
        val r = assertIs<MountModResult.Success>(v.uiState.value.modResult)
        assertEquals("REMOVE", r.action)
        assertTrue(v.uiState.value.mounts.isEmpty())
    }

    @Test
    fun installClicked_callsInstall() = runTest {
        val install = FakeInstall()
        val v = vm(install = install)
        td.scheduler.advanceUntilIdle()
        v.onEvent(MountsEvent.InstallClicked("MOUNT_MINING_LASER_I"))
        td.scheduler.advanceUntilIdle()
        assertEquals("LADD-1" to "MOUNT_MINING_LASER_I", install.lastArgs)
        assertIs<MountModResult.Success>(v.uiState.value.modResult)
    }

    @Test
    fun modifyFailure_setsFailureResult() = runTest {
        val v = vm(remove = FakeRemove(throws = RuntimeException("not at shipyard")))
        td.scheduler.advanceUntilIdle()
        v.onEvent(MountsEvent.RemoveClicked("MOUNT_MINING_LASER_II"))
        td.scheduler.advanceUntilIdle()
        val r = assertIs<MountModResult.Failure>(v.uiState.value.modResult)
        assertEquals("not at shipyard", r.message)
    }

    @Test
    fun resultDismissed_clearsResult() = runTest {
        val v = vm()
        td.scheduler.advanceUntilIdle()
        v.onEvent(MountsEvent.InstallClicked("MOUNT_MINING_LASER_I"))
        td.scheduler.advanceUntilIdle()
        v.onEvent(MountsEvent.ResultDismissed)
        td.scheduler.advanceUntilIdle()
        assertNull(v.uiState.value.modResult)
    }
}
