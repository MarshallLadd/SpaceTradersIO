package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.MiningApiImpl
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
import kotlin.test.assertTrue

// Far-future cooldown expirations avoid the RefreshScheduler immediate-fire/infinite-loop gotcha.
private const val EXTRACT_RESPONSE = """
{"data":{
  "extraction":{"shipSymbol":"LADD-1","yield":{"symbol":"IRON_ORE","units":5}},
  "cooldown":{"shipSymbol":"LADD-1","totalSeconds":70,"remainingSeconds":70,"expiration":"2099-01-01T00:00:00Z"},
  "cargo":{"capacity":40,"units":5,"inventory":[{"symbol":"IRON_ORE","name":"Iron Ore","description":"ore","units":5}]}
}}
"""
private const val SURVEY_RESPONSE = """
{"data":{
  "cooldown":{"shipSymbol":"LADD-1","totalSeconds":60,"remainingSeconds":60,"expiration":"2099-01-01T00:00:00Z"},
  "surveys":[{"signature":"sig-1","symbol":"X1-DM91-B7","deposits":[{"symbol":"IRON_ORE"}],"expiration":"2099-01-01T01:00:00Z","size":"MODERATE"}]
}}
"""
private const val JETTISON_RESPONSE = """{"data":{"cargo":{"capacity":40,"units":0,"inventory":[]}}}"""

private fun miningApi(body: String) = MiningApiImpl(buildMockSpaceTradersClient {
    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
})

class MiningUseCaseTest {

    @Test
    fun extract_updatesCargoAndCooldown_andReturnsYield() = runTest {
        val db = createTestDatabase()
        val fleet = FleetRepositoryImpl(StubFleetApi, db, RefreshScheduler(backgroundScope))
        fleet.saveShip(tradeTestShip("LADD-1"))
        val result = ExtractResourcesUseCaseImpl(miningApi(EXTRACT_RESPONSE), fleet).invoke("LADD-1")

        assertEquals("IRON_ORE", result.yieldSymbol)
        assertEquals(5, result.yieldUnits)
        val ship = fleet.observeShip("LADD-1").first()!!
        assertEquals(5, ship.cargo.units)
        assertEquals(listOf("IRON_ORE"), ship.cargo.inventory.map { it.symbol })
        assertEquals(70, ship.cooldown.totalSeconds)
    }

    @Test
    fun createSurvey_updatesCooldown_andReturnsSurveys() = runTest {
        val db = createTestDatabase()
        val fleet = FleetRepositoryImpl(StubFleetApi, db, RefreshScheduler(backgroundScope))
        fleet.saveShip(tradeTestShip("LADD-1"))
        val result = CreateSurveyUseCaseImpl(miningApi(SURVEY_RESPONSE), fleet).invoke("LADD-1")

        assertEquals(1, result.surveys.size)
        assertEquals("sig-1", result.surveys[0].signature)
        assertEquals(60, fleet.observeShip("LADD-1").first()!!.cooldown.totalSeconds)
    }

    @Test
    fun jettison_updatesCargo() = runTest {
        val db = createTestDatabase()
        val fleet = FleetRepositoryImpl(StubFleetApi, db, RefreshScheduler(backgroundScope))
        fleet.saveShip(tradeTestShip("LADD-1"))
        val cargo = JettisonCargoUseCaseImpl(miningApi(JETTISON_RESPONSE), fleet).invoke("LADD-1", "IRON_ORE", 5)

        assertEquals(0, cargo.units)
        assertTrue(fleet.observeShip("LADD-1").first()!!.cargo.inventory.isEmpty())
    }
}
