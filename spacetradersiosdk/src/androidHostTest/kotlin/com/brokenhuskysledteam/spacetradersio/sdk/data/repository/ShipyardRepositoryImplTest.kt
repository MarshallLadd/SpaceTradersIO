package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.AgentDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.PurchaseShipResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipyardCrewDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipyardDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipyardEngineSummaryDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipyardFrameSummaryDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipyardReactorSummaryDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipyardShipDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipyardTransactionDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AgentsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ShipyardApi
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.testing.createTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun minimalShipyardShipDto(type: String = "SHIP_MINING_DRONE") = ShipyardShipDto(
    type = type,
    name = "Mining Drone",
    description = "Mines ore.",
    purchasePrice = 50000,
    supply = "MODERATE",
    frame = ShipyardFrameSummaryDto(name = "Drone Frame"),
    engine = ShipyardEngineSummaryDto(speed = 10),
    reactor = ShipyardReactorSummaryDto(powerOutput = 3),
    crew = ShipyardCrewDto(required = 0, capacity = 0)
)

private class FakeShipyardApi(
    var shipyardResult: ShipyardDto = ShipyardDto(
        symbol = "X1-DF55-20250Z",
        modificationsFee = 1000,
        ships = listOf(minimalShipyardShipDto())
    ),
    var purchaseResult: PurchaseShipResponseDto = PurchaseShipResponseDto(
        ship = minimalShipDto("LADD-2"),
        agent = AgentDto(null, "LADD", "X1-DF55-20250Z", 100000L, "COSMIC", 2),
        transaction = ShipyardTransactionDto("X1-DF55-20250Z", "SHIP_MINING_DRONE", 50000, "LADD", "2025-06-01T10:00:00.000Z")
    ),
    var exception: Exception? = null
) : ShipyardApi {
    var lastGetSystemSymbol: String? = null
    var lastGetWaypointSymbol: String? = null
    var lastPurchaseShipType: String? = null
    var lastPurchaseWaypointSymbol: String? = null

    override suspend fun getShipyard(systemSymbol: String, waypointSymbol: String): ShipyardDto {
        exception?.let { throw it }
        lastGetSystemSymbol = systemSymbol
        lastGetWaypointSymbol = waypointSymbol
        return shipyardResult
    }

    override suspend fun purchaseShip(shipType: String, waypointSymbol: String): PurchaseShipResponseDto {
        exception?.let { throw it }
        lastPurchaseShipType = shipType
        lastPurchaseWaypointSymbol = waypointSymbol
        return purchaseResult
    }
}

private class FakeAgentsApiForShipyard(
    var agentResult: AgentDto = AgentDto(
        accountId = "acc-1",
        symbol = "LADD",
        headquarters = "X1-DF55-20250Z",
        credits = 150000L,
        startingFaction = "COSMIC",
        shipCount = 1
    )
) : AgentsApi {
    override suspend fun getMyAgent(): AgentDto = agentResult
    override suspend fun getAgent(symbol: String): AgentDto = agentResult
}

class ShipyardRepositoryImplTest {

    private fun createRepo(
        shipyardApi: FakeShipyardApi = FakeShipyardApi(),
        fleetApi: FakeFleetApi = FakeFleetApi()
    ) = runTest {
        val db = createTestDatabase()
        val agentRepo = AgentRepositoryImpl(FakeAgentsApiForShipyard(), db)
        val fleetRepo = FleetRepositoryImpl(fleetApi, db, RefreshScheduler(backgroundScope))
        val repo = ShipyardRepositoryImpl(shipyardApi, fleetRepo, agentRepo, db)
        Triple(repo, agentRepo, fleetRepo)
    }

    @Test
    fun observeShipyard_beforeRefresh_emitsNull() = runTest {
        val db = createTestDatabase()
        val repo = ShipyardRepositoryImpl(
            FakeShipyardApi(),
            FleetRepositoryImpl(FakeFleetApi(), db, RefreshScheduler(backgroundScope)),
            AgentRepositoryImpl(FakeAgentsApiForShipyard(), db),
            db
        )
        val result = repo.observeShipyard("X1-DF55-20250Z").first()
        assertNull(result)
    }

    @Test
    fun refreshShipyard_populatesDb_observeEmitsShipyard() = runTest {
        val db = createTestDatabase()
        val repo = ShipyardRepositoryImpl(
            FakeShipyardApi(),
            FleetRepositoryImpl(FakeFleetApi(), db, RefreshScheduler(backgroundScope)),
            AgentRepositoryImpl(FakeAgentsApiForShipyard(), db),
            db
        )
        repo.refreshShipyard("X1-DF55", "X1-DF55-20250Z")
        val result = repo.observeShipyard("X1-DF55-20250Z").first()
        assertNotNull(result)
        assertEquals("X1-DF55-20250Z", result.symbol)
        assertEquals(1, result.ships?.size)
        assertEquals(ShipType.SHIP_MINING_DRONE, result.ships?.first()?.type)
    }

    @Test
    fun refreshShipyard_nullShips_fogOfWar_shipyardMetaCached() = runTest {
        val api = FakeShipyardApi(
            shipyardResult = ShipyardDto(symbol = "X1-DF55-20250Z", modificationsFee = 500, ships = null)
        )
        val db = createTestDatabase()
        val repo = ShipyardRepositoryImpl(
            api,
            FleetRepositoryImpl(FakeFleetApi(), db, RefreshScheduler(backgroundScope)),
            AgentRepositoryImpl(FakeAgentsApiForShipyard(), db),
            db
        )
        repo.refreshShipyard("X1-DF55", "X1-DF55-20250Z")
        val result = repo.observeShipyard("X1-DF55-20250Z").first()
        assertNotNull(result)
        assertNull(result.ships)
    }

    @Test
    fun refreshShipyard_secondRefresh_replacesShipListings() = runTest {
        val api = FakeShipyardApi()
        val db = createTestDatabase()
        val repo = ShipyardRepositoryImpl(
            api,
            FleetRepositoryImpl(FakeFleetApi(), db, RefreshScheduler(backgroundScope)),
            AgentRepositoryImpl(FakeAgentsApiForShipyard(), db),
            db
        )
        repo.refreshShipyard("X1-DF55", "X1-DF55-20250Z")

        api.shipyardResult = ShipyardDto(
            symbol = "X1-DF55-20250Z",
            modificationsFee = 1000,
            ships = listOf(
                minimalShipyardShipDto("SHIP_PROBE"),
                minimalShipyardShipDto("SHIP_MINING_DRONE")
            )
        )
        repo.refreshShipyard("X1-DF55", "X1-DF55-20250Z")

        val result = repo.observeShipyard("X1-DF55-20250Z").first()
        assertEquals(2, result?.ships?.size)
    }

    @Test
    fun refreshShipyard_networkFails_dbUnchanged() = runTest {
        val api = FakeShipyardApi()
        val db = createTestDatabase()
        val repo = ShipyardRepositoryImpl(
            api,
            FleetRepositoryImpl(FakeFleetApi(), db, RefreshScheduler(backgroundScope)),
            AgentRepositoryImpl(FakeAgentsApiForShipyard(), db),
            db
        )
        repo.refreshShipyard("X1-DF55", "X1-DF55-20250Z")
        val beforeCount = repo.observeShipyard("X1-DF55-20250Z").first()?.ships?.size

        api.exception = RuntimeException("Offline")
        try { repo.refreshShipyard("X1-DF55", "X1-DF55-20250Z") } catch (_: RuntimeException) {}

        val afterCount = repo.observeShipyard("X1-DF55-20250Z").first()?.ships?.size
        assertEquals(beforeCount, afterCount)
    }

    @Test
    fun purchaseShip_savesShipToFleetRepo() = runTest {
        val db = createTestDatabase()
        val fleetRepo = FleetRepositoryImpl(FakeFleetApi(), db, RefreshScheduler(backgroundScope))
        val repo = ShipyardRepositoryImpl(
            FakeShipyardApi(),
            fleetRepo,
            AgentRepositoryImpl(FakeAgentsApiForShipyard(), db),
            db
        )
        repo.purchaseShip(ShipType.SHIP_MINING_DRONE, "X1-DF55-20250Z")
        val ship = fleetRepo.observeShip("LADD-2").first()
        assertNotNull(ship)
        assertEquals("LADD-2", ship.symbol)
    }

    @Test
    fun purchaseShip_updatesAgentCredits() = runTest {
        val db = createTestDatabase()
        val agentRepo = AgentRepositoryImpl(FakeAgentsApiForShipyard(), db)
        agentRepo.refreshAgent()
        val repo = ShipyardRepositoryImpl(
            FakeShipyardApi(),
            FleetRepositoryImpl(FakeFleetApi(), db, RefreshScheduler(backgroundScope)),
            agentRepo,
            db
        )
        repo.purchaseShip(ShipType.SHIP_MINING_DRONE, "X1-DF55-20250Z")
        val agent = agentRepo.observeAgent().first()
        assertEquals(100000L, agent?.credits)
    }

    @Test
    fun purchaseShip_sendsCorrectShipType() = runTest {
        val api = FakeShipyardApi()
        val db = createTestDatabase()
        val repo = ShipyardRepositoryImpl(
            api,
            FleetRepositoryImpl(FakeFleetApi(), db, RefreshScheduler(backgroundScope)),
            AgentRepositoryImpl(FakeAgentsApiForShipyard(), db),
            db
        )
        repo.purchaseShip(ShipType.SHIP_MINING_DRONE, "X1-DF55-20250Z")
        assertEquals("SHIP_MINING_DRONE", api.lastPurchaseShipType)
        assertEquals("X1-DF55-20250Z", api.lastPurchaseWaypointSymbol)
    }

    @Test
    fun clearAll_emptiesAllTables() = runTest {
        val db = createTestDatabase()
        val repo = ShipyardRepositoryImpl(
            FakeShipyardApi(),
            FleetRepositoryImpl(FakeFleetApi(), db, RefreshScheduler(backgroundScope)),
            AgentRepositoryImpl(FakeAgentsApiForShipyard(), db),
            db
        )
        repo.refreshShipyard("X1-DF55", "X1-DF55-20250Z")
        repo.clearAll()
        val result = repo.observeShipyard("X1-DF55-20250Z").first()
        assertNull(result)
    }
}
