package com.brokenhuskysledteam.spacetradersio.sdk.domain.repository

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Waypoint
import kotlinx.coroutines.flow.Flow

/**
 * Defines data operations for star systems and their waypoints.
 *
 * **Pattern:** Repository interface in the domain layer. The interface lives in `domain/` so
 * that callers never depend on how waypoints are fetched or where they are stored. To apply
 * this in a new project: declare the minimum set of data operations your domain needs here,
 * then put pagination logic, network calls, and cache writes inside the `data/` implementation.
 *
 * **Benefits of the interface boundary:**
 * - Tests inject a hand-written fake that returns a hardcoded list — no network required.
 * - The domain layer is isolated from the `SystemsApi` Ktor details and `WaypointStateStore`
 *   specifics.
 * - Swapping caching strategy (e.g. SQLDelight instead of an in-memory store) requires
 *   changing only `SystemRepositoryImpl`, not any caller.
 *
 * **In this project:** `SystemRepositoryImpl` handles multi-page API responses transparently:
 * it loops over `GET /systems/{symbol}/waypoints` until all pages are consumed, then stores
 * every waypoint into `WaypointStateStore` (a reactive in-memory store). Callers receive the
 * complete list directly from the suspend return value and can also observe the store's
 * `StateFlow` for reactive updates.
 */
interface SystemRepository {

    /**
     * Fetches all waypoints for the given star system, handling pagination internally, and
     * returns the complete list.
     *
     * **Pattern:** Suspend function (not `StateFlow`) for on-demand fetch-and-cache. This
     * deliberately returns a value instead of a flow because waypoints are loaded on demand
     * when a screen opens — not observed continuously. The reactive side-effect (updating
     * `WaypointStateStore`) is an implementation detail that enables other parts of the app
     * to observe waypoints reactively without this interface needing to expose a flow.
     *
     * **Why suspend instead of Flow:** Waypoint data does not change during a session and does
     * not need to stream live updates. A one-shot suspend call is simpler to call, easier to
     * test, and avoids the boilerplate of collecting a flow for data that only needs to be
     * loaded once.
     *
     * @param systemSymbol The system identifier to query (e.g. `"X1-AB12"`). Must match the
     *   format returned by the SpaceTraders API.
     * @return A [List] of every [Waypoint] in the system, assembled from all paginated API
     *   responses. Returns an empty list if the system has no waypoints.
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.domain.error.SpaceTradersApiException
     *   if any page request fails.
     */
    suspend fun getSystemWaypoints(systemSymbol: String): List<Waypoint>

    suspend fun getWaypoint(systemSymbol: String, waypointSymbol: String): Waypoint

    fun observeWaypoint(waypointSymbol: String): Flow<Waypoint?>
}
