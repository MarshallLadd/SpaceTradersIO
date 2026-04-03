# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SpaceTraders is a Kotlin Multiplatform (KMP) client for the [SpaceTraders IO](https://spacetraders.io) REST API MMO. The full OpenAPI spec lives at `../OpenAPISpec/space_trader_open_api_spec.json` relative to this directory and is the authoritative reference for all API endpoints, request/response shapes, and auth schemes.

**Targets:** Android (minSdk 28) and iOS (arm64 + simulator). Desktop/web are out of scope for now.

## Git Branching Strategy

This project follows Git Flow:

- **`main`** — production-ready releases only. Never commit directly.
- **`develop`** — integration branch. All feature/bugfix work merges here first.
- **`feature/<name>`** — branched from `develop`, merged back to `develop` via PR.
- **`bugfix/<name>`** — branched from `develop`, merged back to `develop` via PR.
- **`release/<version>`** — branched from `develop` when cutting a release, merged into both `main` and `develop`.
- **`hotfix/<name>`** — branched from `main` for critical production fixes, merged into both `main` and `develop`.

```bash
# Start a new feature
git checkout develop && git pull
git checkout -b feature/my-feature

# Finish and push for PR
git push -u origin feature/my-feature
# Open PR targeting develop (GitHub default)
```

`develop` is the default branch on GitHub — all PRs target it automatically.

## Build Commands

All commands run from the `KMP/` directory. On Windows use `gradlew.bat` instead of `./gradlew`.

```bash
# Build Android debug APK
./gradlew :composeApp:assembleDebug

# Run all common tests
./gradlew :composeApp:testDebugUnitTest

# Run a single test class
./gradlew :composeApp:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetraders.MyTest"

# Sync and check the build without assembling
./gradlew :composeApp:compileDebugKotlinAndroid
```

iOS is built from Xcode using `iosApp/iosApp.xcodeproj` or via the IDE run configuration.

## Architecture

Single Gradle module (`composeApp`) with three source sets:

- **`commonMain`** — all shared logic: API layer, domain models, ViewModels, and Compose UI screens. This is where most code lives.
- **`androidMain`** — Android entry point (`MainActivity`) and platform actuals.
- **`iosMain`** — iOS entry point (`MainViewController`) and platform actuals.

The `expect/actual` mechanism in `Platform.kt` is the current example of platform-specific behaviour.

### Planned layer structure inside `commonMain`

```
api/        ← Ktor HttpClient, endpoint functions, request/response DTOs
domain/     ← Business models, use cases
ui/         ← Compose screens and ViewModels (androidx.lifecycle)
```

## Key Dependencies & Versions

| Dependency | Version |
|---|---|
| Kotlin | 2.3.0 |
| Compose Multiplatform | 1.10.0 |
| AGP | 8.11.2 |
| androidx.lifecycle (ViewModel/runtime) | 2.9.6 |
| Ktor | 3.4.2 |
| kotlinx-serialization | 1.10.0 |
| Koin | 4.2.0 |
| multiplatform-settings | 1.3.0 |

Ktor uses the `okhttp` engine for Android and `darwin` for iOS — both are already wired in `gradle/libs.versions.toml`.

## Testing

Tests live in `composeApp/src/commonTest/`. Test dependencies in `build.gradle.kts` commonTest block: `kotlin.test`, `ktor-client-mock`, `kotlinx-coroutines-test`.

- **Pure unit tests** (mappers, enums): no extra setup needed beyond `kotlin.test`
- **Use case tests**: back `AccountsApi`/`ContractsApi` with Ktor `MockEngine`; use hand-written fakes for repository interfaces (no mockk); use `runTest` for suspend functions
- **Test `HttpClient` must include `defaultRequest { contentType(ContentType.Application.Json) }`** — omitting it causes `Fail to prepare request body` because `setBody()` requires Content-Type, matching the production `SpaceTradersClient` setup

## Gotchas

- `gradlew` must have the executable bit set in git (`git update-index --chmod=+x gradlew`). Windows does not preserve Unix permissions — omitting this causes CI to fail with exit code 126.
- The repo root IS the KMP project root. Git was initialized inside `KMP/`, so there is no `KMP/` subdirectory on CI runners or in the repo. Do not use `working-directory: KMP` in GitHub Actions workflows.
- In Koin 4.x, the Compose Multiplatform artifact is `io.insert-koin:koin-compose`, not `koin-compose-multiplatform` (that artifact does not exist on Maven Central).
- `compileKotlinAndroid` is ambiguous in Gradle — always use `compileDebugKotlinAndroid`.

## SpaceTraders API

- Base URL: `https://api.spacetraders.io/v2`
- Auth: Bearer token (JWT). Two token types: `AgentToken` (per-agent gameplay) and `AccountToken` (account management). Agent registration returns the bearer token directly.
- The OpenAPI spec (`../OpenAPISpec/space_trader_open_api_spec.json`) is the ground truth — always check it before implementing a new endpoint.
- Pagination is used extensively: requests accept `page` and `limit`, responses include a `meta` object with `total`, `page`, `limit`.

## Mobile MCP (Android Emulator Interaction)

The `mobile-mcp` MCP server enables live interaction with the running Android emulator.

- **Always use `mobile_list_elements_on_screen` for click targets** — never estimate coordinates from screenshots. Screenshots render at half native resolution (e.g. 720px wide) but element coordinates are in native pixel space (1080px). Guessing from screenshots will miss.
- **Workflow:** `mobile_list_elements_on_screen` → get coordinates → `mobile_click_on_screen_at_coordinates` → `mobile_take_screenshot` to verify.
- **Always call `mobile_list_available_devices` first** — never assume a device ID; the connected device changes frequently.
- **Windows gotcha:** The MCP server command must use a `cmd /c` wrapper — `command: "cmd", args: ["/c", "npx", "@mobilenext/mobile-mcp@latest"]` in `.claude.json`. Plain `npx` is a `.cmd` script and cannot be spawned directly on Windows.
- MCP servers connect at session startup — config changes require a session restart to take effect.

## Gradle Config Notes

- Configuration cache and build cache are both enabled (`gradle.properties`).
- Daemon JVM heap: 3 GB; build JVM heap: 4 GB.
- `TYPESAFE_PROJECT_ACCESSORS` feature preview is enabled in `settings.gradle.kts`.
