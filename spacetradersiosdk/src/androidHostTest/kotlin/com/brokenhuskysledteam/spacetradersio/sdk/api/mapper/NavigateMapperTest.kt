package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.NavigateResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipFuelDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavRouteDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavRouteWaypointDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavFlightMode
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class NavigateMapperTest {

    private fun waypointDto(symbol: String = "X1-DF55-20250Z") =
        ShipNavRouteWaypointDto(symbol = symbol, type = "MOON", systemSymbol = "X1-DF55", x = 0, y = 0)

    private fun dto() = NavigateResponseDto(
        nav = ShipNavDto(
            systemSymbol = "X1-DF55",
            waypointSymbol = "X1-DF55-17335A",
            route = ShipNavRouteDto(
                destination = waypointDto("X1-DF55-17335A"),
                origin = waypointDto("X1-DF55-20250Z"),
                departureTime = "2025-06-01T10:00:00.000Z",
                arrival = "2025-06-01T10:30:00.000Z"
            ),
            status = "IN_TRANSIT",
            flightMode = "CRUISE"
        ),
        fuel = ShipFuelDto(current = 350, capacity = 400)
    )

    @Test
    fun navigate_navStatusMapsCorrectly() {
        assertEquals(ShipNavStatus.IN_TRANSIT, dto().toDomain().nav.status)
    }

    @Test
    fun navigate_navWaypointSymbolMapsCorrectly() {
        assertEquals("X1-DF55-17335A", dto().toDomain().nav.waypointSymbol)
    }

    @Test
    fun navigate_navFlightModeMapsCorrectly() {
        assertEquals(ShipNavFlightMode.CRUISE, dto().toDomain().nav.flightMode)
    }

    @Test
    fun navigate_fuelCurrentMapsCorrectly() {
        assertEquals(350, dto().toDomain().fuel.current)
    }

    @Test
    fun navigate_fuelCapacityMapsCorrectly() {
        assertEquals(400, dto().toDomain().fuel.capacity)
    }
}
