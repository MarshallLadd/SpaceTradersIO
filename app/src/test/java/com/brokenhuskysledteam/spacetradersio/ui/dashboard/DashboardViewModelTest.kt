package com.brokenhuskysledteam.spacetradersio.ui.dashboard

import app.cash.turbine.test
import com.brokenhuskysledteam.spacetradersio.navigation.NavigationTarget
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersApiException
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersError
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.AgentRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManager
import com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SpaceTradersSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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

// Configurable fake — set agentToReturn for success or exception for failure.
// observeAgent() emits whatever was set by the last refreshAgent() call.
private class FakeAgentRepository : AgentRepository {
    private val _agent = MutableStateFlow<Agent?>(null)
    var agentToReturn: Agent? = null
    var exception: Exception? = null

    override fun observeAgent(): Flow<Agent?> = _agent

    override suspend fun refreshAgent() {
        exception?.let { throw it }
        _agent.value = agentToReturn
    }

    override suspend fun saveAgent(agent: Agent) { _agent.value = agent }
    override suspend fun updateCredits(symbol: String, credits: Long) {
        _agent.update { it?.copy(credits = credits) }
    }
    override suspend fun clearAll() { _agent.value = null }
}

// Tests for DashboardViewModel covering init loading, success/error states,
// retry, logout, and error dismissal.
// The ViewModel calls loadAgent() in init, so tests must configure the fake
// repository *before* calling createViewModel().
// Uses SharingStarted.Eagerly in the ViewModel, so uiState.value is stable
// after advanceUntilIdle() without needing an explicit subscriber.
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var agentRepository: FakeAgentRepository
    private lateinit var sessionManager: FakeSessionManager

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        agentRepository = FakeAgentRepository()
        sessionManager = FakeSessionManager()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): DashboardViewModel =
        DashboardViewModel(agentRepository, sessionManager)

    @Test
    fun init_loadsAgent_success() = runTest {
        agentRepository.agentToReturn = Agent(
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
        agentRepository.exception = RuntimeException("Network error")

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Network error", state.error)
        assertNull(state.agent)
    }

    @Test
    fun retryClicked_reloadsAgent() = runTest {
        agentRepository.exception = RuntimeException("Temporary failure")
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)

        agentRepository.exception = null
        agentRepository.agentToReturn = Agent("acc-1", "CMD", "HQ", 100L, "COSMIC", 1)
        viewModel.onEvent(DashboardEvent.RetryClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals("CMD", assertNotNull(state.agent).symbol)
    }

    @Test
    fun logoutClicked_callsSessionManagerLogoutAndNavigates() = runTest {
        agentRepository.agentToReturn = Agent("acc-1", "CMD", "HQ", 100L, "COSMIC", 1)
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
        agentRepository.exception = RuntimeException("Error")
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)

        viewModel.onEvent(DashboardEvent.ErrorDismissed)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun init_authError_callsSessionManagerLogoutAndNavigatesToAuth() = runTest {
        agentRepository.exception = SpaceTradersApiException(
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
        agentRepository.exception = SpaceTradersApiException(
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
