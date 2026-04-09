package com.brokenhuskysledteam.spacetradersio.ui.systemmap

import androidx.lifecycle.SavedStateHandle
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Cooldown
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.NavigateResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipCargo
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRoute
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRouteWaypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipRegistration
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Waypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.WaypointTrait
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavFlightMode
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipRole
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.SystemRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.WaypointStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.NavigateShipUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.time.Instant

private val NOW = Instant.parse("2025-06-01T10:00:00.000Z")

private fun fakeWaypoint(
    symbol: String,
    type: WaypointType = WaypointType.PLANET,
    x: Int = 0,
    y: Int = 0,
    orbits: String? = null,
    traits: List<WaypointTrait> = emptyList()
) = Waypoint(
    symbol = symbol, type = type, systemSymbol = "X1-DF55",
    x = x, y = y, orbits = orbits, orbitals = emptyList(),
    traits = traits, isUnderConstruction = false
)

private fun fakeShip(
    symbol: String = "LADD-1",
    status: ShipNavStatus = ShipNavStatus.IN_ORBIT,
    waypointSymbol: String = "X1-DF55-20250Z",
    fuel: ShipFuel = ShipFuel(400, 400)
): Ship {
    val wp = ShipNavRouteWaypoint(waypointSymbol, WaypointType.MOON, "X1-DF55", 0, 0)
    return Ship(
        symbol = symbol,
        registration = ShipRegistration(ShipRole.COMMAND, "COSMIC"),
        nav = ShipNav("X1-DF55", waypointSymbol, status, ShipNavFlightMode.CRUISE,
            ShipNavRoute(wp, wp, NOW, NOW)),
        cargo = ShipCargo(0, 40),
        fuel = fuel,
        frameName = "Shuttle Frame",
        cooldown = Cooldown(symbol, 0, 0, null)
    )
}

private class FakeSystemRepository(
    private val store: WaypointStateStore,
    var waypoints: List<Waypoint> = emptyList(),
    var shouldThrow: Boolean = false
) : SystemRepository {
    override suspend fun getSystemWaypoints(systemSymbol: String): List<Waypoint> {
        if (shouldThrow) throw RuntimeException("Network error")
        store.putAll(waypoints.associateBy { it.symbol })
        return waypoints
    }
}

private class FakeNavigateShipUseCase(
    private val fleetStore: FleetStateStore,
    var result: NavigateResult? = null,
    var shouldThrow: Boolean = false
) : NavigateShipUseCase {
    var lastShipSymbol: String? = null
    var lastWaypointSymbol: String? = null

    override suspend fun invoke(shipSymbol: String, waypointSymbol: String): NavigateResult {
        lastShipSymbol = shipSymbol
        lastWaypointSymbol = waypointSymbol
        if (shouldThrow) throw RuntimeException("Navigation failed")
        val r = result ?: throw IllegalStateException("No result configured")
        fleetStore.update(shipSymbol) { it.copy(nav = r.nav, fuel = r.fuel) }
        return r
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SystemMapViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var waypointStore: WaypointStateStore
    private lateinit var fleetStore: FleetStateStore
    private lateinit var systemRepo: FakeSystemRepository
    private lateinit var navigateUseCase: FakeNavigateShipUseCase

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        waypointStore = WaypointStateStore()
        fleetStore = FleetStateStore()
        systemRepo = FakeSystemRepository(waypointStore)
        navigateUseCase = FakeNavigateShipUseCase(fleetStore)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        systemSymbol: String = "X1-DF55",
        focusWaypointSymbol: String? = null,
        shipSymbol: String? = null
    ): SystemMapViewModel {
        val savedState = mutableMapOf<String, Any?>("systemSymbol" to systemSymbol)
        if (focusWaypointSymbol != null) savedState["focusWaypointSymbol"] = focusWaypointSymbol
        if (shipSymbol != null) savedState["shipSymbol"] = shipSymbol
        return SystemMapViewModel(
            savedStateHandle = SavedStateHandle(savedState),
            systemRepository = systemRepo,
            fleetStateStore = fleetStore,
            waypointStateStore = waypointStore,
            navigateShipUseCase = navigateUseCase
        )
    }

    // ── loading ─────────────────────────────────────────────────────────────

    @Test
    fun init_loadsWaypoints_setsLoadingFalse() = runTest {
        systemRepo.waypoints = listOf(fakeWaypoint("WP-1"))
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun init_loadsWaypoints_displaysWaypoints() = runTest {
        systemRepo.waypoints = listOf(fakeWaypoint("WP-1"), fakeWaypoint("WP-2"))
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, vm.uiState.value.waypoints.size)
    }

    @Test
    fun init_loadError_setsErrorMessage() = runTest {
        systemRepo.shouldThrow = true
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    // ── sorting ─────────────────────────────────────────────────────────────

    @Test
    fun sortByName_ordersAlphabetically() = runTest {
        systemRepo.waypoints = listOf(fakeWaypoint("WP-B"), fakeWaypoint("WP-A"))
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(SystemMapEvent.SortModeSelected(SortMode.NAME))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("WP-A", vm.uiState.value.waypoints[0].waypoint.symbol)
        assertEquals("WP-B", vm.uiState.value.waypoints[1].waypoint.symbol)
    }

    @Test
    fun sortByDistance_ordersFromSystemCenter() = runTest {
        systemRepo.waypoints = listOf(
            fakeWaypoint("FAR", x = 100, y = 100),
            fakeWaypoint("NEAR", x = 1, y = 1)
        )
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(SystemMapEvent.SortModeSelected(SortMode.DISTANCE))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("NEAR", vm.uiState.value.waypoints[0].waypoint.symbol)
        assertEquals("FAR", vm.uiState.value.waypoints[1].waypoint.symbol)
    }

    @Test
    fun distanceOriginShipLocation_changesDistances() = runTest {
        fleetStore.put("LADD-1", fakeShip(waypointSymbol = "WP-SHIP"))
        systemRepo.waypoints = listOf(
            fakeWaypoint("WP-A", x = 0, y = 0),
            fakeWaypoint("WP-B", x = 10, y = 10)
        )
        val vm = createViewModel(shipSymbol = "LADD-1")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(SystemMapEvent.DistanceOriginSelected(DistanceOrigin.SHIP_LOCATION))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(DistanceOrigin.SHIP_LOCATION, state.distanceOrigin)
    }

    // ── type filtering ──────────────────────────────────────────────────────

    @Test
    fun typeFilter_toggleOn_filtersWaypoints() = runTest {
        systemRepo.waypoints = listOf(
            fakeWaypoint("PLANET-1", type = WaypointType.PLANET),
            fakeWaypoint("GATE-1", type = WaypointType.JUMP_GATE)
        )
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(SystemMapEvent.TypeFilterToggled(WaypointType.PLANET))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.uiState.value.waypoints.size)
        assertEquals("PLANET-1", vm.uiState.value.waypoints[0].waypoint.symbol)
    }

    @Test
    fun typeFilter_toggleOff_showsAll() = runTest {
        systemRepo.waypoints = listOf(
            fakeWaypoint("PLANET-1", type = WaypointType.PLANET),
            fakeWaypoint("GATE-1", type = WaypointType.JUMP_GATE)
        )
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(SystemMapEvent.TypeFilterToggled(WaypointType.PLANET))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(SystemMapEvent.TypeFilterToggled(WaypointType.PLANET))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, vm.uiState.value.waypoints.size)
    }

    // ── trait filtering ─────────────────────────────────────────────────────

    @Test
    fun traitFilter_marketplace_filtersCorrectly() = runTest {
        systemRepo.waypoints = listOf(
            fakeWaypoint("WITH-MARKET", traits = listOf(
                WaypointTrait(WaypointTraitSymbol.MARKETPLACE, "Marketplace", "A marketplace")
            )),
            fakeWaypoint("NO-MARKET")
        )
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(SystemMapEvent.TraitFilterToggled(WaypointTraitSymbol.MARKETPLACE))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.uiState.value.waypoints.size)
        assertEquals("WITH-MARKET", vm.uiState.value.waypoints[0].waypoint.symbol)
    }

    // ── tree hierarchy ──────────────────────────────────────────────────────

    @Test
    fun moonNestsUnderPlanet() = runTest {
        systemRepo.waypoints = listOf(
            fakeWaypoint("PLANET-1", type = WaypointType.PLANET),
            fakeWaypoint("MOON-1", type = WaypointType.MOON, orbits = "PLANET-1")
        )
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val nodes = vm.uiState.value.waypoints
        assertEquals(1, nodes.size)
        assertEquals("PLANET-1", nodes[0].waypoint.symbol)
        assertEquals(1, nodes[0].orbitals.size)
        assertEquals("MOON-1", nodes[0].orbitals[0].waypoint.symbol)
    }

    @Test
    fun moonPromotesToRootWhenParentFiltered() = runTest {
        systemRepo.waypoints = listOf(
            fakeWaypoint("PLANET-1", type = WaypointType.PLANET),
            fakeWaypoint("MOON-1", type = WaypointType.MOON, orbits = "PLANET-1")
        )
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(SystemMapEvent.TypeFilterToggled(WaypointType.MOON))
        testDispatcher.scheduler.advanceUntilIdle()

        val nodes = vm.uiState.value.waypoints
        assertEquals(1, nodes.size)
        assertEquals("MOON-1", nodes[0].waypoint.symbol)
        assertTrue(nodes[0].orbitals.isEmpty())
    }

    // ── navigation action ───────────────────────────────────────────────────

    @Test
    fun navigateToWaypoint_setsActionResult() = runTest {
        val destWp = ShipNavRouteWaypoint("X1-DF55-17335A", WaypointType.PLANET, "X1-DF55", -21, -16)
        val originWp = ShipNavRouteWaypoint("X1-DF55-20250Z", WaypointType.MOON, "X1-DF55", 0, 0)
        navigateUseCase.result = NavigateResult(
            nav = ShipNav("X1-DF55", "X1-DF55-17335A", ShipNavStatus.IN_TRANSIT, ShipNavFlightMode.CRUISE,
                ShipNavRoute(originWp, destWp, NOW, NOW)),
            fuel = ShipFuel(350, 400)
        )
        fleetStore.put("LADD-1", fakeShip())
        systemRepo.waypoints = listOf(fakeWaypoint("X1-DF55-17335A"))
        val vm = createViewModel(shipSymbol = "LADD-1")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(SystemMapEvent.NavigateToWaypoint("X1-DF55-17335A"))
        testDispatcher.scheduler.advanceUntilIdle()

        val result = assertIs<SystemMapActionResult.NavigationStarted>(vm.uiState.value.actionResult)
        assertEquals("X1-DF55-17335A", result.destinationSymbol)
        assertEquals(350, result.fuelRemaining)
    }

    @Test
    fun navigateToWaypoint_error_setsError() = runTest {
        navigateUseCase.shouldThrow = true
        fleetStore.put("LADD-1", fakeShip())
        systemRepo.waypoints = listOf(fakeWaypoint("WP-1"))
        val vm = createViewModel(shipSymbol = "LADD-1")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(SystemMapEvent.NavigateToWaypoint("WP-1"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isActionInProgress)
    }

    @Test
    fun actionResultDismissed_clearsResult() = runTest {
        val wp = ShipNavRouteWaypoint("WP-1", WaypointType.PLANET, "X1-DF55", 0, 0)
        navigateUseCase.result = NavigateResult(
            nav = ShipNav("X1-DF55", "WP-1", ShipNavStatus.IN_TRANSIT, ShipNavFlightMode.CRUISE,
                ShipNavRoute(wp, wp, NOW, NOW)),
            fuel = ShipFuel(350, 400)
        )
        fleetStore.put("LADD-1", fakeShip())
        systemRepo.waypoints = listOf(fakeWaypoint("WP-1"))
        val vm = createViewModel(shipSymbol = "LADD-1")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(SystemMapEvent.NavigateToWaypoint("WP-1"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(vm.uiState.value.actionResult)

        vm.onEvent(SystemMapEvent.ActionResultDismissed)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.actionResult)
    }

    // ── focus waypoint ──────────────────────────────────────────────────────

    @Test
    fun focusWaypointSymbol_passedThroughToState() = runTest {
        systemRepo.waypoints = listOf(fakeWaypoint("WP-1"))
        val vm = createViewModel(focusWaypointSymbol = "WP-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("WP-1", vm.uiState.value.focusWaypointSymbol)
    }

    // ── ship snapshot ───────────────────────────────────────────────────────

    @Test
    fun selectedShip_populatedFromFleetStore() = runTest {
        fleetStore.put("LADD-1", fakeShip())
        systemRepo.waypoints = listOf(fakeWaypoint("WP-1"))
        val vm = createViewModel(shipSymbol = "LADD-1")
        testDispatcher.scheduler.advanceUntilIdle()

        val ship = assertNotNull(vm.uiState.value.selectedShip)
        assertEquals("LADD-1", ship.symbol)
        assertEquals(400, ship.fuelCurrent)
    }

    @Test
    fun noShipSymbol_selectedShipIsNull() = runTest {
        systemRepo.waypoints = listOf(fakeWaypoint("WP-1"))
        val vm = createViewModel(shipSymbol = null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.uiState.value.selectedShip)
    }
}
