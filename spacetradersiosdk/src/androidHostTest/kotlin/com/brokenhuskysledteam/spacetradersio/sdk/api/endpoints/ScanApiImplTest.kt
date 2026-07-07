package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol
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

private const val SCAN_SYSTEMS_RESPONSE = """
{"data":{
  "cooldown":{"shipSymbol":"LADD-1","totalSeconds":60,"remainingSeconds":60,"expiration":"2099-01-01T00:00:00Z"},
  "systems":[{"symbol":"X1-AB12","sectorSymbol":"X1","type":"RED_STAR","x":10,"y":20,"distance":42}]
}}
"""
private const val SCAN_WAYPOINTS_RESPONSE = """
{"data":{
  "cooldown":{"shipSymbol":"LADD-1","totalSeconds":60,"remainingSeconds":60,"expiration":"2099-01-01T00:00:00Z"},
  "waypoints":[{"symbol":"X1-DM91-C1","type":"PLANET","systemSymbol":"X1-DM91","x":3,"y":4,
    "traits":[{"symbol":"MARKETPLACE","name":"Marketplace","description":"trade"}]}]
}}
"""
private const val CHART_RESPONSE = """
{"data":{"chart":{"waypointSymbol":"X1-DM91-C1","submittedBy":"LADD","submittedOn":"2026-07-06T00:00:00Z"},
  "waypoint":{"symbol":"X1-DM91-C1","type":"PLANET","systemSymbol":"X1-DM91","x":3,"y":4,
    "traits":[{"symbol":"SHIPYARD","name":"Shipyard","description":"ships"}]}}}
"""

class ScanApiImplTest {

    private fun api(body: String, capture: MutableList<HttpRequestData> = mutableListOf()) =
        ScanApiImpl(buildMockSpaceTradersClient { request ->
            capture.add(request)
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) to capture

    @Test
    fun scanSystems_parsesSystemsAndCooldown_andPosts() = runTest {
        val (a, requests) = api(SCAN_SYSTEMS_RESPONSE)
        val result = a.scanSystems("LADD-1").toDomain()
        assertEquals(1, result.systems.size)
        assertEquals(42, result.systems[0].distance)
        assertEquals(60, result.cooldown.totalSeconds)
        assertEquals(HttpMethod.Post, requests.single().method)
        assertTrue(requests.single().url.encodedPath.endsWith("my/ships/LADD-1/scan/systems"))
    }

    @Test
    fun scanWaypoints_revealsTraits_andPosts() = runTest {
        val (a, requests) = api(SCAN_WAYPOINTS_RESPONSE)
        val result = a.scanWaypoints("LADD-1").toDomain()
        assertEquals(1, result.waypoints.size)
        assertTrue(result.waypoints[0].traits.any { it.symbol == WaypointTraitSymbol.MARKETPLACE })
        assertTrue(requests.single().url.encodedPath.endsWith("my/ships/LADD-1/scan/waypoints"))
    }

    @Test
    fun chart_revealsWaypoint_andPosts() = runTest {
        val (a, requests) = api(CHART_RESPONSE)
        val result = a.chartWaypoint("LADD-1").toDomain()
        assertEquals("X1-DM91-C1", result.waypoint.symbol)
        assertTrue(result.waypoint.traits.any { it.symbol == WaypointTraitSymbol.SHIPYARD })
        assertTrue(requests.single().url.encodedPath.endsWith("my/ships/LADD-1/chart"))
    }
}
