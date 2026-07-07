package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.testing.buildMockSpaceTradersClient
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val NAV = """
{"systemSymbol":"X1-AB12","waypointSymbol":"X1-AB12-I10","status":"IN_ORBIT","flightMode":"CRUISE",
 "route":{"destination":{"symbol":"X1-AB12-I10","type":"JUMP_GATE","systemSymbol":"X1-AB12","x":5,"y":5},
          "origin":{"symbol":"X1-DM91-I52","type":"JUMP_GATE","systemSymbol":"X1-DM91","x":0,"y":0},
          "departureTime":"2026-07-06T00:00:00.000Z","arrival":"2026-07-06T00:00:00.000Z"}}
"""
private val WARP_RESPONSE = """{"data":{"nav":$NAV,"fuel":{"current":100,"capacity":400}}}"""
private val JUMP_RESPONSE = """{"data":{"nav":$NAV,"cooldown":{"shipSymbol":"LADD-1","totalSeconds":60,"remainingSeconds":60,"expiration":"2099-01-01T00:00:00Z"}}}"""
private const val JUMPGATE_RESPONSE = """{"data":{"symbol":"X1-DM91-I52","connections":["X1-AB12-I10","X1-CD34-I88"]}}"""
private const val SYSTEMS_RESPONSE = """{"data":[{"symbol":"X1-AB12","sectorSymbol":"X1","type":"RED_STAR","x":10,"y":20}],"meta":{"total":42,"page":1,"limit":20}}"""

class TravelApiImplTest {

    private fun api(body: String, capture: MutableList<HttpRequestData> = mutableListOf()) =
        TravelApiImpl(buildMockSpaceTradersClient { request ->
            capture.add(request)
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) to capture

    @Test
    fun warp_parsesNavAndFuel_andPosts() = runTest {
        val (a, requests) = api(WARP_RESPONSE)
        val r = a.warp("LADD-1", "X1-AB12-I10").toDomain()
        assertEquals(ShipNavStatus.IN_ORBIT, r.nav.status)
        assertEquals(100, r.fuel.current)
        assertEquals(HttpMethod.Post, requests.single().method)
        assertTrue(requests.single().url.encodedPath.endsWith("my/ships/LADD-1/warp"))
    }

    @Test
    fun jump_parsesNavAndCooldown_andPosts() = runTest {
        val (a, requests) = api(JUMP_RESPONSE)
        val r = a.jump("LADD-1", "X1-AB12-I10").toDomain()
        assertEquals("X1-AB12", r.nav.systemSymbol)
        assertEquals(60, r.cooldown.totalSeconds)
        assertTrue(requests.single().url.encodedPath.endsWith("my/ships/LADD-1/jump"))
    }

    @Test
    fun getJumpGate_parsesConnections() = runTest {
        val (a, requests) = api(JUMPGATE_RESPONSE)
        val gate = a.getJumpGate("X1-DM91", "X1-DM91-I52").toDomain()
        assertEquals(2, gate.connections.size)
        assertTrue(requests.single().url.encodedPath.endsWith("systems/X1-DM91/waypoints/X1-DM91-I52/jump-gate"))
    }

    @Test
    fun getSystems_parsesPaginatedSystems() = runTest {
        val (a, _) = api(SYSTEMS_RESPONSE)
        val page = a.getSystems(1, 20)
        assertEquals(1, page.data.size)
        assertEquals("X1-AB12", page.data[0].symbol)
        assertEquals(42, page.meta.total)
    }
}
