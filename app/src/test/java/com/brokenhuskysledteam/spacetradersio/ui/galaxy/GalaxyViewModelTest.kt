package com.brokenhuskysledteam.spacetradersio.ui.galaxy

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.JumpGate
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.StarSystem
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SystemPage
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TravelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun sys(symbol: String) = StarSystem(symbol, "X1", "RED_STAR", 0, 0)

private class FakeTravelRepo(
    private val pages: Map<Int, SystemPage>,
    var throws: Exception? = null
) : TravelRepository {
    override suspend fun getJumpGate(systemSymbol: String, waypointSymbol: String): JumpGate = JumpGate("", emptyList())
    override suspend fun getSystems(page: Int, limit: Int): SystemPage {
        throws?.let { throw it }
        return pages.getValue(page)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GalaxyViewModelTest {
    private val td = StandardTestDispatcher()
    @BeforeTest fun s() { Dispatchers.setMain(td) }
    @AfterTest fun t() { Dispatchers.resetMain() }

    @Test
    fun init_loadsFirstPage() = runTest {
        val repo = FakeTravelRepo(mapOf(1 to SystemPage(listOf(sys("X1-A"), sys("X1-B")), 1, 3)))
        val vm = GalaxyViewModel(repo)
        td.scheduler.advanceUntilIdle()
        assertEquals(2, vm.uiState.value.systems.size)
        assertEquals(3, vm.uiState.value.total)
        assertTrue(vm.uiState.value.canLoadMore)
    }

    @Test
    fun loadMore_appendsNextPage() = runTest {
        val repo = FakeTravelRepo(mapOf(
            1 to SystemPage(listOf(sys("X1-A"), sys("X1-B")), 1, 3),
            2 to SystemPage(listOf(sys("X1-C")), 2, 3)
        ))
        val vm = GalaxyViewModel(repo)
        td.scheduler.advanceUntilIdle()
        vm.onEvent(GalaxyEvent.LoadMoreClicked)
        td.scheduler.advanceUntilIdle()
        assertEquals(listOf("X1-A", "X1-B", "X1-C"), vm.uiState.value.systems.map { it.symbol })
        assertFalse(vm.uiState.value.canLoadMore) // 3 of 3 loaded
    }

    @Test
    fun loadFailure_setsError() = runTest {
        val repo = FakeTravelRepo(emptyMap(), throws = RuntimeException("boom"))
        val vm = GalaxyViewModel(repo)
        td.scheduler.advanceUntilIdle()
        assertEquals("boom", vm.uiState.value.error)
    }
}
