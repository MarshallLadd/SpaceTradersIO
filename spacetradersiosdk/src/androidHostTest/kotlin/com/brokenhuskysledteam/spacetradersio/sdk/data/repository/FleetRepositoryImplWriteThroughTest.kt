package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.AgentDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.CooldownDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MarketTransactionDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MetaDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.PaginatedResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.RefuelResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipCargoDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipFrameSummaryDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipFuelDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavRouteDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavRouteWaypointDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipRegistrationDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Arrival time is far in the future so the registered timer never fires during tests,
// preventing the action from recursively re-scheduling itself indefinitely.
private fun transitShipDto(symbol: String = "LADD-1") = ShipDto(
    symbol = symbol,
    registration = ShipRegistrationDto(name = symbol, factionSymbol = "COSMIC", role = "COMMAND"),
    frame = ShipFrameSummaryDto(symbol = "FRAME_SHUTTLE", name = "Shuttle Frame"),
    nav = ShipNavDto(
        systemSymbol = "X1-DF55",
        waypointSymbol = "X1-DF55-20250Z",
        route = ShipNavRouteDto(
            destination = ShipNavRouteWaypointDto("X1-DF55-30A", "PLANET", "X1-DF55", 10, 20),
            origin = ShipNavRouteWaypointDto("X1-DF55-20250Z", "MOON", "X1-DF55", 0, 0),
            departureTime = "2099-01-01T00:00:00.000Z",
            arrival = "2099-01-01T01:00:00.000Z"
        ),
        status = "IN_TRANSIT",
        flightMode = "CRUISE"
    ),
    cargo = ShipCargoDto(capacity = 40, units = 0),
    fuel = ShipFuelDto(current = 400, capacity = 400),
    cooldown = CooldownDto(shipSymbol = symbol, totalSeconds = 0, remainingSeconds = 0)
)

private fun dockedShipDto(symbol: String = "LADD-2") = ShipDto(
    symbol = symbol,
    registration = ShipRegistrationDto(name = symbol, factionSymbol = "COSMIC", role = "COMMAND"),
    frame = ShipFrameSummaryDto(symbol = "FRAME_SHUTTLE", name = "Shuttle Frame"),
    nav = ShipNavDto(
        systemSymbol = "X1-DF55",
        waypointSymbol = "X1-DF55-20250Z",
        route = ShipNavRouteDto(
            destination = ShipNavRouteWaypointDto("X1-DF55-20250Z", "MOON", "X1-DF55", 0, 0),
            origin = ShipNavRouteWaypointDto("X1-DF55-20250Z", "MOON", "X1-DF55", 0, 0),
            departureTime = "2025-06-01T10:00:00.000Z",
            arrival = "2025-06-01T10:00:00.000Z"
        ),
        status = "DOCKED",
        flightMode = "CRUISE"
    ),
    cargo = ShipCargoDto(capacity = 40, units = 0),
    fuel = ShipFuelDto(current = 400, capacity = 400),
    cooldown = CooldownDto(shipSymbol = symbol, totalSeconds = 0, remainingSeconds = 0)
)

private class FakeWriteThroughFleetApi(
    private val ships: List<ShipDto> = emptyList(),
    private val singleShip: ShipDto = dockedShipDto()
) : FleetApi {
    override suspend fun getMyShips(page: Int, limit: Int): PaginatedResponse<ShipDto> =
        PaginatedResponse(data = ships, meta = MetaDto(total = ships.size, page = page, limit = limit))

    override suspend fun getMyShip(shipSymbol: String): ShipDto = singleShip

    override suspend fun orbitShip(shipSymbol: String): ShipNavDto = singleShip.nav

    override suspend fun dockShip(shipSymbol: String): ShipNavDto = singleShip.nav

    override suspend fun refuelShip(shipSymbol: String): RefuelResponseDto = RefuelResponseDto(
        agent = AgentDto(null, "LADD", "X1-DF55-20250Z", 100000L, "COSMIC", 2),
        fuel = ShipFuelDto(current = 400, capacity = 400),
        transaction = MarketTransactionDto("X1-DF55-20250Z", shipSymbol, "FUEL", "PURCHASE", 6, 75, 450, "2025-06-01T10:00:00.000Z")
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class FleetRepositoryImplWriteThroughTest {

    @Test
    fun getMyShipWritesToStore() = runTest {
        val store = FleetStateStore()
        // backgroundScope: scheduler's loop coroutine runs in background so test can complete
        // even if a timer is pending. Applies to all write-through tests.
        val scheduler = RefreshScheduler(backgroundScope)
        val repo = FleetRepositoryImpl(FakeWriteThroughFleetApi(singleShip = dockedShipDto("LADD-2")), store, scheduler)

        repo.getMyShip("LADD-2")
        assertEquals("LADD-2", store.entities.value["LADD-2"]?.symbol)
    }

    @Test
    fun refreshMyShipsWritesAllToStore() = runTest {
        val store = FleetStateStore()
        val scheduler = RefreshScheduler(backgroundScope)
        val ships = listOf(dockedShipDto("LADD-1"), dockedShipDto("LADD-2"))
        val repo = FleetRepositoryImpl(FakeWriteThroughFleetApi(ships = ships), store, scheduler)

        repo.refreshMyShips()
        assertEquals(2, store.entities.value.size)
    }

    @Test
    fun getMyShipRegistersTransitTimer() = runTest {
        val store = FleetStateStore()
        val scheduler = RefreshScheduler(backgroundScope)
        val repo = FleetRepositoryImpl(
            FakeWriteThroughFleetApi(singleShip = transitShipDto("LADD-1")),
            store, scheduler
        )

        repo.getMyShip("LADD-1")
        assertTrue(scheduler.activeTimers.value.containsKey("transit:LADD-1"))
    }

    @Test
    fun getMyShipDoesNotRegisterTimerForDockedShip() = runTest {
        val store = FleetStateStore()
        val scheduler = RefreshScheduler(backgroundScope)
        val repo = FleetRepositoryImpl(FakeWriteThroughFleetApi(singleShip = dockedShipDto()), store, scheduler)

        repo.getMyShip("LADD-2")
        assertTrue(scheduler.activeTimers.value.isEmpty())
    }
}
