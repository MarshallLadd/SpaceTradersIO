package com.brokenhuskysledteam.spacetradersio.sdk.domain.repository

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.JumpGate
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SystemPage

/**
 * Read access to inter-system travel data (jump-gate connections and galaxy systems).
 *
 * **Pattern:** Read-through repository (no cache). These are fetched on demand when a
 * travel/browse screen opens.
 */
interface TravelRepository {

    /** Fetches the jump gate at a waypoint and its connections. */
    suspend fun getJumpGate(systemSymbol: String, waypointSymbol: String): JumpGate

    /** Fetches a page of star systems for galaxy browsing. */
    suspend fun getSystems(page: Int = 1, limit: Int = 20): SystemPage
}
