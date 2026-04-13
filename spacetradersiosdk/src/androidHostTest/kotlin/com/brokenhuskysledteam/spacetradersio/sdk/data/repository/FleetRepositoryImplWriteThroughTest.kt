package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.CooldownDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.testing.createTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FleetRepositoryImplWriteThroughTest {

    @Test
    fun refreshMyShips_transitShip_schedulesTransitTimer() = runTest {
        val transitShipDto = minimalShipDto("LADD-TRANSIT", navStatus = "IN_TRANSIT").let { dto ->
            dto.copy(nav = dto.nav.copy(
                route = dto.nav.route.copy(arrival = "2099-01-01T01:00:00.000Z")
            ))
        }
        val api = FakeFleetApi(ships = listOf(transitShipDto))
        val db = createTestDatabase()
        val scheduler = RefreshScheduler(backgroundScope)
        val repo = FleetRepositoryImpl(api, db, scheduler)
        repo.refreshMyShips()

        assertTrue(scheduler.activeTimers.value.containsKey("transit:LADD-TRANSIT"))
    }

    @Test
    fun refreshMyShip_dockedShip_noTimerScheduled() = runTest {
        val api = FakeFleetApi(singleShip = minimalShipDto("LADD-1", navStatus = "DOCKED"))
        val db = createTestDatabase()
        val scheduler = RefreshScheduler(backgroundScope)
        val repo = FleetRepositoryImpl(api, db, scheduler)
        repo.refreshMyShip("LADD-1")

        val result = repo.observeShip("LADD-1").first()
        assertEquals("LADD-1", result?.symbol)
        assertTrue(scheduler.activeTimers.value.isEmpty())
    }

    @Test
    fun refreshMyShip_shipWithActiveCooldown_schedulesCooldownTimer() = runTest {
        val cooldownShipDto = minimalShipDto("LADD-1").copy(
            cooldown = CooldownDto(
                shipSymbol = "LADD-1",
                totalSeconds = 60,
                remainingSeconds = 30,
                expiration = "2099-01-01T02:00:00.000Z"
            )
        )
        val api = FakeFleetApi(singleShip = cooldownShipDto)
        val db = createTestDatabase()
        val scheduler = RefreshScheduler(backgroundScope)
        val repo = FleetRepositoryImpl(api, db, scheduler)
        repo.refreshMyShip("LADD-1")

        assertTrue(scheduler.activeTimers.value.containsKey("cooldown:LADD-1"))
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
