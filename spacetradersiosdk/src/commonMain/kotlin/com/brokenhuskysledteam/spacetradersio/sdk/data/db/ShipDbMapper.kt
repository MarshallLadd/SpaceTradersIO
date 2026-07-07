package com.brokenhuskysledteam.spacetradersio.sdk.data.db

import com.brokenhuskysledteam.spacetradersio.sdk.data.db.Ship as DbShip
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.CargoItem
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Cooldown
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipCargo
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRoute
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRouteWaypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipRegistration
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavFlightMode
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipRole
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import kotlin.time.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * JSON codec for the `cargo_inventory` column. The ship table stores the whole cargo
 * inventory list as a single JSON array rather than a child table, because cargo inventory
 * is a value list bound 1:1 to a ship row and is always read/written with the ship's cargo
 * counts (never queried independently). `ignoreUnknownKeys` future-proofs against extra
 * fields being added to persisted entries.
 */
private val cargoInventoryJson = Json { ignoreUnknownKeys = true }

/**
 * Reconstructs a [Ship] domain model from a flat [DbShip] database row.
 *
 * **Pattern:** DB mapper for a nested domain model. The [Ship] domain type is
 * hierarchical — it contains [ShipRegistration], [ShipNav], [ShipNavRoute],
 * two [ShipNavRouteWaypoint]s, [ShipCargo], [ShipFuel], and [Cooldown]. Because
 * SQLite is relational and flat, all of these sub-objects are stored as columns on a
 * single `ship` table (prefixed by their logical group, e.g. `nav_*`, `route_*`).
 * This function is the single place responsible for re-nesting those flat columns
 * back into the domain's object graph.
 *
 * **In this project:** Called by `ShipRepository` when serving ships from the local
 * SQLDelight cache. The parallel path, `ShipDto.toDomain()` (in `api/mapper/`),
 * produces the same [Ship] type from the JSON response. The domain layer uses [Ship]
 * uniformly without caring about the source.
 *
 * **Timestamp handling:** SQLite has no native timestamp type. Departure and arrival
 * times are stored as ISO-8601 strings and parsed back to [Instant] here. The
 * cooldown expiration is nullable — a ship with no active cooldown stores `NULL` in
 * the column, surfaced here as `null` on [Cooldown.expiration].
 *
 * **Enum handling:** String enum values (role, status, flight mode, waypoint type)
 * are stored by `.name` and decoded via each enum's `fromString` factory. This
 * decouples the DB representation from any future Kotlin enum reordering.
 */
fun DbShip.toDomain(): Ship = Ship(
    symbol = symbol,
    registration = ShipRegistration(
        // Enum stored as its name string; fromString maps it back to the sealed type.
        role = ShipRole.fromString(reg_role),
        factionSymbol = reg_faction_symbol
    ),
    nav = ShipNav(
        systemSymbol = nav_system_symbol,
        waypointSymbol = nav_waypoint_symbol,
        status = ShipNavStatus.fromString(nav_status),
        flightMode = ShipNavFlightMode.fromString(nav_flight_mode),
        route = ShipNavRoute(
            origin = ShipNavRouteWaypoint(
                symbol = route_origin_symbol,
                type = WaypointType.fromString(route_origin_type),
                systemSymbol = route_origin_system,
                // Coordinates stored as Long; domain model uses Int.
                x = route_origin_x.toInt(),
                y = route_origin_y.toInt()
            ),
            destination = ShipNavRouteWaypoint(
                symbol = route_dest_symbol,
                type = WaypointType.fromString(route_dest_type),
                systemSymbol = route_dest_system,
                x = route_dest_x.toInt(),
                y = route_dest_y.toInt()
            ),
            // ISO-8601 strings → kotlin.time.Instant for type-safe time arithmetic.
            departureTime = Instant.parse(route_departure),
            arrivalTime = Instant.parse(route_arrival)
        )
    ),
    cargo = ShipCargo(
        units = cargo_units.toInt(),
        capacity = cargo_capacity.toInt(),
        // Inventory is stored as a JSON array; an empty/absent hold round-trips as "[]".
        inventory = cargoInventoryJson.decodeFromString<List<CargoItem>>(cargo_inventory)
    ),
    fuel = ShipFuel(current = fuel_current.toInt(), capacity = fuel_capacity.toInt()),
    frameName = frame_name,
    cooldown = Cooldown(
        // Cooldown always belongs to this ship, so reuse the symbol already in scope.
        shipSymbol = symbol,
        totalSeconds = cooldown_total_seconds.toInt(),
        remainingSeconds = cooldown_remaining_seconds.toInt(),
        // NULL in the DB means the cooldown has no expiration (ship is not on cooldown).
        expiration = cooldown_expiration?.let { Instant.parse(it) }
    )
)

/**
 * Upserts [ship] into the database, writing every column of the `ship` table.
 *
 * **Pattern:** Full-entity upsert extension on the generated Queries class. Use this
 * when a complete [Ship] is returned by the API (e.g. on the ship list endpoint).
 * It replaces the entire row, so all sub-object columns are always in sync.
 *
 * **In this project:** Called by `ShipRepository` after fetching the full ship list
 * from the API. Columns that are domain `Int`s are widened to `Long` for SQLite's
 * INTEGER affinity; timestamps are serialized to ISO-8601 strings; nullable
 * [Cooldown.expiration] becomes a nullable `String` column.
 *
 * @param ship The complete domain model to persist or overwrite.
 */
fun ShipQueries.upsertShip(ship: Ship) {
    upsertShip(
        symbol = ship.symbol,
        reg_role = ship.registration.role.name,
        reg_faction_symbol = ship.registration.factionSymbol,
        nav_system_symbol = ship.nav.systemSymbol,
        nav_waypoint_symbol = ship.nav.waypointSymbol,
        nav_status = ship.nav.status.name,
        nav_flight_mode = ship.nav.flightMode.name,
        route_origin_symbol = ship.nav.route.origin.symbol,
        route_origin_type = ship.nav.route.origin.type.name,
        route_origin_system = ship.nav.route.origin.systemSymbol,
        route_origin_x = ship.nav.route.origin.x.toLong(),
        route_origin_y = ship.nav.route.origin.y.toLong(),
        route_dest_symbol = ship.nav.route.destination.symbol,
        route_dest_type = ship.nav.route.destination.type.name,
        route_dest_system = ship.nav.route.destination.systemSymbol,
        route_dest_x = ship.nav.route.destination.x.toLong(),
        route_dest_y = ship.nav.route.destination.y.toLong(),
        // Instant.toString() produces a round-trippable ISO-8601 string.
        route_departure = ship.nav.route.departureTime.toString(),
        route_arrival = ship.nav.route.arrivalTime.toString(),
        cargo_units = ship.cargo.units.toLong(),
        cargo_capacity = ship.cargo.capacity.toLong(),
        cargo_inventory = cargoInventoryJson.encodeToString(ship.cargo.inventory),
        fuel_current = ship.fuel.current.toLong(),
        fuel_capacity = ship.fuel.capacity.toLong(),
        frame_name = ship.frameName,
        cooldown_total_seconds = ship.cooldown.totalSeconds.toLong(),
        cooldown_remaining_seconds = ship.cooldown.remainingSeconds.toLong(),
        // Store null when there is no active cooldown expiration.
        cooldown_expiration = ship.cooldown.expiration?.toString()
    )
}

/**
 * Updates only the navigation columns for an existing ship row.
 *
 * **Pattern:** Partial update extension. Action endpoints in the SpaceTraders API
 * (e.g. orbit, dock, navigate) return only the sub-object that changed — not the
 * full ship. A full `upsertShip` would require fetching the rest of the ship first.
 * Instead, the `.sq` schema defines a targeted `UPDATE` statement (`updateShipNav`)
 * and this extension maps the [ShipNav] domain object to its parameters, leaving all
 * other columns untouched.
 *
 * **In this project:** Called by `ShipRepository` after orbit, dock, or navigate
 * API calls. The same principle applies to any future partial-update extensions
 * (e.g. `updateShipCargo`, `updateShipFuel`) — one extension per API action that
 * returns a partial payload.
 *
 * @param nav The updated navigation state to write.
 * @param shipSymbol The primary key identifying which row to update.
 */
fun ShipQueries.updateShipNav(nav: ShipNav, shipSymbol: String) {
    updateShipNav(
        nav_system_symbol = nav.systemSymbol,
        nav_waypoint_symbol = nav.waypointSymbol,
        nav_status = nav.status.name,
        nav_flight_mode = nav.flightMode.name,
        route_origin_symbol = nav.route.origin.symbol,
        route_origin_type = nav.route.origin.type.name,
        route_origin_system = nav.route.origin.systemSymbol,
        route_origin_x = nav.route.origin.x.toLong(),
        route_origin_y = nav.route.origin.y.toLong(),
        route_dest_symbol = nav.route.destination.symbol,
        route_dest_type = nav.route.destination.type.name,
        route_dest_system = nav.route.destination.systemSymbol,
        route_dest_x = nav.route.destination.x.toLong(),
        route_dest_y = nav.route.destination.y.toLong(),
        route_departure = nav.route.departureTime.toString(),
        route_arrival = nav.route.arrivalTime.toString(),
        // Used in the WHERE clause — must match the primary key of the target row.
        symbol = shipSymbol
    )
}

/**
 * Updates only the cargo columns for an existing ship row.
 *
 * **Pattern:** Partial update extension. Cargo-changing action endpoints (buy, sell, jettison,
 * extract, transfer) return only the updated [ShipCargo] sub-object, not the full ship. This
 * extension writes the aggregate counts plus the JSON-serialized inventory, leaving all other
 * ship columns untouched. Centralising the JSON encoding here (rather than in the repository)
 * keeps persistence concerns in the DB layer — the repository just passes a domain [ShipCargo].
 *
 * @param cargo The updated cargo state to write, including its full inventory.
 * @param shipSymbol The primary key identifying which row to update.
 */
fun ShipQueries.updateShipCargo(cargo: ShipCargo, shipSymbol: String) {
    updateShipCargo(
        cargo_units = cargo.units.toLong(),
        cargo_capacity = cargo.capacity.toLong(),
        cargo_inventory = cargoInventoryJson.encodeToString(cargo.inventory),
        symbol = shipSymbol
    )
}
