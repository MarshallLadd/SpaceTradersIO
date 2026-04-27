package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.SystemsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Waypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.SystemRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.WaypointStateStore
import kotlinx.coroutines.flow.Flow

/**
 * Concrete implementation of [SystemRepository] that fetches waypoints for a star
 * system via paginated API calls and stores them in an in-memory reactive cache.
 *
 * **Pattern:** Pagination exhaustion + in-memory reactive store. The SpaceTraders API
 * caps results at 20 per page and embeds the total record count in a `meta` object.
 * [fetchAllPages] uses that total to know when all pages have been consumed, then hands
 * the full collection to [WaypointStateStore] in a single `putAll` call. Any ViewModel
 * that holds an `observe` reference to the store receives the full set atomically.
 * To apply this pattern in a new project: (1) accumulate pages until
 * `result.size >= meta.total`, (2) write all results to the store in one call to avoid
 * partial emissions, and (3) expose the store's `StateFlow` to the UI layer.
 *
 * **In this project:** Waypoints are fetched once per system visit and held in memory
 * for the duration of the session. The [WaypointStateStore] is session-scoped, so the
 * cache is automatically cleared on logout when the session is torn down.
 *
 * @param systemsApi       Network layer for the SpaceTraders `/systems/{systemSymbol}/waypoints`
 *                         endpoint.
 * @param waypointStateStore In-memory reactive cache keyed by waypoint symbol. Updated by
 *                           this repository; observed by ViewModels.
 */
class SystemRepositoryImpl(
    private val systemsApi: SystemsApi,
    private val waypointStateStore: WaypointStateStore
) : SystemRepository {

    /**
     * Fetches all waypoints for [systemSymbol] from the network, populates
     * [waypointStateStore], and returns the full list.
     *
     * The function is safe to call multiple times — subsequent calls overwrite the
     * store with the freshest data from the API. After this returns, any ViewModel
     * observing `waypointStateStore.entities` will receive the updated waypoint map
     * automatically through the [kotlinx.coroutines.flow.StateFlow] mechanism.
     *
     * @param systemSymbol The system identifier (e.g. `"X1-DF55"`) whose waypoints
     *                     should be fetched.
     * @return The complete list of [Waypoint]s in the system.
     */
    override suspend fun getWaypoint(systemSymbol: String, waypointSymbol: String): Waypoint {
        val domain = systemsApi.getWaypoint(systemSymbol, waypointSymbol).toDomain()
        waypointStateStore.put(waypointSymbol, domain)
        return domain
    }

    override fun observeWaypoint(waypointSymbol: String): Flow<Waypoint?> =
        waypointStateStore.observe(waypointSymbol)

    override suspend fun getSystemWaypoints(systemSymbol: String): List<Waypoint> {
        val allWaypoints = fetchAllPages(systemSymbol)
        // Write all waypoints atomically so observers see the full set at once,
        // not a partial result while pages are still being fetched.
        waypointStateStore.putAll(allWaypoints.associateBy { it.symbol })
        return allWaypoints
    }

    /**
     * Exhausts all pages of the waypoint endpoint for [systemSymbol] and returns the
     * accumulated results.
     *
     * **Pagination exhaustion pattern:** The SpaceTraders API embeds a `meta.total`
     * count in every paginated response. This function starts at page 1 and continues
     * requesting successive pages until the accumulated result list reaches that total.
     * Using `result.size < total` as the loop condition (rather than checking for an
     * empty response page) is robust against off-by-one errors and handles the edge
     * case where the final page is exactly full.
     *
     * Page size is fixed at 20, which is the SpaceTraders API maximum. For APIs with a
     * configurable limit, prefer passing the maximum allowed value to minimize round trips.
     *
     * @param systemSymbol The system identifier whose waypoints are being fetched.
     * @return All [Waypoint]s across all pages, in the order they were returned by the API.
     */
    private suspend fun fetchAllPages(systemSymbol: String): List<Waypoint> {
        val result = mutableListOf<Waypoint>()
        var page = 1
        do {
            val response = systemsApi.getSystemWaypoints(systemSymbol, page = page, limit = 20)
            result.addAll(response.data.map { it.toDomain() })
            val total = response.meta.total
            page++
            // Continue until we have accumulated every waypoint the API reported.
            // `total` is the authoritative count from the server; comparing against it
            // avoids a final "empty page" round-trip that a `while (page.hasNext)`
            // approach would require.
        } while (result.size < total)
        return result
    }
}
