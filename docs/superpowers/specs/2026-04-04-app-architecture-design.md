# App Architecture Foundation Design

## Context

The SpaceTraders KMP project was recently restructured into two modules: `:spacetradersiosdk` (shared business logic) and `:app` (Android entry point). The SDK has agent registration, contract management, token storage, and API clients. The app is currently a skeleton with a "Hello Android!" screen. This design establishes the app's architectural foundation — dependency injection, navigation, state management, and an initial auth-gated screen flow — so that features can be built on a solid base.

## Decisions

| Area | Choice |
|---|---|
| Dependency Injection | Hilt |
| Navigation | Jetpack Navigation Compose (type-safe `@Serializable` routes) |
| State Management | UDF with `StateFlow<UiState>` + sealed interface events |
| Navigation Signals | `Channel<NavigationTarget>` consumed via `receiveAsFlow()` |
| Token Import Strategy | Store-and-go with revert on first API failure |

## 1. Dependency Injection

### New Dependencies

- `com.google.dagger:hilt-android` + `hilt-android-compiler` (KSP)
- `androidx.hilt:hilt-navigation-compose`
- Gradle plugins: `com.google.dagger.hilt.android`, `com.google.devtools.ksp`

### Hilt Module: `SdkModule`

`@Module @InstallIn(SingletonComponent::class)` — provides all SDK classes via `@Provides`:

| Binding | Scope | Notes |
|---|---|---|
| `Settings` → `Settings()` | `@Singleton` | No-arg constructor uses SharedPreferences on Android |
| `TokenRepository` → `TokenRepositoryImpl(settings)` | `@Singleton` | |
| `SpaceTradersClient(tokenRepository)` | `@Singleton` | |
| `AccountsApi(client.unauthenticated)` | `@Singleton` | Unauthenticated client is a fixed `val`, safe to capture |
| `AgentsApi(spaceTradersClient)` | `@Singleton` | Takes `SpaceTradersClient`, not `HttpClient` (see SDK change below) |
| `ContractsApi(spaceTradersClient)` | `@Singleton` | Same pattern |
| `RegisterAgentUseCase(accountsApi, tokenRepository)` | Unscoped | Stateless, cheap to create |
| Other use cases | Unscoped | Same reasoning |

### Application & Activity

- New `SpaceTradersApplication` class annotated with `@HiltAndroidApp`
- `MainActivity` annotated with `@AndroidEntryPoint`
- Register `SpaceTradersApplication` in `AndroidManifest.xml` via `android:name`

### SDK Change Required

`AgentsApi` and `ContractsApi` constructors change from `HttpClient` to `SpaceTradersClient`. Each API method calls `client.authenticated` internally, so the auth header is resolved per-request from the current stored token.

`AccountsApi` stays with `HttpClient` (unauthenticated) since registration doesn't need auth.

## 2. Navigation & Screen Flow

### Routes

```kotlin
@Serializable object AuthRoute
@Serializable object DashboardRoute
```

### Auth Gate

In the root `SpaceTradersApp` composable:

1. Check `TokenRepository.hasToken()` at launch
2. `startDestination` = `DashboardRoute` if token exists, `AuthRoute` otherwise
3. After successful auth → navigate to `DashboardRoute`, pop auth from back stack
4. Logout → `TokenRepository.clearToken()`, navigate to `AuthRoute`, clear back stack

### Auth Screen (Tabbed)

**Tab 1 — "New Agent":**
- Text field: callsign (agent name)
- Selector: faction (`FactionSymbol` enum values)
- Register button → calls `RegisterAgentUseCase`
- On success: navigate to dashboard

**Tab 2 — "Import Token":**
- Text field: paste bearer token
- Connect button → calls `TokenRepository.saveToken(token)` directly (no use case — this is a single-call operation with no business logic)
- Navigate to dashboard (store-and-go)

### Dashboard Screen (Placeholder)

- Calls `AgentsApi.getMyAgent()` on entry to display agent symbol and credits
- Logout button
- Validates token import: if `getMyAgent()` returns 401/403, clear token and navigate back to auth

## 3. State Management

### Pattern

Each screen has a `@HiltViewModel` exposing:
- `val uiState: StateFlow<XxxUiState>` — single immutable data class
- `fun onEvent(event: XxxEvent)` — handles user actions
- `val navigationEvent: Flow<NavigationTarget>` — one-shot navigation signals via `Channel`

### AuthViewModel

```kotlin
data class AuthUiState(
    val selectedTab: AuthTab = AuthTab.NEW_AGENT,
    // New Agent tab
    val callsign: String = "",
    val selectedFaction: FactionSymbol = FactionSymbol.COSMIC,
    val isRegistering: Boolean = false,
    // Import Token tab
    val token: String = "",
    val isImporting: Boolean = false,
    // Shared
    val error: String? = null
)

enum class AuthTab { NEW_AGENT, IMPORT_TOKEN }

sealed interface AuthEvent {
    data class TabSelected(val tab: AuthTab) : AuthEvent
    data class CallsignChanged(val value: String) : AuthEvent
    data class FactionSelected(val faction: FactionSymbol) : AuthEvent
    data class TokenChanged(val value: String) : AuthEvent
    data object RegisterClicked : AuthEvent
    data object ImportClicked : AuthEvent
    data object ErrorDismissed : AuthEvent
}
```

### DashboardViewModel

```kotlin
data class DashboardUiState(
    val agent: Agent? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

sealed interface DashboardEvent {
    data object RetryClicked : DashboardEvent
    data object LogoutClicked : DashboardEvent
}
```

On init: loads agent via `AgentsApi.getMyAgent()`. On 401/403: clears token, signals navigation to auth.

## 4. Error Handling

- SDK use cases throw exceptions (Ktor `ClientRequestException` for HTTP errors, `IOException` for network)
- ViewModels catch in `viewModelScope.launch` and map to `UiState.error` strings
- Token import failure path: `DashboardViewModel` catches 401/403 → `tokenRepository.clearToken()` → navigate to auth with error context
- `ErrorDismissed` event sets `error = null`

## 5. Testing

### ViewModel Tests (`app/src/test/`)

- Construct ViewModels with fakes for SDK dependencies (fake `TokenRepository`, fake use cases)
- Verify every state transition for every event
- Verify navigation signals emitted correctly
- Verify error paths (exception → error state → dismiss)
- **Target: 100% class, method, line, and branch coverage**

### SDK Test Updates (`spacetradersiosdk/src/androidHostTest/`)

- Update `AgentsApi`/`ContractsApi` tests to pass `SpaceTradersClient` instead of `HttpClient`
- Create a test `SpaceTradersClient` backed by `MockEngine`

### Out of Scope

- Compose UI tests (screens are thin presentation; ViewModel tests cover logic)
- Integration/E2E tests

## Files to Create/Modify

### New Files (app module)
- `app/src/main/java/.../SpaceTradersApplication.kt` — `@HiltAndroidApp`
- `app/src/main/java/.../di/SdkModule.kt` — Hilt module
- `app/src/main/java/.../navigation/Routes.kt` — route objects
- `app/src/main/java/.../navigation/SpaceTradersNavHost.kt` — NavHost setup
- `app/src/main/java/.../ui/auth/AuthViewModel.kt`
- `app/src/main/java/.../ui/auth/AuthScreen.kt`
- `app/src/main/java/.../ui/auth/AuthUiState.kt`
- `app/src/main/java/.../ui/dashboard/DashboardViewModel.kt`
- `app/src/main/java/.../ui/dashboard/DashboardScreen.kt`
- `app/src/main/java/.../ui/dashboard/DashboardUiState.kt`
- `app/src/test/java/.../ui/auth/AuthViewModelTest.kt`
- `app/src/test/java/.../ui/dashboard/DashboardViewModelTest.kt`

### Modified Files
- `app/build.gradle.kts` — add Hilt, KSP, navigation-compose, hilt-navigation-compose deps + SDK module dep
- `build.gradle.kts` (root) — add Hilt and KSP plugins (apply false)
- `gradle/libs.versions.toml` — add Hilt, KSP, navigation-compose version entries
- `app/src/main/AndroidManifest.xml` — add `android:name=".SpaceTradersApplication"`
- `app/src/main/java/.../MainActivity.kt` — add `@AndroidEntryPoint`, replace content with `SpaceTradersApp()`
- `spacetradersiosdk/.../api/endpoints/AgentsApi.kt` — constructor change to `SpaceTradersClient`
- `spacetradersiosdk/.../api/endpoints/ContractsApi.kt` — constructor change to `SpaceTradersClient`
- Existing SDK tests — update to match new API class constructors

## Verification

1. `./gradlew :spacetradersiosdk:allTests` — SDK tests pass with constructor changes
2. `./gradlew :app:assembleDebug` — app builds with Hilt, navigation, all new code
3. `./gradlew :app:testDebugUnitTest` — ViewModel tests pass with 100% coverage
4. Manual: launch on emulator, verify auth gate → register screen → register → dashboard flow
5. Manual: logout → import token tab → paste token → dashboard (or failure → revert to auth)
