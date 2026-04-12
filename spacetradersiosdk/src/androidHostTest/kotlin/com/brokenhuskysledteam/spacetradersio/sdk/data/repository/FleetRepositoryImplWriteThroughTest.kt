package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.CooldownDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavRouteDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavRouteWaypointDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.testing.createTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FleetRepositoryImplWriteThroughTest {

    @Test
    fun refreshMyShips_transitShip_schedulesTimerAndRefreshesAfterArrival() = runTest {
        val transitShipDto = minimalShipDto("LADD-TRANSIT", navStatus = "IN_TRANSIT").let { dto ->
            dto.copy(nav = dto.nav.copy(
                route = dto.nav.route.copy(arrival = "2099-01-01T01:00:00.000Z")
            ))
        }
        val api = FakeFleetApi(ships = listOf(transitShipDto))
        val db = createTestDatabase()
        val repo = FleetRepositoryImpl(api, db, RefreshScheduler(backgroundScope))
        repo.refreshMyShips()

        val result = repo.observeShips().first()
        assertEquals(1, result.size)
        assertEquals("IN_TRANSIT", result.first().nav.status.name)
    }

    @Test
    fun refreshMyShip_dockedShip_noTimerScheduled_shipInDb() = runTest {
        val api = FakeFleetApi(singleShip = minimalShipDto("LADD-1", navStatus = "DOCKED"))
        val db = createTestDatabase()
        val repo = FleetRepositoryImpl(api, db, RefreshScheduler(backgroundScope))
        repo.refreshMyShip("LADD-1")

        val result = repo.observeShip("LADD-1").first()
        assertEquals("LADD-1", result?.symbol)
        assertEquals("DOCKED", result?.nav?.status?.name)
    }

    @Test
    fun saveShip_persistsShipToDb() = runTest {
        val db = createTestDatabase()
        val repo = FleetRepositoryImpl(FakeFleetApi(), db, RefreshScheduler(backgroundScope))
        repo.refreshMyShip("LADD-1")

        val ship = repo.observeShip("LADD-1").first()!!
        repo.saveShip(ship.copy(frameName = "Heavy Frame"))
        val updated = repo.observeShip("LADD-1").first()
        assertEquals("Heavy Frame", updated?.frameName)
    }
}
