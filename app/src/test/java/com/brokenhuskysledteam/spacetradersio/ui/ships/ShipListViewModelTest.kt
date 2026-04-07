package com.brokenhuskysledteam.spacetradersio.ui.ships

import app.cash.turbine.test
import com.brokenhuskysledteam.spacetradersio.navigation.NavigationTarget
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Cooldown
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

private val NOW = Instant.parse("2025-06-01T10:00:00.000Z")
private val LATER = Instant.parse("2025-06-01T10:30:00.000Z")

private fun fakeWaypoint(symbol: String = "X1-DF55-20250Z", type: WaypointType = WaypointType.MOON) =
    ShipNavRouteWaypoint(symbol = symbol, type = type, systemSymbol = "X1-DF55", x = 0, y = 0)

private fun fakeRoute(departure: Instant = NOW, arrival: Instant = LATER) = ShipNavRoute(
    origin = fakeWaypoint(),
    destination = fakeWaypoint("X1-DF55-17335A", WaypointType.PLANET),
    departureTime = departure,
    arrivalTime = arrival
)

private fun fakeNav(
    status: ShipNavStatus = ShipNavStatus.DOCKED,
    waypoint: String = "X1-DF55-20250Z"
) = ShipNav(
    systemSymbol = "X1-DF55",
    waypointSymbol = waypoint,
    status = status,
    flightMode = ShipNavFlightMode.CRUISE,
    route = fakeRoute()
)

private fun fakeShip(
    symbol: String = "LADD-1",
    status: ShipNavStatus = ShipNavStatus.DOCKED
) = Ship(
    symbol = symbol,
    registration = ShipRegistration(role = ShipRole.COMMAND, factionSymbol = "COSMIC"),
    nav = fakeNav(status),
    cargo = ShipCargo(units = 0, capacity = 40),
    fuel = ShipFuel(current = 400, capacity = 400),
    frameName = "Shuttle Frame",
    cooldown = Cooldown(shipSymbol = symbol, totalSeconds = 0, remainingSeconds = 0, expiration = null)
)

// Writes ships to the store in refreshMyShips so the ViewModel's combine pipeline
// reflects the loaded data via FleetStateStore.entities.
private class FakeFleetRepository(
    private val store: FleetStateStore,
    var ships: List<Ship> = listOf(fakeShip()),
    var exception: Exception? = null,
    var singleShip: Ship = fakeShip()
) : FleetRepository {
    override suspend fun getMyShips(page: Int, limit: Int): List<Ship> {
        exception?.let { throw it }
        return ships
    }

    override suspend fun getMyShip(shipSymbol: String): Ship {
        exception?.let { throw it }
        store.put(shipSymbol, singleShip)
        return singleShip
    }

    override suspend fun refreshMyShips(page: Int, limit: Int) {
        exception?.let { throw it }
        store.putAll(ships.associateBy { it.symbol })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShipListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var store: FleetStateStore
    private lateinit var repository: FakeFleetRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        store = FleetStateStore()
        repository = FakeFleetRepository(store)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ShipListViewModel(store, repository)

    @Test
    fun init_loadsShipsSuccess_setsShips() = runTest {
        repository.ships = listOf(fakeShip("LADD-1"), fakeShip("LADD-2"))
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(2, state.ships.size)
    }

    @Test
    fun init_mapsSymbolToShipSummary() = runTest {
        repository.ships = listOf(fakeShip("LADD-1"))
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("LADD-1", viewModel.uiState.value.ships.first().symbol)
    }

    @Test
    fun init_mapsFrameNameToShipSummary() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Shuttle Frame", viewModel.uiState.value.ships.first().frameName)
    }

    @Test
    fun init_mapsNavStatusToShipSummary() = runTest {
        repository.ships = listOf(fakeShip(status = ShipNavStatus.IN_ORBIT))
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ShipNavStatus.IN_ORBIT, viewModel.uiState.value.ships.first().status)
    }

    @Test
    fun init_mapsArrivalTimeForInTransitShip() = runTest {
        repository.ships = listOf(fakeShip(status = ShipNavStatus.IN_TRANSIT))
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LATER, viewModel.uiState.value.ships.first().arrivalTime)
    }

    @Test
    fun init_setsDepartureTimeForInTransitShip() = runTest {
        repository.ships = listOf(fakeShip(status = ShipNavStatus.IN_TRANSIT))
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(NOW, viewModel.uiState.value.ships.first().departureTime)
    }

    @Test
    fun init_loadsShips_error_setsErrorMessage() = runTest {
        repository.exception = RuntimeException("Network failure")
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Network failure", state.error)
        assert(state.ships.isEmpty())
    }

    @Test
    fun retryClicked_reloadsShips() = runTest {
        repository.exception = RuntimeException("Fail")
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)

        repository.exception = null
        repository.ships = listOf(fakeShip("LADD-1"))
        viewModel.onEvent(ShipListEvent.RetryClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.error)
        assertEquals(1, state.ships.size)
    }

    @Test
    fun shipSelected_sendsNavigationEventWithSymbol() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigationEvent.test {
            viewModel.onEvent(ShipListEvent.ShipSelected("LADD-1"))
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(NavigationTarget.ShipDetail("LADD-1"), awaitItem())
        }
    }

    @Test
    fun storeAlreadyPopulated_doesNotCallRefreshMyShips() = runTest {
        store.put("LADD-1", fakeShip("LADD-1"))
        repository.exception = RuntimeException("Should not be called")
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // No error — refreshMyShips was skipped because store was not empty
        assertNull(viewModel.uiState.value.error)
        assertEquals(1, viewModel.uiState.value.ships.size)
    }
}
