package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.AgentDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.CooldownDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MarketTransactionDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MetaDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.NavigateResponseDto
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
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Cooldown
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipCargo
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRoute
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRouteWaypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavFlightMode
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.testing.createTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

internal fun minimalShipDto(
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
            arrival = "2099-01-01T01:00:00.000Z"
        ),
        status = navStatus,
        flightMode = "CRUISE"
    ),
    cargo = ShipCargoDto(capacity = 40, units = 0),
    fuel = ShipFuelDto(current = 400, capacity = 400),
    cooldown = CooldownDto(shipSymbol = symbol, totalSeconds = 0, remainingSeconds = 0)
)

internal class FakeFleetApi(
    private val ships: List<ShipDto> = listOf(minimalShipDto()),
    val singleShip: ShipDto = minimalShipDto()
) : FleetApi {
    var lastGetMyShipsPage: Int = -1
    var lastGetMyShipsLimit: Int = -1

    override suspend fun getMyShips(page: Int, limit: Int): PaginatedResponse<ShipDto> {
        lastGetMyShipsPage = page
        lastGetMyShipsLimit = limit
        return PaginatedResponse(data = ships, meta = MetaDto(total = ships.size, page = page, limit = limit))
    }
    override suspend fun getMyShip(shipSymbol: String): ShipDto = singleShip
    override suspend fun orbitShip(shipSymbol: String): ShipNavDto = singleShip.nav
    override suspend fun dockShip(shipSymbol: String): ShipNavDto = singleShip.nav
    override suspend fun refuelShip(shipSymbol: String): RefuelResponseDto = RefuelResponseDto(
        agent = AgentDto(null, "LADD", "X1-DF55-20250Z", 148500L, "COSMIC", 2),
        fuel = ShipFuelDto(current = 400, capacity = 400),
        transaction = MarketTransactionDto("X1-DF55-20250Z", shipSymbol, "FUEL", "PURCHASE", 6, 75, 450, "2025-06-01T10:00:00.000Z")
    )
    override suspend fun navigateShip(shipSymbol: String, waypointSymbol: String): NavigateResponseDto =
        NavigateResponseDto(nav = singleShip.nav, fuel = ShipFuelDto(current = 400, capacity = 400))
}

class FleetRepositoryImplTest {

    @Test
    fun observeShips_emptyDb_emitsEmptyList() = runTest {
        val repo = FleetRepositoryImpl(FakeFleetApi(), createTestDatabase(), RefreshScheduler(backgroundScope))
        val result = repo.observeShips().first()
        assertEquals(emptyList(), result)
    }

    @Test
    fun refreshMyShips_writesToDb_observeEmitsShips() = runTest {
        val db = createTestDatabase()
        val api = FakeFleetApi(ships = listOf(minimalShipDto("LADD-1"), minimalShipDto("LADD-2")))
        val repo = FleetRepositoryImpl(api, db, RefreshScheduler(backgroundScope))
        repo.refreshMyShips()
        val result = repo.observeShips().first()
        assertEquals(2, result.size)
    }

    @Test
    fun refreshMyShips_mapsSymbolCorrectly() = runTest {
        val db = createTestDatabase()
        val repo = FleetRepositoryImpl(FakeFleetApi(), db, RefreshScheduler(backgroundScope))
        repo.refreshMyShips()
        val result = repo.observeShips().first()
        assertEquals("LADD-1", result.first().symbol)
    }

    @Test
    fun refreshMyShips_forwardsPaginationParams() = runTest {
        val fake = FakeFleetApi()
        val repo = FleetRepositoryImpl(fake, createTestDatabase(), RefreshScheduler(backgroundScope))
        repo.refreshMyShips(page = 3, limit = 5)
        assertEquals(3, fake.lastGetMyShipsPage)
        assertEquals(5, fake.lastGetMyShipsLimit)
    }

    @Test
    fun observeShip_afterRefresh_emitsCorrectShip() = runTest {
        val db = createTestDatabase()
        val repo = FleetRepositoryImpl(FakeFleetApi(), db, RefreshScheduler(backgroundScope))
        repo.refreshMyShip("LADD-1")
        val result = repo.observeShip("LADD-1").first()
        assertEquals("LADD-1", result?.symbol)
    }

    @Test
    fun observeShip_unknownSymbol_emitsNull() = runTest {
        val repo = FleetRepositoryImpl(FakeFleetApi(), createTestDatabase(), RefreshScheduler(backgroundScope))
        val result = repo.observeShip("UNKNOWN").first()
        assertNull(result)
    }

    @Test
    fun updateShipNav_changesNavFields() = runTest {
        val db = createTestDatabase()
        val repo = FleetRepositoryImpl(FakeFleetApi(), db, RefreshScheduler(backgroundScope))
        repo.refreshMyShip("LADD-1")
        val newNav = ShipNav(
            "X1-DF55", "X1-DF55-20250Z", ShipNavStatus.IN_ORBIT, ShipNavFlightMode.CRUISE,
            ShipNavRoute(
                ShipNavRouteWaypoint("X1-DF55-20250Z", WaypointType.MOON, "X1-DF55", 0, 0),
                ShipNavRouteWaypoint("X1-DF55-20250Z", WaypointType.MOON, "X1-DF55", 0, 0),
                Instant.parse("2025-06-01T10:00:00Z"), Instant.parse("2099-01-01T01:00:00Z")
            )
        )
        repo.updateShipNav("LADD-1", newNav)
        val result = repo.observeShip("LADD-1").first()
        assertEquals(ShipNavStatus.IN_ORBIT, result?.nav?.status)
        assertEquals(400, result?.fuel?.current) // fuel unchanged
    }

    @Test
    fun updateShipFuel_changesFuelFields() = runTest {
        val db = createTestDatabase()
        val repo = FleetRepositoryImpl(FakeFleetApi(), db, RefreshScheduler(backgroundScope))
        repo.refreshMyShip("LADD-1")
        repo.updateShipFuel("LADD-1", ShipFuel(200, 400))
        val result = repo.observeShip("LADD-1").first()
        assertEquals(200, result?.fuel?.current)
        assertEquals(ShipNavStatus.DOCKED, result?.nav?.status) // nav unchanged
    }

    @Test
    fun clearAll_emptiesTable() = runTest {
        val db = createTestDatabase()
        val repo = FleetRepositoryImpl(FakeFleetApi(), db, RefreshScheduler(backgroundScope))
        repo.refreshMyShips()
        repo.clearAll()
        val result = repo.observeShips().first()
        assertEquals(emptyList(), result)
    }

    @Test
    fun updateShipCargo_changesCargoFields() = runTest {
        val db = createTestDatabase()
        val repo = FleetRepositoryImpl(FakeFleetApi(), db, RefreshScheduler(backgroundScope))
        repo.refreshMyShip("LADD-1")
        repo.updateShipCargo("LADD-1", ShipCargo(units = 20, capacity = 40))
        val result = repo.observeShip("LADD-1").first()
        assertEquals(20, result?.cargo?.units)
        assertEquals(ShipNavStatus.DOCKED, result?.nav?.status) // nav unchanged
    }

    @Test
    fun updateShipCooldown_setsCooldownFields() = runTest {
        val db = createTestDatabase()
        val repo = FleetRepositoryImpl(FakeFleetApi(), db, RefreshScheduler(backgroundScope))
        repo.refreshMyShip("LADD-1")
        val expiry = Instant.parse("2099-01-01T01:00:00Z")
        repo.updateShipCooldown("LADD-1", Cooldown("LADD-1", 60, 30, expiry))
        val result = repo.observeShip("LADD-1").first()
        assertEquals(60, result?.cooldown?.totalSeconds)
        assertEquals(expiry, result?.cooldown?.expiration)
    }

    @Test
    fun updateShipCooldown_nullExpiration_clearsExpiry() = runTest {
        val db = createTestDatabase()
        val repo = FleetRepositoryImpl(FakeFleetApi(), db, RefreshScheduler(backgroundScope))
        repo.refreshMyShip("LADD-1")
        repo.updateShipCooldown("LADD-1", Cooldown("LADD-1", 0, 0, null))
        val result = repo.observeShip("LADD-1").first()
        assertNull(result?.cooldown?.expiration)
    }
}
