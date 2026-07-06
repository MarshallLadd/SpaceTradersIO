package com.brokenhuskysledteam.spacetradersio.sdk.domain.repository

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipMount

/**
 * Read access to a ship's installed mounts.
 *
 * **Pattern:** Read-through repository (no cache). Mounts change only via explicit install/remove
 * actions, so the screen fetches them fresh when opened and re-fetches after a modification.
 */
interface MountsRepository {
    /**
     * Fetches the mounts currently installed on [shipSymbol].
     *
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersApiException on failure.
     */
    suspend fun getMounts(shipSymbol: String): List<ShipMount>
}
