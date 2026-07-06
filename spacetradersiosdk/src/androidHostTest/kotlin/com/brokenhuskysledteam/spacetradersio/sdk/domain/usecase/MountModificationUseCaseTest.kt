package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.MountsApiImpl
import com.brokenhuskysledteam.spacetradersio.sdk.data.repository.AgentRepositoryImpl
import com.brokenhuskysledteam.spacetradersio.sdk.data.repository.FleetRepositoryImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.testing.buildMockSpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.testing.createTestDatabase
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val INSTALL_RESPONSE = """
{"data":{
  "agent":{"symbol":"LADD","headquarters":"X1-DM91-A1","credits":168000,"startingFaction":"COSMIC","shipCount":2},
  "mounts":[{"symbol":"MOUNT_MINING_LASER_I","name":"Mining Laser I","description":"z","requirements":{"power":1,"crew":1},"strength":10}],
  "cargo":{"capacity":40,"units":0,"inventory":[]},
  "transaction":{"waypointSymbol":"X1-DM91-A1","shipSymbol":"LADD-1","tradeSymbol":"MOUNT_MINING_LASER_I","totalPrice":3600,"timestamp":"2026-07-06T15:47:49.153Z"}
}}
"""

private fun buildMockMountsApi(body: String) = MountsApiImpl(buildMockSpaceTradersClient {
    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
})

class MountModificationUseCaseTest {

    @Test
    fun installMount_updatesCargoAndAgent_andReturnsMounts() = runTest {
        val db = createTestDatabase()
        val fleetRepo = FleetRepositoryImpl(StubFleetApi, db, RefreshScheduler(backgroundScope))
        val agentRepo = AgentRepositoryImpl(FakeAgentsApiForTrade(), db)
        fleetRepo.saveShip(tradeTestShip("LADD-1"))

        val result = InstallMountUseCaseImpl(buildMockMountsApi(INSTALL_RESPONSE), fleetRepo, agentRepo)
            .invoke("LADD-1", "MOUNT_MINING_LASER_I")

        assertEquals("MOUNT_MINING_LASER_I", result.mounts.single().symbol)
        assertEquals(3600, result.transaction.totalPrice)
        assertEquals(0, fleetRepo.observeShip("LADD-1").first()!!.cargo.units)
        assertEquals(168000L, agentRepo.observeAgent().first()?.credits)
    }

    @Test
    fun removeMount_updatesCargoAndAgent() = runTest {
        val db = createTestDatabase()
        val fleetRepo = FleetRepositoryImpl(StubFleetApi, db, RefreshScheduler(backgroundScope))
        val agentRepo = AgentRepositoryImpl(FakeAgentsApiForTrade(), db)
        fleetRepo.saveShip(tradeTestShip("LADD-1"))

        val result = RemoveMountUseCaseImpl(buildMockMountsApi(INSTALL_RESPONSE), fleetRepo, agentRepo)
            .invoke("LADD-1", "MOUNT_MINING_LASER_I")

        assertEquals(168000L, result.agent.credits)
        assertEquals(168000L, agentRepo.observeAgent().first()?.credits)
    }
}
