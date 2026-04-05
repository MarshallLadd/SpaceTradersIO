# Account Token Registration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix `POST /register` to send an `AccountToken` Bearer header by threading a user-supplied account token from the UI through the use case to the API call, then immediately discarding it.

**Architecture:** `SpaceTradersClient` gains `authenticatedWith(token)` — a one-off client factory that mirrors `authenticated` but takes a token directly. `AccountsApi` is refactored to take `SpaceTradersClient` (matching all other API classes) and calls `authenticatedWith(accountToken)` per registration request. The token flows UI → ViewModel → UseCase → Api and is never persisted.

**Tech Stack:** Kotlin Multiplatform, Ktor 3.4.2 (MockEngine for tests), Hilt DI, Jetpack Compose, kotlinx-coroutines-test, Turbine

---

## File Map

| Action | Path |
|--------|------|
| Modify | `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/client/SpaceTradersClient.kt` |
| Modify | `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/testing/TestHelpers.kt` |
| Modify | `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/endpoints/AccountsApi.kt` |
| Create | `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/endpoints/AccountsApiTest.kt` |
| Modify | `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/RegisterAgentUseCase.kt` |
| Modify | `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/RegisterAgentUseCaseTest.kt` |
| Modify | `app/src/main/java/com/brokenhuskysledteam/spacetradersio/di/SdkModule.kt` |
| Modify | `app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/auth/AuthUiState.kt` |
| Modify | `app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/auth/AuthViewModel.kt` |
| Modify | `app/src/test/java/com/brokenhuskysledteam/spacetradersio/ui/auth/AuthViewModelTest.kt` |
| Modify | `app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/auth/AuthScreen.kt` |

---

## Task 1: Add `authenticatedWith()` to `SpaceTradersClient` and fix `TestHelpers`

**Files:**
- Modify: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/client/SpaceTradersClient.kt`
- Modify: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/testing/TestHelpers.kt`

- [ ] **Step 1: Add `authenticatedWith()` to `SpaceTradersClient`**

The class already has `unauthenticated` (null token) and `authenticated` (reads from `TokenRepository`). Add a third entry point for one-off tokens. The private top-level `buildHttpClient(token)` is in the same file and callable from the class. Replace the entire class body (not the file-level functions):

```kotlin
class SpaceTradersClient(
    private val tokenRepository: TokenRepository,
    internal val httpClientFactory: ((token: String?) -> HttpClient)? = null
) {

    val unauthenticated: HttpClient
        get() = httpClientFactory?.invoke(null) ?: buildHttpClient(token = null)

    // Rebuilt on each access so it picks up a freshly saved token after registration.
    val authenticated: HttpClient
        get() {
            val token = tokenRepository.getToken()
            return httpClientFactory?.invoke(token) ?: buildHttpClient(token = token)
        }

    // Builds a one-off client authenticated with the given token.
    // Used for endpoints that require a token not stored in TokenRepository
    // (e.g. AccountToken for /register).
    fun authenticatedWith(token: String): HttpClient =
        httpClientFactory?.invoke(token) ?: buildHttpClient(token = token)
}
```

- [ ] **Step 2: Fix `buildMockSpaceTradersClient` to pass the token through**

The current factory uses `{ _ -> ... }` — it ignores the token, so `authenticatedWith()` would produce a client with no `Authorization` header in tests. Replace the factory lambda so it mirrors production behaviour (adds the header when token is non-null):

```kotlin
fun buildMockSpaceTradersClient(
    tokenRepository: TokenRepository = FakeTokenRepository(),
    handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
): SpaceTradersClient {
    return SpaceTradersClient(
        tokenRepository = tokenRepository,
        httpClientFactory = { token ->
            HttpClient(MockEngine(handler)) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                defaultRequest {
                    contentType(ContentType.Application.Json)
                    if (token != null) headers.append("Authorization", "Bearer $token")
                }
            }
        }
    )
}
```

- [ ] **Step 3: Compile**

```bash
./gradlew :spacetradersiosdk:compileAndroidHostTestSources
```

Expected: BUILD SUCCESSFUL with no errors.

- [ ] **Step 4: Commit**

```bash
git add spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/client/SpaceTradersClient.kt
git add spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/testing/TestHelpers.kt
git commit -m "feat(sdk): add SpaceTradersClient.authenticatedWith() for one-off token auth"
```

---

## Task 2: Refactor `AccountsApi` and write `AccountsApiTest`

**Files:**
- Modify: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/endpoints/AccountsApi.kt`
- Create: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/endpoints/AccountsApiTest.kt`

- [ ] **Step 1: Write `AccountsApiTest`**

Create the new file. The test uses `buildMockSpaceTradersClient` from `TestHelpers`. The `MockEngine` handler captures the `Authorization` header from each incoming request so tests can assert it.

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.testing.FakeTokenRepository
import com.brokenhuskysledteam.spacetradersio.sdk.testing.buildMockSpaceTradersClient
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val REGISTER_RESPONSE = """
{
  "data": {
    "token": "agent-bearer-token",
    "agent": {
      "accountId": "acc-abc123",
      "symbol": "TEST_AGENT",
      "headquarters": "X1-DF55-20250Z",
      "credits": 175000,
      "startingFaction": "COSMIC",
      "shipCount": 2
    },
    "faction": {
      "symbol": "COSMIC",
      "name": "Cosmic Engineers",
      "description": "A faction.",
      "headquarters": null,
      "traits": [],
      "isRecruiting": true
    },
    "contract": {
      "id": "contract-1",
      "factionSymbol": "COSMIC",
      "type": "PROCUREMENT",
      "terms": {
        "deadline": "2025-06-15T12:00:00.000Z",
        "payment": { "onAccepted": 1000, "onFulfilled": 5000 },
        "deliver": []
      },
      "accepted": false,
      "fulfilled": false,
      "expiration": "2025-05-01T00:00:00.000Z",
      "deadlineToAccept": "2025-05-10T00:00:00.000Z"
    },
    "ships": []
  }
}
"""

class AccountsApiTest {

    private var capturedAuthHeader: String? = null

    private fun buildApi(): AccountsApi {
        val client = buildMockSpaceTradersClient(
            tokenRepository = FakeTokenRepository(storedToken = null)
        ) { request ->
            capturedAuthHeader = request.headers[HttpHeaders.Authorization]
            respond(
                content = REGISTER_RESPONSE,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        return AccountsApi(client)
    }

    @Test
    fun register_sendsAccountTokenAsAuthorizationHeader() = runTest {
        buildApi().register("TEST_AGENT", "COSMIC", "my-account-token")
        assertEquals("Bearer my-account-token", capturedAuthHeader)
    }

    @Test
    fun register_returnsAgentToken() = runTest {
        val result = buildApi().register("TEST_AGENT", "COSMIC", "my-account-token")
        assertEquals("agent-bearer-token", result.token)
    }

    @Test
    fun register_returnsAgentSymbol() = runTest {
        val result = buildApi().register("TEST_AGENT", "COSMIC", "my-account-token")
        assertEquals("TEST_AGENT", result.agent.symbol)
    }
}
```

- [ ] **Step 2: Run the test — expect compile failure**

```bash
./gradlew :spacetradersiosdk:compileAndroidHostTestSources
```

Expected: COMPILE ERROR — `AccountsApi(client)` won't compile because `AccountsApi` still takes `HttpClient`, not `SpaceTradersClient`.

- [ ] **Step 3: Refactor `AccountsApi`**

Replace the entire file:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ApiResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.RegisterRequestDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.RegisterResponseDto
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

// Endpoints under the "Accounts" tag in the OpenAPI spec.
// Registration requires an AccountToken — a per-account Bearer issued from the
// SpaceTraders dashboard. It is never stored; it is used once here and then dropped.
class AccountsApi(private val spaceTradersClient: SpaceTradersClient) {

    // POST /register — authenticated with AccountToken (not AgentToken).
    // Creates a new agent linked to the account and returns the AgentToken
    // along with the agent's starting state (faction, contract, ships).
    suspend fun register(symbol: String, faction: String, accountToken: String): RegisterResponseDto =
        spaceTradersClient.authenticatedWith(accountToken).post("register") {
            setBody(RegisterRequestDto(symbol = symbol, faction = faction))
        }.body<ApiResponse<RegisterResponseDto>>().data
}
```

- [ ] **Step 4: Run tests — expect pass**

```bash
./gradlew :spacetradersiosdk:allTests
```

Expected: BUILD SUCCESSFUL. All existing tests pass plus the 3 new `AccountsApiTest` tests.

- [ ] **Step 5: Commit**

```bash
git add spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/endpoints/AccountsApi.kt
git add spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/endpoints/AccountsApiTest.kt
git commit -m "fix(sdk): refactor AccountsApi to use SpaceTradersClient and send AccountToken on register"
```

---

## Task 3: Update `RegisterAgentUseCase` and `RegisterAgentUseCaseTest`

**Files:**
- Modify: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/RegisterAgentUseCase.kt`
- Modify: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/RegisterAgentUseCaseTest.kt`

- [ ] **Step 1: Update `RegisterAgentUseCaseTest`**

The test currently builds `AccountsApi(client)` with a raw `HttpClient`. Switch to `buildMockSpaceTradersClient` to match the new `AccountsApi` constructor. Add `accountToken` to every `invoke()` call. Add one new test verifying only the agent token is persisted.

Replace the entire file:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AccountsApi
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.FactionSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.testing.FakeTokenRepository
import com.brokenhuskysledteam.spacetradersio.sdk.testing.buildMockSpaceTradersClient
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

private const val REGISTER_RESPONSE = """
{
  "data": {
    "token": "test-bearer-token",
    "agent": {
      "accountId": "acc-abc123",
      "symbol": "TEST_AGENT",
      "headquarters": "X1-DF55-20250Z",
      "credits": 100000,
      "startingFaction": "COSMIC",
      "shipCount": 1
    },
    "faction": {
      "symbol": "COSMIC",
      "name": "Cosmic Engineers",
      "description": "A faction description.",
      "headquarters": null,
      "traits": [],
      "isRecruiting": true
    },
    "contract": {
      "id": "contract-1",
      "factionSymbol": "COSMIC",
      "type": "PROCUREMENT",
      "terms": {
        "deadline": "2025-06-15T12:00:00.000Z",
        "payment": { "onAccepted": 1000, "onFulfilled": 5000 },
        "deliver": []
      },
      "accepted": false,
      "fulfilled": false,
      "expiration": "2025-05-01T00:00:00.000Z",
      "deadlineToAccept": "2025-05-10T00:00:00.000Z"
    },
    "ships": []
  }
}
"""

class RegisterAgentUseCaseTest {

    private fun buildUseCase(tokenRepo: FakeTokenRepository): RegisterAgentUseCase {
        val client = buildMockSpaceTradersClient(tokenRepository = tokenRepo) {
            respond(
                content = REGISTER_RESPONSE,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        return RegisterAgentUseCaseImpl(AccountsApi(client), tokenRepo)
    }

    @Test
    fun invoke_savesAgentTokenToRepository() = runTest {
        val tokenRepo = FakeTokenRepository(storedToken = null)
        buildUseCase(tokenRepo).invoke("TEST_AGENT", FactionSymbol.COSMIC, "test-account-token")

        assertEquals("test-bearer-token", tokenRepo.storedToken)
    }

    @Test
    fun invoke_doesNotSaveAccountTokenToRepository() = runTest {
        val tokenRepo = FakeTokenRepository(storedToken = null)
        buildUseCase(tokenRepo).invoke("TEST_AGENT", FactionSymbol.COSMIC, "my-account-token")

        // Only the agent token returned by the API should be persisted
        assertNotEquals("my-account-token", tokenRepo.storedToken)
        assertEquals("test-bearer-token", tokenRepo.storedToken)
    }

    @Test
    fun invoke_returnsAgentWithCorrectSymbol() = runTest {
        val result = buildUseCase(FakeTokenRepository(storedToken = null))
            .invoke("TEST_AGENT", FactionSymbol.COSMIC, "test-account-token")

        assertEquals("TEST_AGENT", result.agent.symbol)
    }

    @Test
    fun invoke_returnsTokenInResult() = runTest {
        val result = buildUseCase(FakeTokenRepository(storedToken = null))
            .invoke("TEST_AGENT", FactionSymbol.COSMIC, "test-account-token")

        assertEquals("test-bearer-token", result.token)
    }

    @Test
    fun invoke_agentAccountIdMappedFromResponse() = runTest {
        val result = buildUseCase(FakeTokenRepository(storedToken = null))
            .invoke("TEST_AGENT", FactionSymbol.COSMIC, "test-account-token")

        assertEquals("acc-abc123", result.agent.accountId)
    }
}
```

- [ ] **Step 2: Run — expect compile failure**

```bash
./gradlew :spacetradersiosdk:compileAndroidHostTestSources
```

Expected: COMPILE ERROR — `invoke()` still only takes `symbol` and `faction`.

- [ ] **Step 3: Add `accountToken` to `RegisterAgentUseCase` interface and impl**

Replace the entire file:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AccountsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.FactionSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository

data class RegistrationResult(
    val agent: Agent,
    val token: String
)

// Registers a new agent and persists the returned AgentToken.
// Implemented by [RegisterAgentUseCaseImpl]; defined as an interface
// so app-layer tests can substitute a fake without mock engines.
interface RegisterAgentUseCase {
    /**
     * Sends a registration request with the given [symbol] and [faction],
     * authenticated with [accountToken] (a per-account Bearer token from the
     * SpaceTraders dashboard). Saves the returned AgentToken and returns the
     * agent + token pair. The [accountToken] is never persisted.
     */
    suspend operator fun invoke(
        symbol: String,
        faction: FactionSymbol = FactionSymbol.COSMIC,
        accountToken: String
    ): RegistrationResult
}

// Registers a new agent with the SpaceTraders API using the caller-supplied
// AccountToken, then immediately persists the returned AgentToken so
// subsequent authenticated requests can be made without re-registering.
class RegisterAgentUseCaseImpl(
    private val accountsApi: AccountsApi,
    private val tokenRepository: TokenRepository
) : RegisterAgentUseCase {
    override suspend operator fun invoke(
        symbol: String,
        faction: FactionSymbol,
        accountToken: String
    ): RegistrationResult {
        val response = accountsApi.register(
            symbol = symbol,
            faction = faction.name,
            accountToken = accountToken
        )
        tokenRepository.saveToken(response.token)
        return RegistrationResult(
            agent = response.agent.toDomain(),
            token = response.token
        )
    }
}
```

- [ ] **Step 4: Run tests — expect pass**

```bash
./gradlew :spacetradersiosdk:allTests
```

Expected: BUILD SUCCESSFUL. All 5 `RegisterAgentUseCaseTest` tests pass.

- [ ] **Step 5: Commit**

```bash
git add spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/RegisterAgentUseCase.kt
git add spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/RegisterAgentUseCaseTest.kt
git commit -m "fix(sdk): thread accountToken through RegisterAgentUseCase to AccountsApi"
```

---

## Task 4: Update `SdkModule` DI

**Files:**
- Modify: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/di/SdkModule.kt`

- [ ] **Step 1: Update `provideAccountsApi`**

`AccountsApi` now takes `SpaceTradersClient` instead of a raw `HttpClient`. Change the provider — it already receives `client: SpaceTradersClient` as a parameter, so just pass it directly instead of `client.unauthenticated`:

```kotlin
@Provides
@Singleton
fun provideAccountsApi(client: SpaceTradersClient): AccountsApi =
    AccountsApi(client)
```

- [ ] **Step 2: Compile the app module**

```bash
./gradlew :app:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL. (The app will still fail to compile after this task because `AuthViewModel` still calls `registerAgentUseCase(symbol, faction)` with the old 2-argument signature — that's fixed in Task 6.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/brokenhuskysledteam/spacetradersio/di/SdkModule.kt
git commit -m "fix(app): update SdkModule to pass SpaceTradersClient directly to AccountsApi"
```

---

## Task 5: Update `AuthUiState` and `AuthEvent`

**Files:**
- Modify: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/auth/AuthUiState.kt`

- [ ] **Step 1: Rename `token` → `agentToken`, add `accountToken`, rename `TokenChanged` → `AgentTokenChanged`, add `AccountTokenChanged`**

Replace the entire file:

```kotlin
package com.brokenhuskysledteam.spacetradersio.ui.auth

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.FactionSymbol

// Immutable snapshot of the auth screen's UI state.
// The ViewModel emits new instances via StateFlow on every state change.
data class AuthUiState(
    val selectedTab: AuthTab = AuthTab.NEW_AGENT,
    val callsign: String = "",
    val selectedFaction: FactionSymbol = FactionSymbol.COSMIC,
    // Account token: entered by the user for registration only; never persisted.
    val accountToken: String = "",
    val isRegistering: Boolean = false,
    // Agent token: entered by the user on the Import Token tab to resume an existing agent.
    val agentToken: String = "",
    val isImporting: Boolean = false,
    val error: String? = null
)

enum class AuthTab { NEW_AGENT, IMPORT_TOKEN }

// All user interactions on the auth screen, dispatched to AuthViewModel.onEvent().
// Using a sealed interface keeps the ViewModel's event handling exhaustive.
sealed interface AuthEvent {
    data class TabSelected(val tab: AuthTab) : AuthEvent
    data class CallsignChanged(val value: String) : AuthEvent
    data class FactionSelected(val faction: FactionSymbol) : AuthEvent
    data class AccountTokenChanged(val value: String) : AuthEvent
    data class AgentTokenChanged(val value: String) : AuthEvent
    data object RegisterClicked : AuthEvent
    data object ImportClicked : AuthEvent
    data object ErrorDismissed : AuthEvent
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/auth/AuthUiState.kt
git commit -m "refactor(app): rename token→agentToken in AuthUiState, add accountToken field"
```

---

## Task 6: Update `AuthViewModel` and `AuthViewModelTest`

**Files:**
- Modify: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/auth/AuthViewModel.kt`
- Modify: `app/src/test/java/com/brokenhuskysledteam/spacetradersio/ui/auth/AuthViewModelTest.kt`

- [ ] **Step 1: Update `AuthViewModelTest`**

Update `FakeRegisterAgentUseCase` to capture `accountToken`. Update all existing tests to use the renamed events/fields. Add 4 new tests.

Replace the entire file:

```kotlin
package com.brokenhuskysledteam.spacetradersio.ui.auth

import app.cash.turbine.test
import com.brokenhuskysledteam.spacetradersio.navigation.NavigationTarget
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersApiException
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersError
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// In-memory token store. Starts empty (logged-out) since auth tests need to
// verify that registration and import write the token correctly.
private class FakeTokenRepository : TokenRepository {
    var savedToken: String? = null
    override fun getToken(): String? = savedToken
    override fun saveToken(token: String) { savedToken = token }
    override fun clearToken() { savedToken = null }
    override fun hasToken(): Boolean = savedToken != null
}

// Configurable fake — set result for success or exception for failure.
// Captures lastAccountToken so tests can verify it was passed (and not stored).
private class FakeRegisterAgentUseCase : RegisterAgentUseCase {
    var result: RegistrationResult? = null
    var exception: Exception? = null
    var lastAccountToken: String? = null

    override suspend fun invoke(
        symbol: String,
        faction: FactionSymbol,
        accountToken: String
    ): RegistrationResult {
        lastAccountToken = accountToken
        exception?.let { throw it }
        return result ?: throw IllegalStateException("No result configured")
    }
}

// Tests for AuthViewModel covering all UDF events, error paths, navigation
// signals, and state transitions for both registration and token import flows.
// Uses StandardTestDispatcher so coroutine execution is explicit via advanceUntilIdle().
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
        assertEquals("", state.accountToken)
        assertFalse(state.isRegistering)
        assertEquals("", state.agentToken)
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
    fun accountTokenChanged_updatesAccountToken() {
        viewModel.onEvent(AuthEvent.AccountTokenChanged("my-account-token"))
        assertEquals("my-account-token", viewModel.uiState.value.accountToken)
    }

    @Test
    fun agentTokenChanged_updatesAgentToken() {
        viewModel.onEvent(AuthEvent.AgentTokenChanged("my-agent-token"))
        assertEquals("my-agent-token", viewModel.uiState.value.agentToken)
    }

    @Test
    fun registerClicked_blankCallsign_setsError() {
        viewModel.onEvent(AuthEvent.RegisterClicked)
        assertEquals("Callsign cannot be empty", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isRegistering)
    }

    @Test
    fun registerClicked_blankAccountToken_setsError() {
        viewModel.onEvent(AuthEvent.CallsignChanged("CMD"))
        // accountToken is blank by default; callsign validation passes first
        viewModel.onEvent(AuthEvent.RegisterClicked)
        assertEquals("Account token cannot be empty", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isRegistering)
    }

    @Test
    fun registerClicked_success_navigatesToDashboard() = runTest {
        registerUseCase.result = RegistrationResult(
            agent = Agent("acc-1", "CMD", "HQ", 100000L, "COSMIC", 1),
            token = "tok"
        )
        viewModel.onEvent(AuthEvent.CallsignChanged("CMD"))
        viewModel.onEvent(AuthEvent.AccountTokenChanged("my-account-token"))

        viewModel.navigationEvent.test {
            viewModel.onEvent(AuthEvent.RegisterClicked)
            assertTrue(viewModel.uiState.value.isRegistering)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(NavigationTarget.Dashboard, awaitItem())
            assertFalse(viewModel.uiState.value.isRegistering)
        }
    }

    @Test
    fun registerClicked_trimsAccountToken() = runTest {
        registerUseCase.result = RegistrationResult(
            agent = Agent("acc-1", "CMD", "HQ", 100000L, "COSMIC", 1),
            token = "tok"
        )
        viewModel.onEvent(AuthEvent.CallsignChanged("CMD"))
        viewModel.onEvent(AuthEvent.AccountTokenChanged("  my-account-token  "))

        viewModel.navigationEvent.test {
            viewModel.onEvent(AuthEvent.RegisterClicked)
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem()
        }

        assertEquals("my-account-token", registerUseCase.lastAccountToken)
    }

    @Test
    fun registerClicked_doesNotSaveAccountTokenToRepository() = runTest {
        registerUseCase.result = RegistrationResult(
            agent = Agent("acc-1", "CMD", "HQ", 100000L, "COSMIC", 1),
            token = "agent-tok"
        )
        viewModel.onEvent(AuthEvent.CallsignChanged("CMD"))
        viewModel.onEvent(AuthEvent.AccountTokenChanged("my-account-token"))

        viewModel.navigationEvent.test {
            viewModel.onEvent(AuthEvent.RegisterClicked)
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem()
        }

        assertNotEquals("my-account-token", tokenRepository.savedToken)
    }

    @Test
    fun registerClicked_failure_setsError() = runTest {
        registerUseCase.exception = RuntimeException("Agent already exists")
        viewModel.onEvent(AuthEvent.CallsignChanged("CMD"))
        viewModel.onEvent(AuthEvent.AccountTokenChanged("my-account-token"))
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
        viewModel.onEvent(AuthEvent.AgentTokenChanged("my-token"))

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
        viewModel.onEvent(AuthEvent.AgentTokenChanged("  my-token  "))

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

    @Test
    fun registerClicked_conflictSymbol_showsUserFriendlyMessage() = runTest {
        registerUseCase.exception = SpaceTradersApiException(
            error = SpaceTradersError.AuthError.RegisterAgentConflictSymbol(code = 4111, message = "Agent symbol has already been claimed."),
            httpStatus = 409
        )
        viewModel.onEvent(AuthEvent.CallsignChanged("TAKEN"))
        viewModel.onEvent(AuthEvent.AccountTokenChanged("my-account-token"))
        viewModel.onEvent(AuthEvent.RegisterClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Callsign already taken", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isRegistering)
    }

    @Test
    fun registerClicked_reservedSymbol_showsUserFriendlyMessage() = runTest {
        registerUseCase.exception = SpaceTradersApiException(
            error = SpaceTradersError.AuthError.RegisterAgentSymbolReserved(code = 4110, message = "Agent symbol is reserved."),
            httpStatus = 409
        )
        viewModel.onEvent(AuthEvent.CallsignChanged("RESERVED"))
        viewModel.onEvent(AuthEvent.AccountTokenChanged("my-account-token"))
        viewModel.onEvent(AuthEvent.RegisterClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Callsign is reserved", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isRegistering)
    }

    @Test
    fun registerClicked_otherApiError_showsServerMessage() = runTest {
        registerUseCase.exception = SpaceTradersApiException(
            error = SpaceTradersError.GeneralError.SystemStatusMaintenance(code = 3100, message = "Server is under maintenance."),
            httpStatus = 503
        )
        viewModel.onEvent(AuthEvent.CallsignChanged("CMD"))
        viewModel.onEvent(AuthEvent.AccountTokenChanged("my-account-token"))
        viewModel.onEvent(AuthEvent.RegisterClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Server is under maintenance.", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isRegistering)
    }
}
```

- [ ] **Step 2: Run — expect compile failure**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: COMPILE ERROR — `AuthViewModel` still references `TokenChanged`, `state.token`, and calls `registerAgentUseCase(symbol, faction)` with the old 2-arg signature.

- [ ] **Step 3: Update `AuthViewModel`**

Replace the entire file:

```kotlin
package com.brokenhuskysledteam.spacetradersio.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.navigation.NavigationTarget
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersApiException
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersError
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RegisterAgentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Drives the auth screen's two flows: new agent registration and token import.
//
// Registration delegates to RegisterAgentUseCase which calls the API (using the
// caller-supplied AccountToken) and persists the returned AgentToken. The
// AccountToken is passed through and immediately discarded — never stored.
//
// Token import uses a "store-and-go" approach — the AgentToken is saved
// immediately and the dashboard's first API call validates it. If that call
// returns 401/403, DashboardViewModel clears the token and sends the user back.
//
// Navigation signals are sent via a Channel (not SharedFlow) so each event is
// consumed exactly once — no re-delivery on configuration changes.
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val registerAgentUseCase: RegisterAgentUseCase,
    private val tokenRepository: TokenRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _navigationEvent = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    fun onEvent(event: AuthEvent) {
        when (event) {
            is AuthEvent.TabSelected -> _uiState.update { it.copy(selectedTab = event.tab, error = null) }
            is AuthEvent.CallsignChanged -> _uiState.update { it.copy(callsign = event.value) }
            is AuthEvent.FactionSelected -> _uiState.update { it.copy(selectedFaction = event.faction) }
            is AuthEvent.AccountTokenChanged -> _uiState.update { it.copy(accountToken = event.value) }
            is AuthEvent.AgentTokenChanged -> _uiState.update { it.copy(agentToken = event.value) }
            is AuthEvent.RegisterClicked -> register()
            is AuthEvent.ImportClicked -> importToken()
            is AuthEvent.ErrorDismissed -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun register() {
        val state = _uiState.value
        if (state.callsign.isBlank()) {
            _uiState.update { it.copy(error = "Callsign cannot be empty") }
            return
        }
        if (state.accountToken.isBlank()) {
            _uiState.update { it.copy(error = "Account token cannot be empty") }
            return
        }
        _uiState.update { it.copy(isRegistering = true, error = null) }
        viewModelScope.launch {
            try {
                registerAgentUseCase(state.callsign.trim(), state.selectedFaction, state.accountToken.trim())
                _uiState.update { it.copy(isRegistering = false) }
                _navigationEvent.send(NavigationTarget.Dashboard)
            } catch (e: SpaceTradersApiException) {
                val errorMessage = when (e.error) {
                    is SpaceTradersError.AuthError.RegisterAgentConflictSymbol -> "Callsign already taken"
                    is SpaceTradersError.AuthError.RegisterAgentSymbolReserved -> "Callsign is reserved"
                    else -> e.message ?: "Registration failed"
                }
                _uiState.update { it.copy(isRegistering = false, error = errorMessage) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRegistering = false, error = e.message ?: "Registration failed") }
            }
        }
    }

    private fun importToken() {
        val state = _uiState.value
        if (state.agentToken.isBlank()) {
            _uiState.update { it.copy(error = "Token cannot be empty") }
            return
        }
        _uiState.update { it.copy(isImporting = true, error = null) }
        tokenRepository.saveToken(state.agentToken.trim())
        _uiState.update { it.copy(isImporting = false) }
        viewModelScope.launch {
            _navigationEvent.send(NavigationTarget.Dashboard)
        }
    }
}
```

- [ ] **Step 4: Run tests — expect pass**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. All `AuthViewModelTest` tests pass (existing + 4 new).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/auth/AuthViewModel.kt
git add app/src/test/java/com/brokenhuskysledteam/spacetradersio/ui/auth/AuthViewModelTest.kt
git commit -m "fix(app): add account token field and validation to AuthViewModel"
```

---

## Task 7: Update `AuthScreen` UI

**Files:**
- Modify: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/auth/AuthScreen.kt`

- [ ] **Step 1: Add Account Token field to `NewAgentTab` and fix `ImportTokenTab`**

Two changes:
1. In `NewAgentTab`: add a `TerminalTextField` for Account Token between the Callsign field and the Faction dropdown.
2. In `ImportTokenTab`: update binding from `uiState.token` / `TokenChanged` to `uiState.agentToken` / `AgentTokenChanged`.

In `NewAgentTab`, after the Callsign `TerminalTextField` and its `Spacer`, add:

```kotlin
TerminalTextField(
    value = uiState.accountToken,
    onValueChange = { onEvent(AuthEvent.AccountTokenChanged(it)) },
    label = "Account Token",
    modifier = Modifier.fillMaxWidth()
)

Spacer(modifier = Modifier.height(12.dp))
```

In `ImportTokenTab`, change:

```kotlin
// Before:
TerminalTextField(
    value = uiState.token,
    onValueChange = { onEvent(AuthEvent.TokenChanged(it)) },
    label = "Bearer Token",
    modifier = Modifier.fillMaxWidth()
)

// After:
TerminalTextField(
    value = uiState.agentToken,
    onValueChange = { onEvent(AuthEvent.AgentTokenChanged(it)) },
    label = "Bearer Token",
    modifier = Modifier.fillMaxWidth()
)
```

- [ ] **Step 2: Build the debug APK**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/auth/AuthScreen.kt
git commit -m "feat(app): add Account Token input field to New Agent registration screen"
```

---

## Task 8: Final verification

- [ ] **Step 1: Run all tests**

```bash
./gradlew :spacetradersiosdk:allTests :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. All tests pass across both modules.

- [ ] **Step 2: Build the APK**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Push the branch**

```bash
git push
```
