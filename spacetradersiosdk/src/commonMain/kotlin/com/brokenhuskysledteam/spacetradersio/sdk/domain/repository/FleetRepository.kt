package com.brokenhuskysledteam.spacetradersio.sdk.domain.repository

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Cooldown
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipCargo
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav

/**
 * Defines all data operations for the player's fleet of ships.
 *
 * **Pattern:** Repository interface in the domain layer. Placing this interface in `domain/`
 * means ViewModels and use cases declare their dependency on an abstraction — never on the
 * concrete `FleetRepositoryImpl`. To apply this in a new project: list every fleet data
 * operation your domain needs here, then implement the storage and network details in the
 * `data/` layer without touching this file.
 *
 * **Benefits of the interface boundary:**
 * - Tests inject a hand-written fake (e.g. `FakeFleetRepository`) that stores ships in a
 *   `MutableStateFlow` — no mocking library required.
 * - The domain layer has no dependency on SQLDelight, Ktor, or any platform API.
 * - The same interface can be backed by a different implementation per platform or environment.
 *
 * **In this project:** `FleetRepositoryImpl` uses SQLDelight for offline storage, Ktor for
 * network calls, and a `RefreshScheduler` to auto-refresh ships when transit or cooldown
 * timers expire. None of those details are visible through this interface.
 */
interface FleetRepository {

    /**
     * Returns a hot [kotlinx.coroutines.flow.Flow] that emits the full list of locally-cached
     * ships and re-emits whenever any ship row in the database changes.
     *
     * **Pattern:** Reactive offline-first observation over a collection. The ViewModel
     * collects this flow once and never polls. In the implementation, any write to the ships
     * table (upsert, partial update, or delete) triggers a new emission automatically via
     * SQLDelight's `asFlow().mapToList()`.
     *
     * @return A [kotlinx.coroutines.flow.Flow] emitting the current list of all ships. Emits
     *   an empty list when the database contains no ship rows (e.g. before the first
     *   [refreshMyShips] call or after [clearAll]).
     */
    fun observeShips(): kotlinx.coroutines.flow.Flow<List<Ship>>

    /**
     * Returns a hot [kotlinx.coroutines.flow.Flow] that emits the locally-cached [Ship] with
     * the given symbol and re-emits whenever that specific row changes.
     *
     * **Pattern:** Reactive observation of a single entity by key. Backed by a SQLDelight
     * `selectShipBySymbol` query via `mapToOneOrNull`, so any update to that row — even a
     * partial update such as [updateShipNav] — propagates to all collectors automatically.
     *
     * @param shipSymbol The unique ship identifier (e.g. `"AGENT-1"`).
     * @return A [kotlinx.coroutines.flow.Flow] that emits `null` when no ship with the given
     *   symbol exists in the database, or the current [Ship] once one has been stored.
     */
    fun observeShip(shipSymbol: String): kotlinx.coroutines.flow.Flow<Ship?>

    /**
     * Fetches a page of the player's ships from the network and upserts all results into the
     * local database.
     *
     * **Pattern:** Paginated network-then-cache write. The call is intentionally fire-and-
     * forget from the caller's perspective: [observeShips] collectors receive the new data
     * automatically once the DB writes complete. Pagination defaults are chosen to match the
     * API's recommended page size.
     *
     * @param page  The 1-based page number to fetch (default: `1`).
     * @param limit The number of ships per page (default: `20`, max: `20` per API spec).
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.domain.error.SpaceTradersApiException
     *   if the API call fails.
     */
    suspend fun refreshMyShips(page: Int = 1, limit: Int = 20)

    /**
     * Fetches a single ship by symbol from the network and upserts it into the local database.
     *
     * Use this when you need a guaranteed fresh view of one specific ship rather than the
     * entire fleet. In the implementation, the result also registers any pending transit or
     * cooldown refresh timers for the ship.
     *
     * @param shipSymbol The unique ship identifier to refresh.
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.domain.error.SpaceTradersApiException
     *   if the API call fails or the symbol is not found.
     */
    suspend fun refreshMyShip(shipSymbol: String)

    /**
     * Writes a complete [Ship] record directly to the local database without a network call.
     *
     * Used when a ship object is already available (e.g. obtained as part of another API
     * response) and a full refresh round-trip would be wasteful.
     *
     * @param ship The [Ship] to persist.
     */
    suspend fun saveShip(ship: Ship)

    /**
     * Applies a partial navigation update for the given ship without re-fetching the full
     * ship record.
     *
     * **Why partial updates exist:** SpaceTraders action endpoints (orbit, dock, navigate)
     * return only the portion of the ship that changed — they do not return the complete ship
     * object. Issuing a full [refreshMyShip] after every action would waste a network round-
     * trip. Instead, use cases call this method with the partial response body so the DB
     * (and therefore [observeShip] collectors) reflects the change immediately.
     *
     * In the implementation, if the new [nav] indicates `IN_TRANSIT` status, a refresh timer
     * is automatically scheduled to fire when the ship arrives at its destination.
     *
     * @param shipSymbol The unique ship identifier.
     * @param nav        The updated [ShipNav] containing the new status, route, and waypoint.
     */
    suspend fun updateShipNav(shipSymbol: String, nav: ShipNav)

    /**
     * Applies a partial fuel update for the given ship without re-fetching the full record.
     *
     * The refuel endpoint returns a [ShipFuel] response body; this method applies it locally
     * so [observeShip] collectors see the updated fuel level without an extra network call.
     *
     * @param shipSymbol The unique ship identifier.
     * @param fuel       The updated [ShipFuel] containing current and capacity values.
     */
    suspend fun updateShipFuel(shipSymbol: String, fuel: ShipFuel)

    /**
     * Applies a partial cargo update for the given ship without re-fetching the full record.
     *
     * Cargo-changing endpoints (buy, sell, jettison, etc.) return a [ShipCargo] response body;
     * this method applies it locally so [observeShip] collectors stay current.
     *
     * @param shipSymbol The unique ship identifier.
     * @param cargo      The updated [ShipCargo] containing units used and capacity.
     */
    suspend fun updateShipCargo(shipSymbol: String, cargo: ShipCargo)

    /**
     * Applies a partial cooldown update for the given ship without re-fetching the full record.
     *
     * Action endpoints that impose a cooldown (e.g. survey, extract) return a [Cooldown]
     * response body. This method stores it locally so the UI can display the cooldown
     * countdown without a second network call.
     *
     * @param shipSymbol The unique ship identifier.
     * @param cooldown   The new [Cooldown] containing total seconds, remaining seconds, and
     *   optional expiration timestamp.
     */
    suspend fun updateShipCooldown(shipSymbol: String, cooldown: Cooldown)

    /**
     * Deletes all ship data from the local database.
     *
     * Called during logout to ensure no fleet data persists after the session ends. After this
     * call, [observeShips] emits an empty list and [observeShip] emits `null` for any symbol.
     */
    suspend fun clearAll()
}
