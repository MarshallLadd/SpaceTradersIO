package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MetaDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.PaginatedResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.WaypointDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.WaypointTraitDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.SystemsApi
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.WaypointStateStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeSystemsApiWithSingleWaypoint(
    private val waypointResult: WaypointDto = WaypointDto(
        symbol = "X1-DF55-20250Z",
        type = "MOON",
        systemSymbol = "X1-DF55",
        x = 0, y = 0,
        traits = listOf(WaypointTraitDto("SHIPYARD", "Shipyard", "Buy ships here."))
    )
) : SystemsApi {
    var lastSingleWaypointSymbol: String? = null

    override suspend fun getSystemWaypoints(systemSymbol: String, page: Int, limit: Int): PaginatedResponse<WaypointDto> =
        PaginatedResponse(data = listOf(waypointResult), meta = MetaDto(total = 1, page = 1, limit = 20))

    override suspend fun getWaypoint(systemSymbol: String, waypointSymbol: String): WaypointDto {
        lastSingleWaypointSymbol = waypointSymbol
        return waypointResult
    }
}

class SystemRepositoryGetWaypointTest {

    private fun createRepo(api: FakeSystemsApiWithSingleWaypoint = FakeSystemsApiWithSingleWaypoint()): Pair<SystemRepositoryImpl, WaypointStateStore> {
        val store = WaypointStateStore()
        return SystemRepositoryImpl(api, store) to store
    }

    @Test
    fun getWaypoint_returnsDomainWaypoint() = runTest {
        val (repo, _) = createRepo()
        val waypoint = repo.getWaypoint("X1-DF55", "X1-DF55-20250Z")
        assertEquals("X1-DF55-20250Z", waypoint.symbol)
    }

    @Test
    fun getWaypoint_populatesWaypointStateStore() = runTest {
        val (repo, store) = createRepo()
        repo.getWaypoint("X1-DF55", "X1-DF55-20250Z")
        val stored = store.observe("X1-DF55-20250Z").first()
        assertEquals("X1-DF55-20250Z", stored?.symbol)
    }

    @Test
    fun getWaypoint_mapsTrait_shipyard() = runTest {
        val (repo, _) = createRepo()
        val waypoint = repo.getWaypoint("X1-DF55", "X1-DF55-20250Z")
        assertTrue(waypoint.traits.any { it.symbol == WaypointTraitSymbol.SHIPYARD })
    }

    @Test
    fun observeWaypoint_beforeFetch_emitsNull() = runTest {
        val (repo, _) = createRepo()
        val result = repo.observeWaypoint("X1-DF55-20250Z").first()
        assertNull(result)
    }

    @Test
    fun observeWaypoint_afterGetWaypoint_emitsWaypoint() = runTest {
        val (repo, _) = createRepo()
        repo.getWaypoint("X1-DF55", "X1-DF55-20250Z")
        val result = repo.observeWaypoint("X1-DF55-20250Z").first()
        assertEquals("X1-DF55-20250Z", result?.symbol)
    }
}
