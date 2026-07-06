package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MountRequirementsDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipModificationTransactionDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipMountDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class MountMapperTest {

    @Test
    fun mount_toDomain_mapsAllFields() {
        val dto = ShipMountDto(
            symbol = "MOUNT_MINING_LASER_II",
            name = "Mining Laser II",
            description = "An advanced mining laser.",
            strength = 5,
            requirements = MountRequirementsDto(power = 2, crew = 2, slots = null),
            deposits = listOf("IRON_ORE", "COPPER_ORE")
        )
        val mount = dto.toDomain()
        assertEquals("MOUNT_MINING_LASER_II", mount.symbol)
        assertEquals("Mining Laser II", mount.name)
        assertEquals(5, mount.strength)
        assertEquals(2, mount.requirements.power)
        assertEquals(2, mount.requirements.crew)
        assertNull(mount.requirements.slots)
        assertEquals(listOf("IRON_ORE", "COPPER_ORE"), mount.deposits)
    }

    @Test
    fun mount_toDomain_nullStrengthAndEmptyDepositsDefaults() {
        val dto = ShipMountDto(symbol = "MOUNT_SENSOR_ARRAY_I", name = "Sensor Array I", description = "x")
        val mount = dto.toDomain()
        assertNull(mount.strength)
        assertEquals(emptyList(), mount.deposits)
    }

    @Test
    fun modificationTransaction_toDomain_parsesTimestamp() {
        val dto = ShipModificationTransactionDto(
            waypointSymbol = "X1-DM91-A1", shipSymbol = "LADD-1", tradeSymbol = "MOUNT_MINING_LASER_I",
            totalPrice = 3600, timestamp = "2026-07-06T15:47:49.153Z"
        )
        val tx = dto.toDomain()
        assertEquals("MOUNT_MINING_LASER_I", tx.tradeSymbol)
        assertEquals(3600, tx.totalPrice)
        assertEquals(Instant.parse("2026-07-06T15:47:49.153Z"), tx.timestamp)
    }
}
