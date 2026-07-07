package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.SpaceTradersDatabase
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.updateShipCargo
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.updateShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.upsertShip
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Cooldown
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipCargo
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Concrete implementation of [FleetRepository] with three distinct responsibilities:
 *
 * 1. **Reactive DB-backed ship streams** — [observeShips] and [observeShip] expose
 *    SQLDelight-backed [Flow]s that emit automatically whenever the underlying ship
 *    rows change, without any manual ViewModel notification.
 *
 * 2. **Partial ship state updates** — SpaceTraders action endpoints (orbit, dock,
 *    navigate, refuel, extract, etc.) return only the sub-object that changed, not
 *    the full ship. The `update*` methods apply surgical column-level DB updates so
 *    the rest of the ship row remains intact while the reactive flow still delivers
 *    the change to all observers.
 *
 * 3. **Automatic transit refresh timer scheduling** — When a ship is [ShipNavStatus.IN_TRANSIT]
 *    or has an active cooldown, a timer is registered with [RefreshScheduler] to
 *    re-fetch ship state once the event has resolved. This keeps local data in sync
 *    without requiring the user to manually refresh.
 *
 * **Pattern:** Offline-first reactive repository + auto-refresh timer. To apply in a
 * new project: expose [Flow]s backed by a local database, write all mutations to the
 * database so observers update automatically, and register timers for any server-side
 * time-bounded events (transit, cooldowns) so that state stays current even when the
 * user takes no action.
 *
 * **In this project:** This is the most complex repository because ships have multiple
 * independently-updatable sub-objects (nav, fuel, cargo, cooldown) and two categories
 * of time-bounded state that must auto-resolve.
 *
 * @param fleetApi         Network layer for the SpaceTraders `/my/ships` endpoints.
 * @param database         SQLDelight database holding the `ship` table.
 * @param refreshScheduler Coroutine-based timer that fires [refreshMyShip] after transit
 *                         or cooldown expiry.
 */
class FleetRepositoryImpl(
    private val fleetApi: FleetApi,
    private val database: SpaceTradersDatabase,
    private val refreshScheduler: RefreshScheduler
) : FleetRepository {

    // Shorthand accessor — avoids repeating `database.shipQueries` throughout.
    private val queries get() = database.shipQueries

    /**
     * Returns a [Flow] that emits the full list of owned ships whenever any ship row
     * changes in the database.
     *
     * `mapToList` converts each query result set into a `List` of DB entities.
     * `Dispatchers.Default` is used because `Dispatchers.IO` is JVM-only and not
     * available in `commonMain`.
     *
     * @return A cold [Flow] backed by the SQLDelight `selectAllShips` query.
     */
    override fun observeShips(): Flow<List<Ship>> =
        queries.selectAllShips()
            .asFlow()
            .mapToList(Dispatchers.Default) // Dispatchers.IO is JVM-only; Default is the correct KMP substitute.
            .map { rows -> rows.map { it.toDomain() } }

    /**
     * Returns a [Flow] that emits a single [Ship] (or `null`) whenever the row for
     * [shipSymbol] changes in the database.
     *
     * Used by the ship detail screen to react to partial updates (e.g. fuel level
     * change after refuelling) without re-fetching the entire fleet.
     *
     * @param shipSymbol The unique ship identifier (e.g. `"MYAGENT-1"`).
     * @return A cold [Flow] backed by the SQLDelight `selectShipBySymbol` query;
     *   emits `null` if the ship is not found.
     */
    override fun observeShip(shipSymbol: String): Flow<Ship?> =
        queries.selectShipBySymbol(shipSymbol)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.toDomain() }

    /**
     * Fetches a page of owned ships from the network, persists them all in a single
     * database transaction, and registers auto-refresh timers for any ships that are
     * currently in transit or cooling down.
     *
     * The database transaction groups all upserts into a single write — this prevents
     * observers from receiving partial fleet updates mid-batch.
     *
     * @param page  Page number (1-based) to request from the API.
     * @param limit Number of ships per page (API max: 20).
     */
    override suspend fun refreshMyShips(page: Int, limit: Int) {
        val ships = fleetApi.getMyShips(page, limit).data.map { it.toDomain() }
        // Wrap all upserts in a transaction so the DB (and therefore observers) sees
        // the entire updated fleet at once rather than one ship at a time.
        database.transaction {
            ships.forEach { queries.upsertShip(it) }
        }
        // Timer registration must happen after the transaction commits because
        // registerTimersForShip may immediately trigger a refresh if a ship's
        // arrivalTime is already in the past.
        ships.forEach { registerTimersForShip(it) }
    }

    /**
     * Fetches the current state of a single ship from the network, persists it, and
     * registers auto-refresh timers if the ship is in transit or has an active cooldown.
     *
     * This is the action invoked by [RefreshScheduler] when a transit or cooldown timer
     * fires — it is the "arrival callback" that brings local state back in sync after a
     * time-bounded server event resolves.
     *
     * @param shipSymbol The unique ship identifier to refresh.
     */
    override suspend fun refreshMyShip(shipSymbol: String) {
        val ship = fleetApi.getMyShip(shipSymbol).toDomain()
        queries.upsertShip(ship)
        registerTimersForShip(ship)
    }

    /**
     * Persists [ship] to the database without making a network call.
     *
     * Used during the registration flow when the API has already returned the initial
     * ship data and a separate network round-trip is unnecessary.
     *
     * @param ship The [Ship] to write. The `symbol` column acts as the primary key.
     */
    override suspend fun saveShip(ship: Ship) {
        queries.upsertShip(ship)
    }

    /**
     * Applies a surgical update to the navigation columns for [shipSymbol] in the
     * database and, if the ship is now [ShipNavStatus.IN_TRANSIT], registers a refresh
     * timer for its arrival.
     *
     * SpaceTraders navigation endpoints (navigate, orbit, dock, warp) return a [ShipNav]
     * object rather than the full ship. Rather than storing stale full-ship data, this
     * method updates only the nav columns, leaving fuel, cargo, and cooldown intact.
     *
     * The `transit:$shipSymbol` timer ID allows [RefreshScheduler.cancelByPrefix] to
     * cancel it later if needed (e.g. the ship is docked before the timer fires, which
     * cannot happen in normal gameplay but is a useful safety invariant in tests).
     *
     * @param shipSymbol The unique ship identifier whose nav state changed.
     * @param nav        The updated [ShipNav] returned by the action endpoint.
     */
    override suspend fun updateShipNav(shipSymbol: String, nav: ShipNav) {
        queries.updateShipNav(nav, shipSymbol)
        if (nav.status == ShipNavStatus.IN_TRANSIT) {
            // Schedule a refresh 1 second after arrivalTime so the local DB reflects
            // the DOCKED/IN_ORBIT status as soon as the transit resolves on the server.
            refreshScheduler.schedule(
                id = "transit:$shipSymbol",
                expiresAt = nav.route.arrivalTime,
                action = { refreshMyShip(shipSymbol) }
            )
        }
    }

    /**
     * Applies a surgical update to the fuel columns for [shipSymbol] in the database.
     *
     * The refuel endpoint returns a [ShipFuel] object. Only the `fuel_current` and
     * `fuel_capacity` columns are touched; all other ship columns remain as-is.
     *
     * @param shipSymbol The unique ship identifier whose fuel level changed.
     * @param fuel       The updated [ShipFuel] returned by the refuel endpoint.
     */
    override suspend fun updateShipFuel(shipSymbol: String, fuel: ShipFuel) {
        queries.updateShipFuel(
            fuel_current = fuel.current.toLong(),
            fuel_capacity = fuel.capacity.toLong(),
            symbol = shipSymbol
        )
    }

    /**
     * Applies a surgical update to the cargo columns for [shipSymbol] in the database.
     *
     * Cargo endpoints (jettison, transfer, extract) return a [ShipCargo] object.
     * Only `cargo_units` and `cargo_capacity` are updated; all other columns are left
     * intact so a cargo change does not inadvertently overwrite the current nav state.
     *
     * @param shipSymbol The unique ship identifier whose cargo changed.
     * @param cargo      The updated [ShipCargo] returned by the cargo action endpoint.
     */
    override suspend fun updateShipCargo(shipSymbol: String, cargo: ShipCargo) {
        // Delegates to the DB-layer extension, which serializes the inventory to JSON.
        queries.updateShipCargo(cargo, shipSymbol)
    }

    /**
     * Applies a surgical update to the cooldown columns for [shipSymbol] in the database.
     *
     * Survey and extract endpoints return a [Cooldown] object. The `cooldown_expiration`
     * is stored as a nullable ISO-8601 string — `null` means no active cooldown. Note that
     * a corresponding timer is NOT registered here; cooldown timers are registered by
     * [registerTimersForShip] when the full ship state is written, not on a partial update,
     * because a partial cooldown update does not include all the data needed to verify
     * the ship's overall state.
     *
     * @param shipSymbol The unique ship identifier whose cooldown changed.
     * @param cooldown   The updated [Cooldown] returned by the action endpoint.
     */
    override suspend fun updateShipCooldown(shipSymbol: String, cooldown: Cooldown) {
        queries.updateShipCooldown(
            cooldown_total_seconds = cooldown.totalSeconds.toLong(),
            cooldown_remaining_seconds = cooldown.remainingSeconds.toLong(),
            cooldown_expiration = cooldown.expiration?.toString(),
            symbol = shipSymbol
        )
        // Schedule an auto-refresh when the cooldown expires (mirrors the transit timer in
        // updateShipNav). This activates the RefreshScheduler for action cooldowns — e.g. after
        // an extraction the ship refreshes ~1s after the cooldown clears, so the UI reflects that
        // the ship is ready to act again without the user manually refreshing.
        cooldown.expiration?.let { expiry ->
            refreshScheduler.schedule(
                id = "cooldown:$shipSymbol",
                expiresAt = expiry,
                action = { refreshMyShip(shipSymbol) }
            )
        }
    }

    /**
     * Deletes all rows from the `ship` table.
     *
     * Called during logout to ensure no player ship data persists after the session ends.
     * All [observeShips] and [observeShip] flows will emit empty/null values immediately.
     */
    override suspend fun clearAll() {
        queries.deleteAllShips()
    }

    /**
     * Registers [RefreshScheduler] timers for any time-bounded state on [ship].
     *
     * **Auto-refresh timer pattern:** SpaceTraders has two categories of server-side
     * time-bounded events that must be auto-resolved:
     *
     * - **Transit:** When `ship.nav.status == IN_TRANSIT`, a timer fires [refreshMyShip]
     *   1 second after `ship.nav.route.arrivalTime`. On arrival the ship's status on the
     *   server changes to `IN_ORBIT`, and the refresh call brings local state in sync.
     *
     * - **Cooldown:** When `ship.cooldown.expiration` is non-null, a timer fires
     *   [refreshMyShip] 1 second after that instant. After the cooldown clears, the
     *   server marks the ship as ready, and the refresh reflects that locally.
     *
     * Timer IDs are prefixed with the ship symbol (`"transit:$symbol"` and
     * `"cooldown:$symbol"`). This naming convention allows
     * `refreshScheduler.cancelByPrefix(ship.symbol)` to cancel both timers for a given
     * ship in a single call — useful if, for example, the session is torn down before
     * a timer fires.
     *
     * If the ship is not in transit and has no active cooldown, this function is a no-op.
     *
     * @param ship The fully-hydrated [Ship] whose time-bounded state should be scheduled.
     */
    private fun registerTimersForShip(ship: Ship) {
        if (ship.nav.status == ShipNavStatus.IN_TRANSIT) {
            // "transit:" prefix enables cancelByPrefix(ship.symbol) to cancel both
            // this and any cooldown timer for the same ship in one call.
            refreshScheduler.schedule(
                id = "transit:${ship.symbol}",
                expiresAt = ship.nav.route.arrivalTime,
                action = { refreshMyShip(ship.symbol) }
            )
        }
        ship.cooldown.expiration?.let { expiry ->
            // Only register if an expiration instant exists; null means no active cooldown.
            refreshScheduler.schedule(
                id = "cooldown:${ship.symbol}",
                expiresAt = expiry,
                action = { refreshMyShip(ship.symbol) }
            )
        }
    }
}
