package com.brokenhuskysledteam.spacetradersio.sdk.e2e

import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Live E2E for Phase 6 (scanning & charting) against the real SpaceTraders API.
 *
 * The command ship starts with a sensor array, so it can scan. Registers a fresh agent, orbits,
 * and scans the current system's waypoints — verifying real scan results deserialize (including
 * revealed traits) and a cooldown is incurred. Charting is precondition-heavy (requires an
 * uncharted waypoint), so it is covered by a MockEngine unit test rather than a live test.
 * Runs only under `-Pe2e`.
 */
class ScanLiveTest {

    @Test
    fun scanWaypoints_returnsWaypointsAndCooldown() = runBlocking {
        assumeE2eEnabled()
        val session = registerE2eAgent()
        val ship = session.commandShip
        // Scanning is done from orbit; the command ship starts in orbit but ensure it.
        if (ship.nav.status != "IN_ORBIT") session.fleetApi.orbitShip(ship.symbol)

        val result = session.scanApi.scanWaypoints(ship.symbol).toDomain()
        assertTrue(result.waypoints.isNotEmpty(), "A waypoint scan should reveal waypoints")
        assertTrue(result.cooldown.expiration != null, "A scan should incur a cooldown")
        result.waypoints.forEach { wp -> assertTrue(wp.symbol.isNotBlank()) }
    }

    @Test
    fun scanSystems_returnsNearbySystems() = runBlocking {
        assumeE2eEnabled()
        val session = registerE2eAgent()
        val ship = session.commandShip
        if (ship.nav.status != "IN_ORBIT") session.fleetApi.orbitShip(ship.symbol)

        val result = session.scanApi.scanSystems(ship.symbol).toDomain()
        assertTrue(result.systems.isNotEmpty(), "A system scan should reveal nearby systems")
        assertTrue(result.systems.all { it.symbol.isNotBlank() })
    }
}
