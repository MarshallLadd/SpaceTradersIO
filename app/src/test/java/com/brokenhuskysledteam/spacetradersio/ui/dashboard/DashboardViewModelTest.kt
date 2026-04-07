package com.brokenhuskysledteam.spacetradersio.ui.dashboard

import app.cash.turbine.test
import com.brokenhuskysledteam.spacetradersio.navigation.NavigationTarget
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.AgentDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AgentsApi
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersApiException
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersError
import com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManager
import com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SpaceTradersSession
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.AgentStateStore
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

// Captures logout calls and exposes whether logout() was invoked.
private class FakeSessionManager : SessionManager {
    var logoutCalled = false
    override fun requireSession(): SpaceTradersSession = error("Not implemented in tests")
    override fun login(token: String) {}
    override fun logout() { logoutCalled = true }
    override fun restoreIfAuthenticated() {}
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
// retry, logout, and error dismissal.
// The ViewModel calls loadAgent() in init, so tests must configure the fake
// API *before* calling createViewModel().
// Uses SharingStarted.Eagerly in the ViewModel, so uiState.value is stable
// after advanceUntilIdle() without needing an explicit subscriber.
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var agentsApi: FakeAgentsApi
    private lateinit var agentStateStore: AgentStateStore
    private lateinit var sessionManager: FakeSessionManager

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        agentsApi = FakeAgentsApi()
        agentStateStore = AgentStateStore()
        sessionManager = FakeSessionManager()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): DashboardViewModel =
        DashboardViewModel(agentsApi, agentStateStore, sessionManager)

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
    fun logoutClicked_callsSessionManagerLogoutAndNavigates() = runTest {
        agentsApi.agentResult = AgentDto("acc-1", "CMD", "HQ", 100L, "COSMIC", 1)
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigationEvent.test {
            viewModel.onEvent(DashboardEvent.LogoutClicked)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(NavigationTarget.Auth, awaitItem())
        }

        assertTrue(sessionManager.logoutCalled)
    }

    @Test
    fun errorDismissed_clearsError() = runTest {
        agentsApi.exception = RuntimeException("Error")
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)

        viewModel.onEvent(DashboardEvent.ErrorDismissed)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun init_authError_callsSessionManagerLogoutAndNavigatesToAuth() = runTest {
        agentsApi.exception = SpaceTradersApiException(
            error = SpaceTradersError.AuthError.InvalidToken(code = 4115, message = "Invalid token."),
            httpStatus = 401
        )

        val viewModel = createViewModel()
        viewModel.navigationEvent.test {
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(NavigationTarget.Auth, awaitItem())
        }
        assertTrue(sessionManager.logoutCalled)
    }

    @Test
    fun init_nonAuthApiError_setsErrorMessage() = runTest {
        agentsApi.exception = SpaceTradersApiException(
            error = SpaceTradersError.GeneralError.SystemStatusMaintenance(code = 3100, message = "Server is under maintenance."),
            httpStatus = 503
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Server is under maintenance.", state.error)
        assertNull(state.agent)
    }
}
