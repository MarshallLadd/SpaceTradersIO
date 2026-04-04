package com.brokenhuskysledteam.spacetradersio.ui.auth

import app.cash.turbine.test
import com.brokenhuskysledteam.spacetradersio.navigation.NavigationTarget
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.FactionSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RegisterAgentUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RegistrationResult
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeTokenRepository : TokenRepository {
    var savedToken: String? = null
    override fun getToken(): String? = savedToken
    override fun saveToken(token: String) { savedToken = token }
    override fun clearToken() { savedToken = null }
    override fun hasToken(): Boolean = savedToken != null
}

private class FakeRegisterAgentUseCase : RegisterAgentUseCase {
    var result: RegistrationResult? = null
    var exception: Exception? = null

    override suspend fun invoke(symbol: String, faction: FactionSymbol): RegistrationResult {
        exception?.let { throw it }
        return result ?: throw IllegalStateException("No result configured")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tokenRepository: FakeTokenRepository
    private lateinit var registerUseCase: FakeRegisterAgentUseCase
    private lateinit var viewModel: AuthViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        tokenRepository = FakeTokenRepository()
        registerUseCase = FakeRegisterAgentUseCase()
        viewModel = AuthViewModel(registerUseCase, tokenRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isDefault() {
        val state = viewModel.uiState.value
        assertEquals(AuthTab.NEW_AGENT, state.selectedTab)
        assertEquals("", state.callsign)
        assertEquals(FactionSymbol.COSMIC, state.selectedFaction)
        assertFalse(state.isRegistering)
        assertEquals("", state.token)
        assertFalse(state.isImporting)
        assertNull(state.error)
    }

    @Test
    fun tabSelected_updatesSelectedTab() {
        viewModel.onEvent(AuthEvent.TabSelected(AuthTab.IMPORT_TOKEN))
        assertEquals(AuthTab.IMPORT_TOKEN, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun tabSelected_clearsError() {
        viewModel.onEvent(AuthEvent.RegisterClicked) // triggers blank callsign error
        viewModel.onEvent(AuthEvent.TabSelected(AuthTab.IMPORT_TOKEN))
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun callsignChanged_updatesCallsign() {
        viewModel.onEvent(AuthEvent.CallsignChanged("COMMANDER"))
        assertEquals("COMMANDER", viewModel.uiState.value.callsign)
    }

    @Test
    fun factionSelected_updatesFaction() {
        viewModel.onEvent(AuthEvent.FactionSelected(FactionSymbol.VOID))
        assertEquals(FactionSymbol.VOID, viewModel.uiState.value.selectedFaction)
    }

    @Test
    fun tokenChanged_updatesToken() {
        viewModel.onEvent(AuthEvent.TokenChanged("my-bearer-token"))
        assertEquals("my-bearer-token", viewModel.uiState.value.token)
    }

    @Test
    fun registerClicked_blankCallsign_setsError() {
        viewModel.onEvent(AuthEvent.RegisterClicked)
        assertEquals("Callsign cannot be empty", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isRegistering)
    }

    @Test
    fun registerClicked_success_navigatesToDashboard() = runTest {
        registerUseCase.result = RegistrationResult(
            agent = Agent("acc-1", "CMD", "HQ", 100000L, "COSMIC", 1),
            token = "tok"
        )
        viewModel.onEvent(AuthEvent.CallsignChanged("CMD"))

        viewModel.navigationEvent.test {
            viewModel.onEvent(AuthEvent.RegisterClicked)
            assertTrue(viewModel.uiState.value.isRegistering)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(NavigationTarget.Dashboard, awaitItem())
            assertFalse(viewModel.uiState.value.isRegistering)
        }
    }

    @Test
    fun registerClicked_failure_setsError() = runTest {
        registerUseCase.exception = RuntimeException("Agent already exists")
        viewModel.onEvent(AuthEvent.CallsignChanged("CMD"))
        viewModel.onEvent(AuthEvent.RegisterClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Agent already exists", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isRegistering)
    }

    @Test
    fun importClicked_blankToken_setsError() {
        viewModel.onEvent(AuthEvent.TabSelected(AuthTab.IMPORT_TOKEN))
        viewModel.onEvent(AuthEvent.ImportClicked)
        assertEquals("Token cannot be empty", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isImporting)
    }

    @Test
    fun importClicked_success_savesTokenAndNavigates() = runTest {
        viewModel.onEvent(AuthEvent.TokenChanged("my-token"))

        viewModel.navigationEvent.test {
            viewModel.onEvent(AuthEvent.ImportClicked)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(NavigationTarget.Dashboard, awaitItem())
        }

        assertEquals("my-token", tokenRepository.savedToken)
        assertFalse(viewModel.uiState.value.isImporting)
    }

    @Test
    fun importClicked_trimsWhitespace() = runTest {
        viewModel.onEvent(AuthEvent.TokenChanged("  my-token  "))

        viewModel.navigationEvent.test {
            viewModel.onEvent(AuthEvent.ImportClicked)
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem()
        }

        assertEquals("my-token", tokenRepository.savedToken)
    }

    @Test
    fun errorDismissed_clearsError() {
        viewModel.onEvent(AuthEvent.RegisterClicked) // blank callsign → error
        viewModel.onEvent(AuthEvent.ErrorDismissed)
        assertNull(viewModel.uiState.value.error)
    }
}
