package com.brokenhuskysledteam.spacetradersio.sdk.e2e

import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Live E2E for Phase 0 (cargo inventory) against the real SpaceTraders API.
 *
 * Registers a fresh agent, then verifies that the real `cargo.inventory` array deserializes
 * without error and that the cargo model is internally consistent: the aggregate `units` equals
 * the sum of the per-item unit counts, on both the registration bundle and `GET /my/ships`.
 * This proves the inventory wire contract and the DTO→domain mapping end-to-end.
 *
 * Runs only under `-Pe2e` (see [assumeE2eEnabled]); otherwise skipped.
 */
class CargoInventoryLiveTest {

    @Test
    fun registeredShips_cargoInventory_isConsistentAndMapsToDomain() = runBlocking {
        assumeE2eEnabled()
        val session = registerE2eAgent()

        // Starting ships come back in the registration bundle.
        val ships = session.registration.ships
        assertTrue(ships.isNotEmpty(), "A fresh agent should start with at least one ship")

        ships.forEach { ship ->
            // Invariant: aggregate cargo units == sum of the inventory item unit counts.
            val inventorySum = ship.cargo.inventory.sumOf { it.units }
            assertEquals(
                ship.cargo.units,
                inventorySum,
                "Ship ${ship.symbol}: cargo.units (${ship.cargo.units}) must equal the sum of inventory units ($inventorySum)"
            )
            // Every inventory entry carries a non-blank symbol/name and a positive unit count.
            ship.cargo.inventory.forEach { item ->
                assertTrue(item.symbol.isNotBlank(), "inventory item symbol must not be blank")
                assertTrue(item.units >= 1, "inventory item ${item.symbol} must have >= 1 unit")
            }
            // DTO → domain preserves the inventory list length and units.
            val domain = ship.toDomain()
            assertEquals(ship.cargo.inventory.size, domain.cargo.inventory.size)
            assertEquals(ship.cargo.units, domain.cargo.inventory.sumOf { it.units })
        }
    }

    @Test
    fun getMyShips_cargoInventory_deserializesAndIsConsistent() = runBlocking {
        assumeE2eEnabled()
        val session = registerE2eAgent()

        val ships = session.fleetApi.getMyShips().data
        assertTrue(ships.isNotEmpty(), "GET /my/ships should return the starting ships")

        ships.forEach { ship ->
            val inventorySum = ship.cargo.inventory.sumOf { it.units }
            assertEquals(ship.cargo.units, inventorySum, "Ship ${ship.symbol}: cargo units vs inventory sum")
        }
    }
}
