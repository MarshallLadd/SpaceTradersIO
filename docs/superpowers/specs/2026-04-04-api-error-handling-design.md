# SpaceTraders API Error Handling — Design Spec

**Date:** 2026-04-04
**Branch:** `feature/api-error-handling` (to be created from `develop`)

## Context

The SpaceTraders API returns structured error responses with ~90 custom error codes (3000–5000 range). Currently, the SDK has **no error deserialization** — Ktor throws raw `ClientRequestException` on non-2xx responses, and ViewModels catch these with ad-hoc `when` checks on HTTP status codes. The API error payload (which includes a numeric code, message, and optional data) is never parsed.

This means the app cannot distinguish between "token expired" and "ship cargo full" — they're both opaque exception messages. As more endpoints are implemented, this becomes a maintenance burden and prevents meaningful error UX.

**Goal:** Add a typed error model to the SDK that deserializes API error responses into a sealed class hierarchy, thrown as a `SpaceTradersApiException`. The app can then `when`-match on specific error types or categories.

## Design Decisions

| Decision | Choice | Rationale |
|---|---|---|
| SDK responsibility | Model + parse only | App controls recovery strategy (retry, navigate, display) |
| Error representation | Sealed class hierarchy | Rich type-matching by category and specific error |
| Surfacing mechanism | Typed exception via HttpCallValidator | No change to endpoint return types; centralized parsing |

## 1. Error Response DTO

**File:** `spacetradersiosdk/src/commonMain/.../api/dto/ErrorResponseDto.kt`

```kotlin
@Serializable
data class ErrorResponseDto(
    val error: ErrorBodyDto
)

@Serializable
data class ErrorBodyDto(
    val code: Int,
    val message: String,
    val data: JsonObject? = null
)
```

The API wraps errors in `{ "error": { "code": ..., "message": ..., "data": ... } }`. The `data` field is an untyped `JsonObject` because its shape varies by error code — callers can inspect it when needed but it's not required.

## 2. Sealed Error Hierarchy

**File:** `spacetradersiosdk/src/commonMain/.../domain/model/SpaceTradersError.kt`

Categories based on the error code ranges from `space_trader_api_error_codes.json`:

```
SpaceTradersError (sealed)
├── GeneralError (sealed)         — 3000-3200: serialization, validation, maintenance, reset
├── CooldownError (data class)    — 4000: cooldown conflict
├── WaypointAccessError (data)    — 4001: waypoint no access
├── AuthError (sealed)            — 4100-4116: token/account issues
├── NavigationError (sealed)      — 4200-4204: route/destination/fuel
├── ShipOperationError (sealed)   — 4205-4271: extraction, cargo, survey, modules, mounts, etc.
├── ContractError (sealed)        — 4500-4511: accept/deliver/fulfill
├── MarketError (sealed)          — 4600-4605: trade/purchase
├── WaypointFactionError (data)   — 4700: waypoint faction
├── ConstructionError (sealed)    — 4800-4802: construction materials
├── MediaTypeError (data class)   — 5000: unsupported media type
└── Unknown (data class)          — fallback for unrecognized codes
```

Every leaf node is a `data class` carrying:
- `code: Int` — the API error code
- `message: String` — the API error message
- `data: JsonObject?` — optional error-specific payload

Every sealed parent defines these as `abstract` properties.

The `Unknown` fallback ensures forward compatibility — if the API adds new error codes before the SDK is updated, they still get caught and surfaced rather than falling through as deserialization errors.

## 3. SpaceTradersApiException

**File:** `spacetradersiosdk/src/commonMain/.../domain/model/SpaceTradersApiException.kt`

```kotlin
class SpaceTradersApiException(
    val error: SpaceTradersError,
    val httpStatus: Int
) : Exception(error.message)
```

Simple exception wrapper. The `message` property delegates to the error's message for stack trace readability.

## 4. Error Mapper

**File:** `spacetradersiosdk/src/commonMain/.../api/mapper/ErrorMapper.kt`

A single `fun ErrorBodyDto.toDomain(): SpaceTradersError` function that maps numeric codes to sealed hierarchy instances. Uses a `when` expression on the code. Falls through to `SpaceTradersError.Unknown` for unrecognized codes.

## 5. HttpCallValidator Installation

**File:** `spacetradersiosdk/src/commonMain/.../api/client/SpaceTradersClient.kt` (modify existing)

Install `HttpCallValidator` in the `buildHttpClient` function:

```kotlin
install(HttpCallValidator) {
    validateResponse { response ->
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            val errorDto = json.decodeFromString<ErrorResponseDto>(errorBody)
            val error = errorDto.error.toDomain()
            throw SpaceTradersApiException(
                error = error,
                httpStatus = response.status.value
            )
        }
    }
}
```

This intercepts **before** Ktor's default `ClientRequestException` — so callers never see raw Ktor exceptions for API errors. Network errors (no connectivity, DNS failure, timeouts) still propagate as `IOException`.

**Malformed error body fallback:** If the response body can't be parsed as `ErrorResponseDto` (e.g., HTML error page from a proxy), catch the deserialization failure and throw a `SpaceTradersApiException` with `SpaceTradersError.Unknown(code = 0, message = bodyText, data = null)` and the HTTP status code. This ensures non-2xx responses always become `SpaceTradersApiException`, never raw Ktor exceptions.

The `Json` instance used for decoding must match the client's configuration (lenient, ignore unknown keys). Extract the existing `Json { ... }` block into a shared `val` in `SpaceTradersClient`.

## 6. App Layer Migration

### DashboardViewModel

**File:** `app/src/main/java/.../ui/dashboard/DashboardViewModel.kt`

Replace the current two-catch pattern:
```kotlin
// Before
catch (e: ClientRequestException) {
    if (e.response.status == HttpStatusCode.Unauthorized || ...)
}
catch (e: Exception) { ... }

// After
catch (e: SpaceTradersApiException) {
    when (e.error) {
        is SpaceTradersError.AuthError -> {
            tokenRepository.clearToken()
            _navigationEvent.send(NavigationTarget.Auth)
        }
        else -> _uiState.update { it.copy(isLoading = false, error = e.message) }
    }
}
catch (e: Exception) {
    // Network errors, etc.
    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load agent") }
}
```

The `ClientRequestException` import and `HttpStatusCode` import can be removed.

### AuthViewModel

**File:** `app/src/main/java/.../ui/auth/AuthViewModel.kt`

Replace generic `Exception` catch with `SpaceTradersApiException` first, then generic `Exception` fallback:
```kotlin
catch (e: SpaceTradersApiException) {
    when (e.error) {
        is SpaceTradersError.AuthError.RegisterAgentConflictSymbol ->
            _uiState.update { it.copy(isRegistering = false, error = "Callsign already taken") }
        is SpaceTradersError.AuthError.RegisterAgentSymbolReserved ->
            _uiState.update { it.copy(isRegistering = false, error = "Callsign is reserved") }
        else ->
            _uiState.update { it.copy(isRegistering = false, error = e.message) }
    }
}
catch (e: Exception) {
    _uiState.update { it.copy(isRegistering = false, error = e.message ?: "Registration failed") }
}
```

## 7. Testing Strategy

### SDK Tests (in `spacetradersiosdk/src/androidHostTest/`)

| Test class | What it covers |
|---|---|
| `ErrorMapperTest` | Every error code maps to the correct sealed type. Unknown codes map to `Unknown`. |
| `ErrorResponseDtoTest` | Deserialization of error JSON payloads, including with/without `data` field. |
| `SpaceTradersClientErrorTest` | MockEngine returns non-2xx responses with error JSON bodies; verifies `SpaceTradersApiException` is thrown with correct error type and HTTP status. Tests both authenticated and unauthenticated clients. |

### App Tests (in `app/src/test/`)

| Test class | What it covers |
|---|---|
| `DashboardViewModelTest` | Update existing tests: auth errors trigger navigation, other API errors show in UI state, non-API exceptions still handled. |
| `AuthViewModelTest` | Update existing tests: registration conflict shows user-friendly message, generic API errors show server message. |

### Coverage targets

- `ErrorMapper`: 100% branch coverage (every code in the `when` expression, plus the `else` branch)
- `SpaceTradersApiException`: 100% (constructor + message delegation)
- `SpaceTradersError` sealed hierarchy: 100% class coverage (every leaf instantiated in mapper tests)
- `HttpCallValidator` integration: success passthrough, error interception, malformed error body fallback

## Files to Create

| File | Location |
|---|---|
| `ErrorResponseDto.kt` | `spacetradersiosdk/src/commonMain/.../api/dto/` |
| `SpaceTradersError.kt` | `spacetradersiosdk/src/commonMain/.../domain/model/` |
| `SpaceTradersApiException.kt` | `spacetradersiosdk/src/commonMain/.../domain/model/` |
| `ErrorMapper.kt` | `spacetradersiosdk/src/commonMain/.../api/mapper/` |
| `ErrorMapperTest.kt` | `spacetradersiosdk/src/androidHostTest/.../api/mapper/` |
| `ErrorResponseDtoTest.kt` | `spacetradersiosdk/src/androidHostTest/.../api/dto/` |
| `SpaceTradersClientErrorTest.kt` | `spacetradersiosdk/src/androidHostTest/.../api/client/` |

## Files to Modify

| File | Change |
|---|---|
| `SpaceTradersClient.kt` | Extract shared `Json` instance, install `HttpCallValidator` |
| `DashboardViewModel.kt` | Catch `SpaceTradersApiException`, match on `AuthError` |
| `AuthViewModel.kt` | Catch `SpaceTradersApiException`, match on registration errors |
| `DashboardViewModelTest.kt` | Update error fakes to throw `SpaceTradersApiException` |
| `AuthViewModelTest.kt` | Update error fakes to throw `SpaceTradersApiException` |

## Verification

1. `gradlew.bat :spacetradersiosdk:allTests` — all new and existing SDK tests pass
2. `gradlew.bat :app:testDebugUnitTest` — all new and existing app tests pass
3. `gradlew.bat :app:assembleDebug` — app builds cleanly
4. Manual: run app on emulator, import an invalid token, verify auth error navigates back to login screen
