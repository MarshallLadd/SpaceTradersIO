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
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.DockShipUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.OrbitShipUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RefuelShipUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

// Writes the ship to the store on getMyShip so the ViewModel's combine pipeline
// picks it up via FleetStateStore.observe(shipSymbol).
private class FakeDetailFleetRepository(
    private val store: FleetStateStore,
    var ship: Ship = fakeShip()
) : FleetRepository {
    override suspend fun getMyShips(page: Int, limit: Int) = listOf(ship)
    override suspend fun getMyShip(shipSymbol: String): Ship {
        store.put(shipSymbol, ship)
        return ship
    }
    override suspend fun refreshMyShips(page: Int, limit: Int) {}
}

// Fake use cases also update the store so that the ViewModel's observe() pipeline
// reflects the action result — mirroring what the real Impl classes do.
private class FakeOrbitUseCase(
    private val store: FleetStateStore,
    private val result: ShipNav = fakeNav(ShipNavStatus.IN_ORBIT)
) : OrbitShipUseCase {
    override suspend fun invoke(shipSymbol: String): ShipNav {
        store.update(shipSymbol) { it.copy(nav = result) }
        return result
    }
}

private class FakeDockUseCase(
    private val store: FleetStateStore,
    private val result: ShipNav = fakeNav(ShipNavStatus.DOCKED)
) : DockShipUseCase {
    override suspend fun invoke(shipSymbol: String): ShipNav {
        store.update(shipSymbol) { it.copy(nav = result) }
        return result
    }
}

private class FakeRefuelUseCase(
    private val store: FleetStateStore,
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
        store.update(shipSymbol) { it.copy(fuel = result.fuel) }
        return result
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShipDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var store: FleetStateStore
    private lateinit var repository: FakeDetailFleetRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        store = FleetStateStore()
        repository = FakeDetailFleetRepository(store)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        shipSymbol: String = "LADD-1",
        orbitUseCase: OrbitShipUseCase = FakeOrbitUseCase(store),
        dockUseCase: DockShipUseCase = FakeDockUseCase(store),
        refuelUseCase: RefuelShipUseCase = FakeRefuelUseCase(store)
    ) = ShipDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("shipSymbol" to shipSymbol)),
        fleetStateStore = store,
        fleetRepository = repository,
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
        val errorRepo = object : FleetRepository {
            override suspend fun getMyShips(page: Int, limit: Int) = emptyList<Ship>()
            override suspend fun getMyShip(shipSymbol: String): Ship = throw RuntimeException("Not found")
            override suspend fun refreshMyShips(page: Int, limit: Int) {}
        }
        val viewModel = ShipDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("shipSymbol" to "LADD-X")),
            fleetStateStore = store,
            fleetRepository = errorRepo,
            orbitShipUseCase = FakeOrbitUseCase(store),
            dockShipUseCase = FakeDockUseCase(store),
            refuelShipUseCase = FakeRefuelUseCase(store)
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
        val viewModel = createViewModel(orbitUseCase = FakeOrbitUseCase(store, fakeNav(ShipNavStatus.IN_ORBIT)))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(ShipDetailEvent.OrbitClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ShipNavStatus.IN_ORBIT, viewModel.uiState.value.ship?.navStatus)
    }

    @Test
    fun orbitClicked_setsOrbitedActionResult() = runTest {
        repository.ship = fakeShip(ShipNavStatus.DOCKED)
        val viewModel = createViewModel(orbitUseCase = FakeOrbitUseCase(store, fakeNav(ShipNavStatus.IN_ORBIT)))
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
        val viewModel = createViewModel(dockUseCase = FakeDockUseCase(store, fakeNav(ShipNavStatus.DOCKED)))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(ShipDetailEvent.DockClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ShipNavStatus.DOCKED, viewModel.uiState.value.ship?.navStatus)
    }

    @Test
    fun dockClicked_setsDockedActionResult() = runTest {
        repository.ship = fakeShip(ShipNavStatus.IN_ORBIT)
        val viewModel = createViewModel(dockUseCase = FakeDockUseCase(store, fakeNav(ShipNavStatus.DOCKED)))
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
                store,
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
}
