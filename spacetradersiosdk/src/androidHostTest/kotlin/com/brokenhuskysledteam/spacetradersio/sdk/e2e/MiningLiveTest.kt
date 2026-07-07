package com.brokenhuskysledteam.spacetradersio.sdk.e2e

import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Live E2E for Phase 3 (mining & extraction) against the real SpaceTraders API.
 *
 * Registers a fresh agent, flies the command ship (which starts with a mining laser) to an
 * asteroid, orbits, and extracts — verifying a real yield lands in the hold and a cooldown is
 * incurred. Then jettisons the extracted good to verify the jettison path. This proves the
 * mine loop end-to-end. Runs only under `-Pe2e`.
 */
class MiningLiveTest {

    private val asteroidTypes = setOf("ASTEROID", "ASTEROID_FIELD", "ENGINEERED_ASTEROID")

    @Test
    fun extractAtAsteroid_addsYieldToCargo_andIncursCooldown() = runBlocking {
        assumeE2eEnabled()
        val session = registerE2eAgent()
        val ship = session.commandShip
        val system = ship.nav.systemSymbol

        val waypoints = session.systemsApi.getSystemWaypoints(system, limit = 20).data.map { it.toDomain() }
        val asteroid = waypoints.firstOrNull { it.type.name in asteroidTypes }
        assumeTrue("No asteroid in the starting system's first page — cannot run mining test", asteroid != null)

        session.arriveAndOrbit(ship.symbol, asteroid!!.symbol)

        val extract = session.miningApi.extract(ship.symbol).toDomain()
        assertTrue(extract.yieldUnits >= 1, "Extraction should yield at least one unit")
        assertTrue(extract.cargo.units >= extract.yieldUnits, "Cargo should contain at least the extracted units")
        assertTrue(extract.cooldown.expiration != null, "Extraction should incur a cooldown")

        // Jettison what we just mined and confirm the hold shrinks.
        val jettisoned = session.miningApi.jettison(ship.symbol, extract.yieldSymbol, extract.yieldUnits).cargo.toDomain()
        assertTrue(jettisoned.units < extract.cargo.units, "Jettison should reduce the cargo unit count")
    }
}
