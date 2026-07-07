package com.brokenhuskysledteam.spacetradersio.sdk.e2e

import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Live E2E for Phase 2 (ship mounts) against the real SpaceTraders API.
 *
 * Registers a fresh agent and reads the command ship's mounts, verifying the real `ShipMount`
 * wire contract deserializes and maps to the domain. The starting frigate always ships with
 * mounts (a mining laser, surveyor, etc.), so this asserts a non-empty, well-formed mount list.
 *
 * Install/remove are precondition-heavy on the live server (ship must be docked at a shipyard
 * with the mount already in cargo), so they are covered by MockEngine unit tests rather than a
 * live test. Runs only under `-Pe2e`.
 */
class MountsLiveTest {

    @Test
    fun getMounts_returnsWellFormedMountsForCommandShip() = runBlocking {
        assumeE2eEnabled()
        val session = registerE2eAgent()

        val mounts = session.mountsApi.getMounts(session.commandShip.symbol).map { it.toDomain() }
        assertTrue(mounts.isNotEmpty(), "The starting frigate should have installed mounts")
        mounts.forEach { mount ->
            assertTrue(mount.symbol.startsWith("MOUNT_"), "Mount symbol should be a MOUNT_* value: ${mount.symbol}")
            assertTrue(mount.name.isNotBlank(), "Mount name should not be blank")
        }
    }
}
