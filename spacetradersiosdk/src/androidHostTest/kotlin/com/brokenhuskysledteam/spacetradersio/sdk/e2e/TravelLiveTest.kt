package com.brokenhuskysledteam.spacetradersio.sdk.e2e

import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Live E2E for Phase 4 (inter-system travel) against the real SpaceTraders API.
 *
 * Verifies the reliably-executable read paths: galaxy browsing (`GET /systems`) and jump-gate
 * connections (`GET .../jump-gate`). Warp and jump *execution* are precondition-heavy on the live
 * server (a reachable destination in warp range with fuel; a connected charged jump gate), so
 * they are covered by MockEngine unit tests rather than a live test. Runs only under `-Pe2e`.
 */
class TravelLiveTest {

    @Test
    fun getSystems_returnsPaginatedGalaxy() = runBlocking {
        assumeE2eEnabled()
        val session = registerE2eAgent()
        val page = session.travelApi.getSystems(1, 20)
        assertTrue(page.data.isNotEmpty(), "The galaxy should contain systems")
        assertTrue(page.meta.total > 0)
        page.data.forEach { s -> assertTrue(s.symbol.isNotBlank()) }
    }

    @Test
    fun getJumpGate_returnsConnectionsWhenAJumpGateExists() = runBlocking {
        assumeE2eEnabled()
        val session = registerE2eAgent()
        val system = session.commandShip.nav.systemSymbol
        val waypoints = session.systemsApi.getSystemWaypoints(system, limit = 20).data.map { it.toDomain() }
        val jumpGate = waypoints.firstOrNull { it.type.name == "JUMP_GATE" }
        assumeTrue("No jump gate in the starting system's first page", jumpGate != null)

        val gate = session.travelApi.getJumpGate(system, jumpGate!!.symbol).toDomain()
        assertEquals(jumpGate.symbol, gate.symbol)
        // connections may be empty for an uncharted/isolated gate, but the call must succeed and map.
    }
}
