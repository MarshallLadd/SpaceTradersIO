package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.testing.buildMockSpaceTradersClient
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val WAYPOINTS_RESPONSE = """
{
  "data": [
    {
      "symbol": "X1-DF55-17335A",
      "type": "PLANET",
      "systemSymbol": "X1-DF55",
      "x": -21,
      "y": -16,
      "orbitals": [{"symbol": "X1-DF55-20250Z"}],
      "traits": [
        {"symbol": "MARKETPLACE", "name": "Marketplace", "description": "A marketplace"}
      ],
      "isUnderConstruction": false
    }
  ],
  "meta": {"total": 1, "page": 1, "limit": 20}
}
"""

private fun MockRequestHandleScope.okJson(content: String) = respond(
    content = content,
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
)

class SystemsApiImplTest {

    @Test
    fun getSystemWaypoints_sendsGetRequest() = runTest {
        var capturedMethod: HttpMethod? = null
        val client = buildMockSpaceTradersClient { request ->
            capturedMethod = request.method
            okJson(WAYPOINTS_RESPONSE)
        }
        SystemsApiImpl(client).getSystemWaypoints("X1-DF55")
        assertEquals(HttpMethod.Get, capturedMethod)
    }

    @Test
    fun getSystemWaypoints_sendsToCorrectPath() = runTest {
        var capturedPath: String? = null
        val client = buildMockSpaceTradersClient { request ->
            capturedPath = request.url.encodedPath
            okJson(WAYPOINTS_RESPONSE)
        }
        SystemsApiImpl(client).getSystemWaypoints("X1-DF55")
        assertEquals("/systems/X1-DF55/waypoints", capturedPath)
    }

    @Test
    fun getSystemWaypoints_sendsPaginationParams() = runTest {
        var capturedPage: String? = null
        var capturedLimit: String? = null
        val client = buildMockSpaceTradersClient { request ->
            capturedPage = request.url.parameters["page"]
            capturedLimit = request.url.parameters["limit"]
            okJson(WAYPOINTS_RESPONSE)
        }
        SystemsApiImpl(client).getSystemWaypoints("X1-DF55", page = 3, limit = 10)
        assertEquals("3", capturedPage)
        assertEquals("10", capturedLimit)
    }

    @Test
    fun getSystemWaypoints_returnsWaypointWithCorrectSymbol() = runTest {
        val client = buildMockSpaceTradersClient { okJson(WAYPOINTS_RESPONSE) }
        val result = SystemsApiImpl(client).getSystemWaypoints("X1-DF55")
        assertEquals("X1-DF55-17335A", result.data.first().symbol)
    }

    @Test
    fun getSystemWaypoints_returnsPaginationMeta() = runTest {
        val client = buildMockSpaceTradersClient { okJson(WAYPOINTS_RESPONSE) }
        val result = SystemsApiImpl(client).getSystemWaypoints("X1-DF55")
        assertEquals(1, result.meta.total)
    }

    @Test
    fun getSystemWaypoints_returnsWaypointWithTraits() = runTest {
        val client = buildMockSpaceTradersClient { okJson(WAYPOINTS_RESPONSE) }
        val result = SystemsApiImpl(client).getSystemWaypoints("X1-DF55")
        assertEquals("MARKETPLACE", result.data.first().traits.first().symbol)
    }

    @Test
    fun getSystemWaypoints_returnsWaypointWithOrbitals() = runTest {
        val client = buildMockSpaceTradersClient { okJson(WAYPOINTS_RESPONSE) }
        val result = SystemsApiImpl(client).getSystemWaypoints("X1-DF55")
        assertEquals("X1-DF55-20250Z", result.data.first().orbitals.first().symbol)
    }
}
