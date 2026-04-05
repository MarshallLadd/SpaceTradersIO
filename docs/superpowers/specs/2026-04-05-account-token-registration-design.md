# Account Token Registration — Design Spec

**Date:** 2026-04-05
**Branch:** `bugfix/account-token-registration`

## Problem

The SpaceTraders OpenAPI spec defines two distinct Bearer token types:

- **`AgentToken`** — issued per agent per game reset; used for all gameplay API calls
- **`AccountToken`** — issued per SpaceTraders account; required to create a new agent

`POST /register` uses `AccountToken` security. The current implementation calls this endpoint via the `unauthenticated` client (no `Authorization` header), which causes a `401` from the API. Registration is completely broken without an account token.

There is no API endpoint to create or fetch an account token — it is obtained out-of-band from the SpaceTraders account dashboard.

## Goal

- Accept an account token from the user on the New Agent registration screen
- Send it as `Authorization: Bearer <accountToken>` on the `POST /register` call
- Discard the account token immediately after the call — it is never stored
- Rename `token`/`TokenChanged` to `agentToken`/`AgentTokenChanged` throughout the app layer for clarity

## Approach

`SpaceTradersClient` gains an `authenticatedWith(token: String): HttpClient` factory method. `AccountsApi` is refactored to take `SpaceTradersClient` (matching all other API classes) and calls `authenticatedWith(accountToken)` per registration request. The account token flows from the UI through the use case to the API call and is then dropped — it is never written to `TokenRepository`.

## SDK Layer Changes

### `SpaceTradersClient`

Add:
```kotlin
fun authenticatedWith(token: String): HttpClient
```
Calls the existing private `buildHttpClient(token)`. Exposes the client factory for one-off tokens without changing the existing `unauthenticated` / `authenticated` properties.

### `AccountsApi`

- Constructor: `AccountsApi(private val client: HttpClient)` → `AccountsApi(private val spaceTradersClient: SpaceTradersClient)`
  - Aligns with `AgentsApiImpl`, `ContractsApi`, `FleetApiImpl` which all take `SpaceTradersClient`
- `register()` signature: `suspend fun register(symbol: String, faction: String, accountToken: String): RegisterResponseDto`
- Internally calls `spaceTradersClient.authenticatedWith(accountToken).post("register") { ... }`

### `RegisterAgentUseCase` / `RegisterAgentUseCaseImpl`

- `invoke()` gains `accountToken: String` parameter
- Interface: `suspend operator fun invoke(symbol: String, faction: FactionSymbol, accountToken: String): RegistrationResult`
- Impl passes `accountToken` to `accountsApi.register()` and does not store it

## App Layer Changes

### `AuthUiState`

- Rename `token: String` → `agentToken: String` (used by the Import Token tab)
- Add `accountToken: String = ""` (used by the New Agent tab)

### `AuthEvent`

- Rename `TokenChanged` → `AgentTokenChanged`
- Add `data class AccountTokenChanged(val value: String)`

### `AuthViewModel`

- Handle `AccountTokenChanged` → `_uiState.update { it.copy(accountToken = event.value) }`
- Rename handler for `AgentTokenChanged` accordingly
- `register()` validates `state.accountToken.isBlank()` → error `"Account token cannot be empty"`
- Passes `state.accountToken.trim()` to use case; never writes it to `TokenRepository`
- `importToken()` reads `state.agentToken` (renamed from `state.token`)

### `AuthScreen` / `NewAgentTab`

- Add a `TerminalTextField` for "Account Token" between the Callsign field and the Faction dropdown
- Binds to `uiState.accountToken`, emits `AccountTokenChanged`

### `SdkModule`

- `provideAccountsApi`: `AccountsApi(client.unauthenticated)` → `AccountsApi(client)`

## Test Changes

### New: `AccountsApiTest`

- Uses `MockEngine` to capture request headers
- Verifies `Authorization: Bearer <accountToken>` is present on the `POST /register` request

### Updated: `RegisterAgentUseCaseTest`

- Add `accountToken` argument to all `invoke()` calls
- Add test: `invoke_doesNotSaveAccountTokenToRepository` — verifies only the agent token is written to `TokenRepository`

### Updated: `AuthViewModelTest`

- Update `FakeRegisterAgentUseCase.invoke()` signature to accept `accountToken`
- Add tests:
  - `accountTokenChanged_updatesAccountToken`
  - `registerClicked_blankAccountToken_setsError`
  - `registerClicked_trimsAccountToken`
  - `registerClicked_doesNotSaveAccountTokenToRepository`
- Rename existing token-related tests and events to use `agentToken`/`AgentTokenChanged`
- Update `registerClicked_success_navigatesToDashboard` to also set account token

## Non-Goals

- Storing or caching the account token
- Validating account token format (JWT structure)
- Any UI for obtaining an account token (out-of-band concern)
