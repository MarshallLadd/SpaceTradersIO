package com.brokenhuskysledteam.spacetradersio.ui.ships

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Cooldown
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.MarketTransaction
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.RefuelResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipCargo
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRoute
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRouteWaypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipRegistration
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavFlightMode
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipRole
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Waypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.WaypointTrait
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.SystemRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.DockShipUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.OrbitShipUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RefuelShipUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import androidx.lifecycle.SavedStateHandle
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

private fun fakeWaypoint(symbol: String = "X1-DF55-20250Z") =
    ShipNavRouteWaypoint(symbol = symbol, type = WaypointType.MOON, systemSymbol = "X1-DF55", x = 0, y = 0)

private fun fakeNav(status: ShipNavStatus = ShipNavStatus.DOCKED) = ShipNav(
    systemSymbol = "X1-DF55",
    waypointSymbol = "X1-DF55-20250Z",
    status = status,
    flightMode = ShipNavFlightMode.CRUISE,
    route = ShipNavRoute(
        origin = fakeWaypoint(),
        destination = fakeWaypoint("X1-DF55-17335A"),
        departureTime = NOW,
        arrivalTime = NOW
    )
)

private fun fakeShip(status: ShipNavStatus = ShipNavStatus.DOCKED) = Ship(
    symbol = "LADD-1",
    registration = ShipRegistration(role = ShipRole.COMMAND, factionSymbol = "COSMIC"),
    nav = fakeNav(status),
    cargo = ShipCargo(units = 5, capacity = 40),
    fuel = ShipFuel(current = 340, capacity = 400),
    frameName = "Shuttle Frame",
    cooldown = Cooldown(shipSymbol = "LADD-1", totalSeconds = 0, remainingSeconds = 0, expiration = null)
)

// In-memory FleetRepository backed by MutableStateFlow.
// refreshMyShip() stores the configured ship so the ViewModel's combine pipeline
// picks it up via observeShip(). Fake use cases call updateShipNav/Fuel to reflect
// action side-effects, just like the real impls do.
private class FakeDetailFleetRepository(
    var ship: Ship = fakeShip(),
    var exception: Exception? = null
) : FleetRepository {
    private val _ships = MutableStateFlow<Map<String, Ship>>(emptyMap())

    override fun observeShips(): Flow<List<Ship>> = _ships.map { it.values.toList() }
    override fun observeShip(shipSymbol: String): Flow<Ship?> = _ships.map { it[shipSymbol] }

    override suspend fun refreshMyShips(page: Int, limit: Int) {}
    override suspend fun refreshMyShip(shipSymbol: String) {
        exception?.let { throw it }
        _ships.update { it + (shipSymbol to ship) }
    }

    override suspend fun saveShip(s: Ship) { _ships.update { it + (s.symbol to s) } }
    override suspend fun updateShipNav(shipSymbol: String, nav: ShipNav) {
        _ships.update { m -> m[shipSymbol]?.let { m + (shipSymbol to it.copy(nav = nav)) } ?: m }
    }
    override suspend fun updateShipFuel(shipSymbol: String, fuel: ShipFuel) {
        _ships.update { m -> m[shipSymbol]?.let { m + (shipSymbol to it.copy(fuel = fuel)) } ?: m }
    }
    override suspend fun updateShipCargo(shipSymbol: String, cargo: ShipCargo) {
        _ships.update { m -> m[shipSymbol]?.let { m + (shipSymbol to it.copy(cargo = cargo)) } ?: m }
    }
    override suspend fun updateShipCooldown(shipSymbol: String, cooldown: Cooldown) {
        _ships.update { m -> m[shipSymbol]?.let { m + (shipSymbol to it.copy(cooldown = cooldown)) } ?: m }
    }
    override suspend fun clearAll() { _ships.value = emptyMap() }
}

// Fake use cases call the repo's update methods to reflect action side-effects,
// mirroring what the real Impl classes do.
private class FakeOrbitUseCase(
    private val repo: FakeDetailFleetRepository,
    private val result: ShipNav = fakeNav(ShipNavStatus.IN_ORBIT)
) : OrbitShipUseCase {
    override suspend fun invoke(shipSymbol: String): ShipNav {
        repo.updateShipNav(shipSymbol, result)
        return result
    }
}

private class FakeDockUseCase(
    private val repo: FakeDetailFleetRepository,
    private val result: ShipNav = fakeNav(ShipNavStatus.DOCKED)
) : DockShipUseCase {
    override suspend fun invoke(shipSymbol: String): ShipNav {
        repo.updateShipNav(shipSymbol, result)
        return result
    }
}

private class FakeSystemRepository(
    private val traits: List<WaypointTrait> = emptyList()
) : SystemRepository {
    override suspend fun getSystemWaypoints(systemSymbol: String): List<Waypoint> = emptyList()
    override suspend fun getWaypoint(systemSymbol: String, waypointSymbol: String): Waypoint =
        Waypoint(
            symbol = waypointSymbol, type = WaypointType.MOON, systemSymbol = systemSymbol,
            x = 0, y = 0, orbits = null, orbitals = emptyList(),
            traits = traits, isUnderConstruction = false
        )
    override fun observeWaypoint(waypointSymbol: String): Flow<Waypoint?> = flowOf(null)
}

private class FakeRefuelUseCase(
    private val repo: FakeDetailFleetRepository,
    private val result: RefuelResult = RefuelResult(
        agent = Agent(
            accountId = null, symbol = "LADD", headquarters = "X1-DF55-20250Z",
            credits = 148500L, startingFaction = "COSMIC", shipCount = 2
        ),
        fuel = ShipFuel(current = 400, capacity = 400),
        transaction = MarketTransaction(
            waypointSymbol = "X1-DF55-20250Z", shipSymbol = "LADD-1",
            tradeSymbol = "FUEL", type = "PURCHASE", units = 6,
            pricePerUnit = 75, totalPrice = 450, timestamp = NOW
        )
    )
) : RefuelShipUseCase {
    override suspend fun invoke(shipSymbol: String): RefuelResult {
        repo.updateShipFuel(shipSymbol, result.fuel)
        return result
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShipDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeDetailFleetRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeDetailFleetRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        shipSymbol: String = "LADD-1",
        systemRepository: SystemRepository = FakeSystemRepository(),
        orbitUseCase: OrbitShipUseCase = FakeOrbitUseCase(repository),
        dockUseCase: DockShipUseCase = FakeDockUseCase(repository),
        refuelUseCase: RefuelShipUseCase = FakeRefuelUseCase(repository)
    ) = ShipDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("shipSymbol" to shipSymbol)),
        fleetRepository = repository,
        systemRepository = systemRepository,
        orbitShipUseCase = orbitUseCase,
        dockShipUseCase = dockUseCase,
        refuelShipUseCase = refuelUseCase
    )

    // ── initial load ──────────────────────────────────────────────────────────

    @Test
    fun init_loadsShip_setsShipDetail() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals("LADD-1", assertNotNull(state.ship).symbol)
    }

    @Test
    fun init_mapsFrameNameToDetail() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Shuttle Frame", viewModel.uiState.value.ship?.frameName)
    }

    @Test
    fun init_mapsFuelToDetail() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val ship = assertNotNull(viewModel.uiState.value.ship)
        assertEquals(340, ship.fuelCurrent)
        assertEquals(400, ship.fuelCapacity)
    }

    @Test
    fun init_mapsOriginToDetail() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val ship = assertNotNull(viewModel.uiState.value.ship)
        assertEquals("X1-DF55-20250Z", ship.originSymbol)
        assertEquals(WaypointType.MOON, ship.originType)
    }

    @Test
    fun init_originSymbolComesFromRoute_notWaypointSymbol() = runTest {
        repository.ship = fakeShip().let { ship ->
            ship.copy(
                nav = ship.nav.copy(
                    waypointSymbol = "CURRENT-LOCATION",
                    route = ship.nav.route.copy(
                        origin = ShipNavRouteWaypoint(
                            symbol = "DEPARTURE-POINT",
                            type = WaypointType.ORBITAL_STATION,
                            systemSymbol = "X1-DF55",
                            x = 10,
                            y = 20
                        )
                    )
                )
            )
        }
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val ship = assertNotNull(viewModel.uiState.value.ship)
        assertEquals("CURRENT-LOCATION", ship.waypointSymbol)
        assertEquals("DEPARTURE-POINT", ship.originSymbol)
        assertEquals(WaypointType.ORBITAL_STATION, ship.originType)
    }

    @Test
    fun init_loadsShip_error_setsErrorMessage() = runTest {
        val errorRepo = FakeDetailFleetRepository(exception = RuntimeException("Not found"))
        val viewModel = ShipDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("shipSymbol" to "LADD-X")),
            fleetRepository = errorRepo,
            systemRepository = FakeSystemRepository(),
            orbitShipUseCase = FakeOrbitUseCase(errorRepo),
            dockShipUseCase = FakeDockUseCase(errorRepo),
            refuelShipUseCase = FakeRefuelUseCase(errorRepo)
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
    }

    // ── orbit action ──────────────────────────────────────────────────────────

    @Test
    fun orbitClicked_updatesNavStatusToInOrbit() = runTest {
        repository.ship = fakeShip(ShipNavStatus.DOCKED)
        val viewModel = createViewModel(orbitUseCase = FakeOrbitUseCase(repository, fakeNav(ShipNavStatus.IN_ORBIT)))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(ShipDetailEvent.OrbitClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ShipNavStatus.IN_ORBIT, viewModel.uiState.value.ship?.navStatus)
    }

    @Test
    fun orbitClicked_setsOrbitedActionResult() = runTest {
        repository.ship = fakeShip(ShipNavStatus.DOCKED)
        val viewModel = createViewModel(orbitUseCase = FakeOrbitUseCase(repository, fakeNav(ShipNavStatus.IN_ORBIT)))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(ShipDetailEvent.OrbitClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertIs<ActionResult.Orbited>(viewModel.uiState.value.actionResult)
    }

    @Test
    fun orbitClicked_clearsIsActionInProgress() = runTest {
        repository.ship = fakeShip(ShipNavStatus.DOCKED)
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(ShipDetailEvent.OrbitClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isActionInProgress)
    }

    // ── dock action ───────────────────────────────────────────────────────────

    @Test
    fun dockClicked_updatesNavStatusToDocked() = runTest {
        repository.ship = fakeShip(ShipNavStatus.IN_ORBIT)
        val viewModel = createViewModel(dockUseCase = FakeDockUseCase(repository, fakeNav(ShipNavStatus.DOCKED)))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(ShipDetailEvent.DockClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ShipNavStatus.DOCKED, viewModel.uiState.value.ship?.navStatus)
    }

    @Test
    fun dockClicked_setsDockedActionResult() = runTest {
        repository.ship = fakeShip(ShipNavStatus.IN_ORBIT)
        val viewModel = createViewModel(dockUseCase = FakeDockUseCase(repository, fakeNav(ShipNavStatus.DOCKED)))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(ShipDetailEvent.DockClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertIs<ActionResult.Docked>(viewModel.uiState.value.actionResult)
    }

    // ── refuel action ─────────────────────────────────────────────────────────

    @Test
    fun refuelClicked_updatesFuelInState() = runTest {
        repository.ship = fakeShip(ShipNavStatus.DOCKED)
        val viewModel = createViewModel(
            refuelUseCase = FakeRefuelUseCase(
                repository,
                RefuelResult(
                    agent = Agent(null, "LADD", "X1-DF55-20250Z", 148500L, "COSMIC", 2),
                    fuel = ShipFuel(current = 400, capacity = 400),
                    transaction = MarketTransaction("X1-DF55-20250Z", "LADD-1", "FUEL", "PURCHASE", 6, 75, 450, NOW)
                )
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(ShipDetailEvent.RefuelClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(400, viewModel.uiState.value.ship?.fuelCurrent)
    }

    @Test
    fun refuelClicked_setsRefueledActionResult() = runTest {
        repository.ship = fakeShip(ShipNavStatus.DOCKED)
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(ShipDetailEvent.RefuelClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertIs<ActionResult.Refueled>(viewModel.uiState.value.actionResult)
    }

    @Test
    fun refuelClicked_actionResult_containsCorrectCost() = runTest {
        repository.ship = fakeShip(ShipNavStatus.DOCKED)
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(ShipDetailEvent.RefuelClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        val result = assertIs<ActionResult.Refueled>(viewModel.uiState.value.actionResult)
        assertEquals(450, result.totalCost)
    }

    @Test
    fun refuelClicked_actionResult_containsNewCredits() = runTest {
        repository.ship = fakeShip(ShipNavStatus.DOCKED)
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(ShipDetailEvent.RefuelClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        val result = assertIs<ActionResult.Refueled>(viewModel.uiState.value.actionResult)
        assertEquals(148500L, result.newCredits)
    }

    // ── actionResultDismissed ─────────────────────────────────────────────────

    @Test
    fun actionResultDismissed_clearsActionResult() = runTest {
        repository.ship = fakeShip(ShipNavStatus.DOCKED)
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(ShipDetailEvent.OrbitClicked)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.actionResult)

        viewModel.onEvent(ShipDetailEvent.ActionResultDismissed)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.actionResult)
    }

    // ── hasShipyard ───────────────────────────────────────────────────────────

    @Test
    fun hasShipyard_waypointHasShipyardTrait_isTrue() = runTest {
        val viewModel = createViewModel(
            systemRepository = FakeSystemRepository(
                traits = listOf(WaypointTrait(WaypointTraitSymbol.SHIPYARD, "Shipyard", "Buy ships here."))
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasShipyard)
    }

    @Test
    fun hasShipyard_waypointHasNoShipyardTrait_isFalse() = runTest {
        val viewModel = createViewModel(systemRepository = FakeSystemRepository(traits = emptyList()))
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.hasShipyard)
    }

    @Test
    fun onEvent_viewShipyardClicked_isNoOpInViewModel() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val stateBefore = viewModel.uiState.value
        viewModel.onEvent(ShipDetailEvent.ViewShipyardClicked("X1-DF55", "X1-DF55-20250Z"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(stateBefore.ship, viewModel.uiState.value.ship)
        assertEquals(stateBefore.hasShipyard, viewModel.uiState.value.hasShipyard)
    }
}
