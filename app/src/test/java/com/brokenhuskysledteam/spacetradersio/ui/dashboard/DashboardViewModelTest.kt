package com.brokenhuskysledteam.spacetradersio.ui.dashboard

import app.cash.turbine.test
import com.brokenhuskysledteam.spacetradersio.navigation.NavigationTarget
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.AgentDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AgentsApi
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// In-memory token store. Starts with a token to simulate a logged-in state.
private class FakeTokenRepository(var savedToken: String? = "existing-token") : TokenRepository {
    override fun getToken(): String? = savedToken
    override fun saveToken(token: String) { savedToken = token }
    override fun clearToken() { savedToken = null }
    override fun hasToken(): Boolean = savedToken != null
}

// Configurable fake — set agentResult for success or exception for failure.
// Implements the interface directly; no real HTTP calls.
private class FakeAgentsApi : AgentsApi {
    var agentResult: AgentDto? = null
    var exception: Exception? = null

    override suspend fun getMyAgent(): AgentDto {
        exception?.let { throw it }
        return agentResult ?: throw IllegalStateException("No result configured")
    }

    override suspend fun getAgent(symbol: String): AgentDto {
        exception?.let { throw it }
        return agentResult ?: throw IllegalStateException("No result configured")
    }
}

// Tests for DashboardViewModel covering init loading, success/error states,
// retry, logout with token clearing, and error dismissal.
// The ViewModel calls loadAgent() in init, so tests must configure the fake
// API *before* calling createViewModel().
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tokenRepository: FakeTokenRepository
    private lateinit var agentsApi: FakeAgentsApi

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        tokenRepository = FakeTokenRepository()
        agentsApi = FakeAgentsApi()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): DashboardViewModel =
        DashboardViewModel(agentsApi, tokenRepository)

    @Test
    fun init_loadsAgent_success() = runTest {
        agentsApi.agentResult = AgentDto(
            accountId = "acc-1",
            symbol = "COMMANDER",
            headquarters = "X1-HQ",
            credits = 50000L,
            startingFaction = "COSMIC",
            shipCount = 3
        )

        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.isLoading)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        val agent = assertNotNull(state.agent)
        assertEquals("COMMANDER", agent.symbol)
        assertEquals(50000L, agent.credits)
        assertEquals(3, agent.shipCount)
    }

    @Test
    fun init_loadsAgent_genericError_setsError() = runTest {
        agentsApi.exception = RuntimeException("Network error")

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Network error", state.error)
        assertNull(state.agent)
    }

    @Test
    fun retryClicked_reloadsAgent() = runTest {
        agentsApi.exception = RuntimeException("Temporary failure")
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)

        agentsApi.exception = null
        agentsApi.agentResult = AgentDto("acc-1", "CMD", "HQ", 100L, "COSMIC", 1)
        viewModel.onEvent(DashboardEvent.RetryClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals("CMD", assertNotNull(state.agent).symbol)
    }

    @Test
    fun logoutClicked_clearsTokenAndNavigates() = runTest {
        agentsApi.agentResult = AgentDto("acc-1", "CMD", "HQ", 100L, "COSMIC", 1)
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigationEvent.test {
            viewModel.onEvent(DashboardEvent.LogoutClicked)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(NavigationTarget.Auth, awaitItem())
        }

        assertNull(tokenRepository.savedToken)
    }

    @Test
    fun errorDismissed_clearsError() = runTest {
        agentsApi.exception = RuntimeException("Error")
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)

        viewModel.onEvent(DashboardEvent.ErrorDismissed)
        assertNull(viewModel.uiState.value.error)
    }
}
