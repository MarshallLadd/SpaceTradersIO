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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
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
import kotlin.test.assertTrue
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

// In-memory FleetRepository backed by MutableStateFlow.
// refreshMyShips() populates the observable from `ships`; exception simulates failure.
private class FakeFleetRepository(
    initialShips: List<Ship> = listOf(fakeShip())
) : FleetRepository {
    private val _ships = MutableStateFlow<Map<String, Ship>>(emptyMap())
    var ships: List<Ship> = initialShips
    var exception: Exception? = null
    var refreshMyShipsCalled = false

    override fun observeShips(): Flow<List<Ship>> = _ships.map { it.values.toList() }
    override fun observeShip(shipSymbol: String): Flow<Ship?> = _ships.map { it[shipSymbol] }

    override suspend fun refreshMyShips(page: Int, limit: Int) {
        refreshMyShipsCalled = true
        exception?.let { throw it }
        _ships.value = ships.associateBy { it.symbol }
    }

    override suspend fun refreshMyShip(shipSymbol: String) { exception?.let { throw it } }
    override suspend fun saveShip(ship: Ship) { _ships.update { it + (ship.symbol to ship) } }
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

@OptIn(ExperimentalCoroutinesApi::class)
class ShipListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeFleetRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeFleetRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ShipListViewModel(repository)

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
    fun init_alwaysCallsRefreshMyShips() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(repository.refreshMyShipsCalled)
    }
}
