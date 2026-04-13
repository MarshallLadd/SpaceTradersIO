package com.brokenhuskysledteam.spacetradersio.sdk.data.db

import com.brokenhuskysledteam.spacetradersio.sdk.data.db.Ship as DbShip
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

fun DbShip.toDomain(): Ship = Ship(
    symbol = symbol,
    registration = ShipRegistration(
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
            departureTime = Instant.parse(route_departure),
            arrivalTime = Instant.parse(route_arrival)
        )
    ),
    cargo = ShipCargo(units = cargo_units.toInt(), capacity = cargo_capacity.toInt()),
    fuel = ShipFuel(current = fuel_current.toInt(), capacity = fuel_capacity.toInt()),
    frameName = frame_name,
    cooldown = Cooldown(
        shipSymbol = symbol,
        totalSeconds = cooldown_total_seconds.toInt(),
        remainingSeconds = cooldown_remaining_seconds.toInt(),
        expiration = cooldown_expiration?.let { Instant.parse(it) }
    )
)

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
        route_departure = ship.nav.route.departureTime.toString(),
        route_arrival = ship.nav.route.arrivalTime.toString(),
        cargo_units = ship.cargo.units.toLong(),
        cargo_capacity = ship.cargo.capacity.toLong(),
        fuel_current = ship.fuel.current.toLong(),
        fuel_capacity = ship.fuel.capacity.toLong(),
        frame_name = ship.frameName,
        cooldown_total_seconds = ship.cooldown.totalSeconds.toLong(),
        cooldown_remaining_seconds = ship.cooldown.remainingSeconds.toLong(),
        cooldown_expiration = ship.cooldown.expiration?.toString()
    )
}

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
        symbol = shipSymbol
    )
}
