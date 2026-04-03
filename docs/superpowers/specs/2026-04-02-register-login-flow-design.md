# Register / Login Flow — Design Spec
**Date:** 2026-04-02  
**Status:** Approved

---

## Prerequisites

Before implementing this spec, complete the KMP module restructure (`chore/restructure-kmp-modules`). The current single-module layout (`composeApp` with both `kotlin.multiplatform` and `com.android.application`) is incompatible with AGP 9.0.0+. All file paths in this spec reference paths within the shared KMP module's source sets and are correct for the post-restructure layout.

---

## Context

The app currently has a solid backend infrastructure (API layer, domain models, use cases, token storage, Koin DI) but no UI whatsoever. This spec covers the first runnable feature: a register/login flow that lets a user create a new SpaceTraders agent or connect an existing one via token, and then view their agent details. The token is persisted so subsequent launches skip straight to the agent screen.

The guiding principle throughout: **the API is the source of truth**. All state changes (token save, navigation) happen only after a successful API response.

---

## Architecture

### Navigation
Use `androidx.navigation.compose` (KMP-compatible in Compose Multiplatform 1.10.0). Typed route definitions in a sealed class `AppRoutes`. `App.kt` becomes the `NavHost` entry point.

**Route graph:**
```
Welcome  ←── start destination when no token
  ├──► Register
  └──► TokenEntry
Register  ──► Agent  (popUpTo Welcome inclusive)
TokenEntry ──► Agent  (popUpTo Welcome inclusive)
Agent     ──► Welcome (on logout, popUpTo Agent inclusive)
```

`App.kt` reads `TokenRepository.hasToken()` synchronously at startup to pick the correct start destination — no flicker, no extra loading screen.

### File Structure
All new files live in `commonMain/kotlin/com/brokenhuskysledteam/spacetraders/`:

```
ui/
├── navigation/
│   └── AppRoutes.kt
├── welcome/
│   └── WelcomeScreen.kt
├── register/
│   ├── RegisterScreen.kt
│   └── RegisterViewModel.kt
├── tokenentry/
│   ├── TokenEntryScreen.kt
│   └── TokenEntryViewModel.kt
└── agent/
    ├── AgentScreen.kt
    └── AgentViewModel.kt

domain/usecase/
├── GetMyAgentUseCase.kt       ← new
└── ValidateAndSaveTokenUseCase.kt  ← new
```

---

## UI State Model

Each ViewModel exposes a `StateFlow` of a screen-specific sealed interface. The composable `when`-branches on it exhaustively.

### State shapes

**`RegisterUiState`**
```kotlin
sealed interface RegisterUiState {
    data object Idle : RegisterUiState
    data object Submitting : RegisterUiState
    data class Error(val message: String) : RegisterUiState
}
```
On success, the ViewModel emits a one-shot navigation event (via `Channel<Unit>`) rather than embedding nav logic in UI state.

**`TokenEntryUiState`**
```kotlin
sealed interface TokenEntryUiState {
    data object Idle : TokenEntryUiState
    data object Validating : TokenEntryUiState
    data class Error(val message: String) : TokenEntryUiState
}
```

**`AgentUiState`**
```kotlin
sealed interface AgentUiState {
    data object Loading : AgentUiState
    data class Success(val agent: Agent) : AgentUiState
    data class Error(val message: String) : AgentUiState
}
```

### Loading placeholders
- **Form screens (Register, TokenEntry):** Form is already visible. On submit, the CTA button replaces its label with a `CircularProgressIndicator`; all inputs are disabled. No separate loading screen.
- **Agent screen:** Unknown content until API responds. The `Loading` branch renders a skeleton layout — grey `Box` composables in the shape of the agent card fields (symbol, credits, faction, etc.), with a gentle alpha-pulse animation using Compose's built-in `rememberInfiniteTransition`. No extra library required.

### Error presentation
Blocking `AlertDialog` for all error states during this phase. Easy to swap to `Snackbar` or inline text later.

---

## Screens

### WelcomeScreen
Two CTAs: **"Create Agent"** (→ Register) and **"Enter Token"** (→ TokenEntry). No ViewModel needed — purely navigational.

### RegisterScreen
- Text field: callsign/symbol input
- Faction selector: scrollable grid of 18 faction cards, each with a placeholder image and the faction name. Backed by `FactionSymbol` enum (already exists).
- CTA: **"Register"** — transitions to `Submitting` state on tap
- On success: navigate to Agent
- On error: `AlertDialog` with API error message, form re-enabled

### TokenEntryScreen
- Multi-line text field for pasting a bearer token
- CTA: **"Connect"** — transitions to `Validating` state on tap
- On success: navigate to Agent
- On error: `AlertDialog` with API error message, field re-enabled

### AgentScreen
- Top app bar with agent symbol as title and a logout icon button (top-right)
- Body: agent detail card showing symbol, credits, headquarters, starting faction, ship count
- `Loading` branch: skeleton layout (grey `Box` composables with alpha-pulse animation)
- `Error` branch: `AlertDialog`
- Logout: clears token → navigates to Welcome

---

## Data Flow

### App startup
```
TokenRepository.hasToken()
  ├── true  → startDestination = Agent route
  └── false → startDestination = Welcome route
```

### Registration
```
callsign + faction → RegisterViewModel
  → RegisterAgentUseCase (existing)
    → AccountsApi.register() [unauthenticated client]
    → TokenRepository.saveToken()   ← only on API success
    → emit navigation event → Agent screen
```

### Token entry (validate-before-save)
```
pasted token → TokenEntryViewModel
  → ValidateAndSaveTokenUseCase (new)
    1. TokenRepository.saveToken(token)   ← temp
    2. AgentsApi.getMyAgent()             ← authenticated client reads from repo
    ├── success → return Agent (token stays saved)
    └── failure → TokenRepository.clearToken(), propagate error
  → emit navigation event → Agent screen
```
*Note: save-then-validate is required because `SpaceTradersClient.authenticated` reads the token from `TokenRepository` on every access — there is no mechanism to pass a token directly.*

### Agent screen load
```
AgentViewModel.init
  → GetMyAgentUseCase (new)
    → AgentsApi.getMyAgent()
    → AgentDto.toDomain()
  → emit AgentUiState.Success(agent)
```

### Logout
```
AgentViewModel.logout()
  → TokenRepository.clearToken()
  → emit navigation event → Welcome screen (popUpTo Agent inclusive)
```

---

## New Use Cases

### `GetMyAgentUseCase`
```kotlin
class GetMyAgentUseCase(private val agentsApi: AgentsApi) {
    suspend operator fun invoke(): Agent =
        agentsApi.getMyAgent().toDomain()
}
```

### `ValidateAndSaveTokenUseCase`
```kotlin
class ValidateAndSaveTokenUseCase(
    private val tokenRepository: TokenRepository,
    private val agentsApi: AgentsApi
) {
    suspend operator fun invoke(token: String): Agent {
        tokenRepository.saveToken(token)
        return try {
            agentsApi.getMyAgent().toDomain()
        } catch (e: Exception) {
            tokenRepository.clearToken()
            throw e
        }
    }
}
```

---

## Koin Wiring Additions

In `AppModule.kt`:
```kotlin
single { GetMyAgentUseCase(get()) }
single { ValidateAndSaveTokenUseCase(get(), get()) }

viewModel { RegisterViewModel(get()) }
viewModel { TokenEntryViewModel(get()) }
viewModel { AgentViewModel(get()) }
```

---

## Testing

All tests in `commonTest/`. `MockEngine` for HTTP, hand-written fakes for repositories, `runTest` for coroutines. No mockk.

| Test class | What it covers |
|---|---|
| `GetMyAgentUseCaseTest` | Correct endpoint called, DTO mapped to `Agent` |
| `ValidateAndSaveTokenUseCaseTest` | Success: token saved, agent returned. Failure: token cleared, exception propagated |
| `RegisterViewModelTest` | Idle→Submitting→nav event on success; Idle→Submitting→Error on failure, token not saved |
| `TokenEntryViewModelTest` | Idle→Validating→nav event on success; Idle→Validating→Error on failure, token cleared |
| `AgentViewModelTest` | Loading→Success on init; Loading→Error on API failure; logout clears token + nav event |

---

## Verification

1. Build: `gradlew :composeApp:compileDebugKotlinAndroid` — must compile clean
2. Tests: `gradlew :composeApp:testDebugUnitTest` — all new tests pass
3. Run on emulator via Android Studio or `gradlew :composeApp:assembleDebug` + install
4. Happy path — new agent: launch → Welcome → Register (enter callsign, pick faction) → Agent screen shows real data
5. Happy path — existing token: launch → Welcome → TokenEntry (paste token) → Agent screen
6. Token persistence: kill and relaunch the app → lands directly on Agent screen
7. Logout: tap logout icon → Welcome screen, token cleared, relaunch confirms no token
8. Error paths: register with taken callsign → dialog shown, form re-enabled; paste invalid token → dialog shown, field re-enabled
