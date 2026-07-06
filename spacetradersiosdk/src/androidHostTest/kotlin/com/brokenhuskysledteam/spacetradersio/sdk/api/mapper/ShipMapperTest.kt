package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.CargoItemDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.CooldownDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipCargoDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipFrameSummaryDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipFuelDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavRouteDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavRouteWaypointDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipRegistrationDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavFlightMode
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipRole
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class ShipMapperTest {

    private val departureTimeStr = "2025-06-01T10:00:00.000Z"
    private val arrivalTimeStr = "2025-06-01T10:30:00.000Z"

    private fun waypointDto(
        symbol: String = "X1-DF55-17335A",
        type: String = "PLANET",
        systemSymbol: String = "X1-DF55",
        x: Int = -21,
        y: Int = -16
    ) = ShipNavRouteWaypointDto(
        symbol = symbol,
        type = type,
        systemSymbol = systemSymbol,
        x = x,
        y = y
    )

    private fun routeDto() = ShipNavRouteDto(
        destination = waypointDto(),
        origin = waypointDto(symbol = "X1-DF55-20250Z", type = "MOON", x = -15, y = 12),
        departureTime = departureTimeStr,
        arrival = arrivalTimeStr
    )

    private fun navDto(status: String = "DOCKED", flightMode: String = "CRUISE") = ShipNavDto(
        systemSymbol = "X1-DF55",
        waypointSymbol = "X1-DF55-20250Z",
        route = routeDto(),
        status = status,
        flightMode = flightMode
    )

    private fun fullDto() = ShipDto(
        symbol = "COMMANDER-1",
        registration = ShipRegistrationDto(name = "Commander One", factionSymbol = "COSMIC", role = "COMMAND"),
        frame = ShipFrameSummaryDto(symbol = "FRAME_SHUTTLE", name = "Shuttle Frame"),
        nav = navDto(),
        cargo = ShipCargoDto(capacity = 40, units = 10),
        fuel = ShipFuelDto(current = 340, capacity = 400),
        cooldown = CooldownDto(shipSymbol = "COMMANDER-1", totalSeconds = 0, remainingSeconds = 0, expiration = null)
    )

    // ── Ship ─────────────────────────────────────────────────────────────────

    @Test
    fun ship_toDomain_symbolMapsCorrectly() {
        assertEquals("COMMANDER-1", fullDto().toDomain().symbol)
    }

    @Test
    fun ship_toDomain_frameNameMapsCorrectly() {
        assertEquals("Shuttle Frame", fullDto().toDomain().frameName)
    }

    // ── ShipRegistration ─────────────────────────────────────────────────────

    @Test
    fun registration_toDomain_roleMapsCorrectly() {
        assertEquals(ShipRole.COMMAND, fullDto().toDomain().registration.role)
    }

    @Test
    fun registration_toDomain_factionSymbolMapsCorrectly() {
        assertEquals("COSMIC", fullDto().toDomain().registration.factionSymbol)
    }

    @Test
    fun registration_toDomain_unknownRole_fallsBackToCommand() {
        val dto = fullDto().copy(
            registration = ShipRegistrationDto(name = "X", factionSymbol = "COSMIC", role = "FUTURE_ROLE")
        )
        assertEquals(ShipRole.COMMAND, dto.toDomain().registration.role)
    }

    // ── ShipNav ──────────────────────────────────────────────────────────────

    @Test
    fun nav_toDomain_systemSymbolMapsCorrectly() {
        assertEquals("X1-DF55", fullDto().toDomain().nav.systemSymbol)
    }

    @Test
    fun nav_toDomain_waypointSymbolMapsCorrectly() {
        assertEquals("X1-DF55-20250Z", fullDto().toDomain().nav.waypointSymbol)
    }

    @Test
    fun nav_toDomain_statusDockedMapsCorrectly() {
        assertEquals(ShipNavStatus.DOCKED, navDto("DOCKED").toDomain().status)
    }

    @Test
    fun nav_toDomain_statusInOrbitMapsCorrectly() {
        assertEquals(ShipNavStatus.IN_ORBIT, navDto("IN_ORBIT").toDomain().status)
    }

    @Test
    fun nav_toDomain_statusInTransitMapsCorrectly() {
        assertEquals(ShipNavStatus.IN_TRANSIT, navDto("IN_TRANSIT").toDomain().status)
    }

    @Test
    fun nav_toDomain_unknownStatus_fallsBackToDocked() {
        assertEquals(ShipNavStatus.DOCKED, navDto("FUTURE_STATUS").toDomain().status)
    }

    @Test
    fun nav_toDomain_flightModeCruiseMapsCorrectly() {
        assertEquals(ShipNavFlightMode.CRUISE, navDto(flightMode = "CRUISE").toDomain().flightMode)
    }

    @Test
    fun nav_toDomain_flightModeBurnMapsCorrectly() {
        assertEquals(ShipNavFlightMode.BURN, navDto(flightMode = "BURN").toDomain().flightMode)
    }

    @Test
    fun nav_toDomain_unknownFlightMode_fallsBackToCruise() {
        assertEquals(ShipNavFlightMode.CRUISE, navDto(flightMode = "WARP").toDomain().flightMode)
    }

    // ── ShipNavRoute ─────────────────────────────────────────────────────────

    @Test
    fun route_toDomain_departureTimeParsedCorrectly() {
        assertEquals(Instant.parse(departureTimeStr), fullDto().toDomain().nav.route.departureTime)
    }

    @Test
    fun route_toDomain_arrivalTimeParsedFromArrivalField() {
        assertEquals(Instant.parse(arrivalTimeStr), fullDto().toDomain().nav.route.arrivalTime)
    }

    @Test
    fun route_destination_symbolMapsCorrectly() {
        assertEquals("X1-DF55-17335A", fullDto().toDomain().nav.route.destination.symbol)
    }

    @Test
    fun route_destination_typePlanetMapsCorrectly() {
        assertEquals(WaypointType.PLANET, fullDto().toDomain().nav.route.destination.type)
    }

    @Test
    fun route_destination_coordinatesMapsCorrectly() {
        val dest = fullDto().toDomain().nav.route.destination
        assertEquals(-21, dest.x)
        assertEquals(-16, dest.y)
    }

    @Test
    fun route_origin_typeMoonMapsCorrectly() {
        assertEquals(WaypointType.MOON, fullDto().toDomain().nav.route.origin.type)
    }

    @Test
    fun route_waypoint_unknownType_fallsBackToPlanet() {
        val dto = fullDto().copy(
            nav = navDto().copy(
                route = routeDto().copy(
                    destination = waypointDto(type = "FUTURE_TYPE")
                )
            )
        )
        assertEquals(WaypointType.PLANET, dto.toDomain().nav.route.destination.type)
    }

    // ── ShipFuel ─────────────────────────────────────────────────────────────

    @Test
    fun fuel_toDomain_currentMapsCorrectly() {
        assertEquals(340, fullDto().toDomain().fuel.current)
    }

    @Test
    fun fuel_toDomain_capacityMapsCorrectly() {
        assertEquals(400, fullDto().toDomain().fuel.capacity)
    }

    // ── ShipCargo ────────────────────────────────────────────────────────────

    @Test
    fun cargo_toDomain_unitsMapsCorrectly() {
        assertEquals(10, fullDto().toDomain().cargo.units)
    }

    @Test
    fun cargo_toDomain_capacityMapsCorrectly() {
        assertEquals(40, fullDto().toDomain().cargo.capacity)
    }

    @Test
    fun cargo_toDomain_emptyInventoryByDefault() {
        assertEquals(emptyList(), fullDto().toDomain().cargo.inventory)
    }

    @Test
    fun cargo_toDomain_inventoryMapsEachItem() {
        val dto = fullDto().copy(
            cargo = ShipCargoDto(
                capacity = 40,
                units = 15,
                inventory = listOf(
                    CargoItemDto("IRON_ORE", "Iron Ore", "Raw iron ore.", 12),
                    CargoItemDto("FUEL", "Fuel", "High-grade fuel.", 3)
                )
            )
        )
        val inventory = dto.toDomain().cargo.inventory
        assertEquals(2, inventory.size)
        assertEquals("IRON_ORE", inventory[0].symbol)
        assertEquals("Iron Ore", inventory[0].name)
        assertEquals("Raw iron ore.", inventory[0].description)
        assertEquals(12, inventory[0].units)
        assertEquals("FUEL", inventory[1].symbol)
        assertEquals(3, inventory[1].units)
    }

    // ── Cooldown ─────────────────────────────────────────────────────────────

    @Test
    fun cooldown_toDomain_shipSymbolMapsCorrectly() {
        assertEquals("COMMANDER-1", fullDto().toDomain().cooldown.shipSymbol)
    }

    @Test
    fun cooldown_toDomain_totalSecondsMapsCorrectly() {
        assertEquals(0, fullDto().toDomain().cooldown.totalSeconds)
    }

    @Test
    fun cooldown_toDomain_remainingSecondsMapsCorrectly() {
        assertEquals(0, fullDto().toDomain().cooldown.remainingSeconds)
    }

    @Test
    fun cooldown_toDomain_nullExpirationMapsToNull() {
        assertNull(fullDto().toDomain().cooldown.expiration)
    }

    @Test
    fun cooldown_toDomain_expirationParsedWhenPresent() {
        val expirationStr = "2025-06-01T11:00:00.000Z"
        val dto = fullDto().copy(
            cooldown = CooldownDto(
                shipSymbol = "COMMANDER-1",
                totalSeconds = 60,
                remainingSeconds = 30,
                expiration = expirationStr
            )
        )
        assertEquals(Instant.parse(expirationStr), dto.toDomain().cooldown.expiration)
    }
}
