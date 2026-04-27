package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MetaDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.PaginatedResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.WaypointDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.SystemsApi
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.WaypointStateStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun waypointDto(
    symbol: String,
    type: String = "PLANET",
    x: Int = 0,
    y: Int = 0,
    orbits: String? = null
) = WaypointDto(
    symbol = symbol,
    type = type,
    systemSymbol = "X1-DF55",
    x = x,
    y = y,
    orbits = orbits,
    orbitals = emptyList(),
    traits = emptyList(),
    isUnderConstruction = false
)

class SystemRepositoryImplTest {

    // ── single page ─────────────────────────────────────────────────────────

    @Test
    fun getSystemWaypoints_singlePage_returnsAllWaypoints() = runTest {
        val store = WaypointStateStore()
        val api = FakeSystemsApi(
            pages = mapOf(
                1 to PaginatedResponse(
                    data = listOf(waypointDto("WP-1"), waypointDto("WP-2")),
                    meta = MetaDto(total = 2, page = 1, limit = 20)
                )
            )
        )
        val repo = SystemRepositoryImpl(api, store)

        val result = repo.getSystemWaypoints("X1-DF55")
        assertEquals(2, result.size)
        assertEquals("WP-1", result[0].symbol)
        assertEquals("WP-2", result[1].symbol)
    }

    @Test
    fun getSystemWaypoints_singlePage_populatesStateStore() = runTest {
        val store = WaypointStateStore()
        val api = FakeSystemsApi(
            pages = mapOf(
                1 to PaginatedResponse(
                    data = listOf(waypointDto("WP-1")),
                    meta = MetaDto(total = 1, page = 1, limit = 20)
                )
            )
        )
        val repo = SystemRepositoryImpl(api, store)

        repo.getSystemWaypoints("X1-DF55")
        assertEquals("WP-1", store.entities.value["WP-1"]?.symbol)
    }

    // ── multi page ──────────────────────────────────────────────────────────

    @Test
    fun getSystemWaypoints_multiPage_fetchesAllPages() = runTest {
        val store = WaypointStateStore()
        val page1Data = (1..20).map { waypointDto("WP-$it") }
        val page2Data = (21..40).map { waypointDto("WP-$it") }
        val page3Data = (41..45).map { waypointDto("WP-$it") }

        val api = FakeSystemsApi(
            pages = mapOf(
                1 to PaginatedResponse(data = page1Data, meta = MetaDto(total = 45, page = 1, limit = 20)),
                2 to PaginatedResponse(data = page2Data, meta = MetaDto(total = 45, page = 2, limit = 20)),
                3 to PaginatedResponse(data = page3Data, meta = MetaDto(total = 45, page = 3, limit = 20))
            )
        )
        val repo = SystemRepositoryImpl(api, store)

        val result = repo.getSystemWaypoints("X1-DF55")
        assertEquals(45, result.size)
    }

    @Test
    fun getSystemWaypoints_multiPage_allPagesInStore() = runTest {
        val store = WaypointStateStore()
        val page1Data = (1..20).map { waypointDto("WP-$it") }
        val page2Data = (21..25).map { waypointDto("WP-$it") }

        val api = FakeSystemsApi(
            pages = mapOf(
                1 to PaginatedResponse(data = page1Data, meta = MetaDto(total = 25, page = 1, limit = 20)),
                2 to PaginatedResponse(data = page2Data, meta = MetaDto(total = 25, page = 2, limit = 20))
            )
        )
        val repo = SystemRepositoryImpl(api, store)

        repo.getSystemWaypoints("X1-DF55")
        assertEquals(25, store.entities.value.size)
    }

    @Test
    fun getSystemWaypoints_multiPage_requestsCorrectPages() = runTest {
        val store = WaypointStateStore()
        val api = FakeSystemsApi(
            pages = mapOf(
                1 to PaginatedResponse(data = (1..20).map { waypointDto("WP-$it") }, meta = MetaDto(total = 25, page = 1, limit = 20)),
                2 to PaginatedResponse(data = (21..25).map { waypointDto("WP-$it") }, meta = MetaDto(total = 25, page = 2, limit = 20))
            )
        )
        val repo = SystemRepositoryImpl(api, store)

        repo.getSystemWaypoints("X1-DF55")
        assertEquals(listOf(1, 2), api.requestedPages)
    }

    // ── empty system ────────────────────────────────────────────────────────

    @Test
    fun getSystemWaypoints_emptySystem_returnsEmptyList() = runTest {
        val store = WaypointStateStore()
        val api = FakeSystemsApi(
            pages = mapOf(
                1 to PaginatedResponse(data = emptyList(), meta = MetaDto(total = 0, page = 1, limit = 20))
            )
        )
        val repo = SystemRepositoryImpl(api, store)

        val result = repo.getSystemWaypoints("X1-DF55")
        assertTrue(result.isEmpty())
    }
}

private class FakeSystemsApi(
    private val pages: Map<Int, PaginatedResponse<WaypointDto>>
) : SystemsApi {
    val requestedPages = mutableListOf<Int>()

    override suspend fun getSystemWaypoints(
        systemSymbol: String,
        page: Int,
        limit: Int
    ): PaginatedResponse<WaypointDto> {
        requestedPages.add(page)
        return pages[page] ?: PaginatedResponse(emptyList(), MetaDto(total = 0, page = page, limit = limit))
    }

    override suspend fun getWaypoint(systemSymbol: String, waypointSymbol: String): WaypointDto =
        WaypointDto(symbol = waypointSymbol, type = "MOON", systemSymbol = systemSymbol, x = 0, y = 0)
}
