package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.CooldownDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.OrbitDockResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.PaginatedResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.RefuelResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipCargoDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipFrameSummaryDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipFuelDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavRouteDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavRouteWaypointDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.AgentDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MarketTransactionDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MetaDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipRegistrationDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

// Minimal ShipDto for use in fakes — all required fields, sensible defaults.
private fun minimalShipDto(
    symbol: String = "LADD-1",
    navStatus: String = "DOCKED"
) = ShipDto(
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
        status = navStatus,
        flightMode = "CRUISE"
    ),
    cargo = ShipCargoDto(capacity = 40, units = 0),
    fuel = ShipFuelDto(current = 400, capacity = 400),
    cooldown = CooldownDto(shipSymbol = symbol, totalSeconds = 0, remainingSeconds = 0)
)

private fun minimalNavDto(status: String = "IN_ORBIT") = ShipNavDto(
    systemSymbol = "X1-DF55",
    waypointSymbol = "X1-DF55-20250Z",
    route = ShipNavRouteDto(
        destination = ShipNavRouteWaypointDto("X1-DF55-20250Z", "MOON", "X1-DF55", 0, 0),
        origin = ShipNavRouteWaypointDto("X1-DF55-20250Z", "MOON", "X1-DF55", 0, 0),
        departureTime = "2025-06-01T10:00:00.000Z",
        arrival = "2025-06-01T10:00:00.000Z"
    ),
    status = status,
    flightMode = "CRUISE"
)

// Hand-written fake implementing FleetApi for repository tests.
private class FakeFleetApi(
    private val ships: List<ShipDto> = listOf(minimalShipDto()),
    private val singleShip: ShipDto = minimalShipDto(),
    private val orbitNav: ShipNavDto = minimalNavDto("IN_ORBIT"),
    private val dockNav: ShipNavDto = minimalNavDto("DOCKED")
) : FleetApi {

    var lastGetMyShipsPage: Int = -1
    var lastGetMyShipsLimit: Int = -1

    override suspend fun getMyShips(page: Int, limit: Int): PaginatedResponse<ShipDto> {
        lastGetMyShipsPage = page
        lastGetMyShipsLimit = limit
        return PaginatedResponse(data = ships, meta = MetaDto(total = ships.size, page = page, limit = limit))
    }

    override suspend fun getMyShip(shipSymbol: String): ShipDto = singleShip

    override suspend fun orbitShip(shipSymbol: String): ShipNavDto = orbitNav

    override suspend fun dockShip(shipSymbol: String): ShipNavDto = dockNav

    override suspend fun refuelShip(shipSymbol: String): RefuelResponseDto = RefuelResponseDto(
        agent = AgentDto(
            accountId = null, symbol = "LADD", headquarters = "X1-DF55-20250Z",
            credits = 148500L, startingFaction = "COSMIC", shipCount = 2
        ),
        fuel = ShipFuelDto(current = 400, capacity = 400),
        transaction = MarketTransactionDto(
            waypointSymbol = "X1-DF55-20250Z", shipSymbol = shipSymbol,
            tradeSymbol = "FUEL", type = "PURCHASE", units = 6,
            pricePerUnit = 75, totalPrice = 450, timestamp = "2025-06-01T10:00:00.000Z"
        )
    )
}

class FleetRepositoryImplTest {

    @Test
    fun getMyShips_returnsCorrectNumberOfShips() = runTest {
        val repo = FleetRepositoryImpl(FakeFleetApi(ships = listOf(minimalShipDto("LADD-1"), minimalShipDto("LADD-2"))))
        val result = repo.getMyShips(page = 1, limit = 20)
        assertEquals(2, result.size)
    }

    @Test
    fun getMyShips_mapsSymbolToDomain() = runTest {
        val repo = FleetRepositoryImpl(FakeFleetApi())
        val result = repo.getMyShips(page = 1, limit = 20)
        assertEquals("LADD-1", result.first().symbol)
    }

    @Test
    fun getMyShips_mapsNavStatusToDomain() = runTest {
        val repo = FleetRepositoryImpl(FakeFleetApi(ships = listOf(minimalShipDto(navStatus = "IN_ORBIT"))))
        val result = repo.getMyShips(page = 1, limit = 20)
        assertEquals(ShipNavStatus.IN_ORBIT, result.first().nav.status)
    }

    @Test
    fun getMyShips_forwardsPaginationParams() = runTest {
        val fake = FakeFleetApi()
        FleetRepositoryImpl(fake).getMyShips(page = 3, limit = 5)
        assertEquals(3, fake.lastGetMyShipsPage)
        assertEquals(5, fake.lastGetMyShipsLimit)
    }

    @Test
    fun getMyShip_mapsSymbolToDomain() = runTest {
        val repo = FleetRepositoryImpl(FakeFleetApi(singleShip = minimalShipDto("LADD-3")))
        val result = repo.getMyShip("LADD-3")
        assertEquals("LADD-3", result.symbol)
    }

    @Test
    fun getMyShip_mapsFuelCurrentToDomain() = runTest {
        val repo = FleetRepositoryImpl(FakeFleetApi())
        val result = repo.getMyShip("LADD-1")
        assertEquals(400, result.fuel.current)
    }
}
