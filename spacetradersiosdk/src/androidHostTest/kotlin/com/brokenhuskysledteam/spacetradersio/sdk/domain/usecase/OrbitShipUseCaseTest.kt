package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApiImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.testing.buildMockSpaceTradersClient
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val ORBIT_RESPONSE = """
{
  "data": {
    "nav": {
      "systemSymbol": "X1-DF55",
      "waypointSymbol": "X1-DF55-20250Z",
      "route": {
        "destination": {"symbol":"X1-DF55-20250Z","type":"MOON","systemSymbol":"X1-DF55","x":0,"y":0},
        "origin": {"symbol":"X1-DF55-20250Z","type":"MOON","systemSymbol":"X1-DF55","x":0,"y":0},
        "departureTime": "2025-06-01T10:00:00.000Z",
        "arrival": "2025-06-01T10:00:00.000Z"
      },
      "status": "IN_ORBIT",
      "flightMode": "CRUISE"
    }
  }
}
"""

class OrbitShipUseCaseTest {

    private fun buildUseCase() = OrbitShipUseCaseImpl(
        FleetApiImpl(buildMockSpaceTradersClient { respond(
            content = ORBIT_RESPONSE,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        )})
    )

    @Test
    fun invoke_returnsNavWithInOrbitStatus() = runTest {
        val nav = buildUseCase().invoke("LADD-1")
        assertEquals(ShipNavStatus.IN_ORBIT, nav.status)
    }

    @Test
    fun invoke_returnsNavWithCorrectWaypointSymbol() = runTest {
        val nav = buildUseCase().invoke("LADD-1")
        assertEquals("X1-DF55-20250Z", nav.waypointSymbol)
    }

    @Test
    fun invoke_returnsNavWithCorrectSystemSymbol() = runTest {
        val nav = buildUseCase().invoke("LADD-1")
        assertEquals("X1-DF55", nav.systemSymbol)
    }
}
