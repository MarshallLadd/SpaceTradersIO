# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SpaceTraders is a Kotlin Multiplatform (KMP) SDK and Android client for the [SpaceTraders IO](https://spacetraders.io) REST API MMO. The full OpenAPI spec lives at `../OpenAPISpec/space_trader_open_api_spec.json` relative to this directory and is the authoritative reference for all API endpoints, request/response shapes, and auth schemes.

**Targets:** The SDK targets Android (minSdk 28) and iOS (arm64 + simulator). The Android app is a standalone Jetpack Compose application. An iOS app will consume the SDK's xcframework natively. Desktop/web are out of scope.

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
./gradlew :app:assembleDebug

# Run all SDK common tests
./gradlew :spacetradersiosdk:testDebugUnitTest

# Run a single test class
./gradlew :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetraders.MyTest"

# Sync and check the build without assembling
./gradlew :spacetradersiosdk:compileDebugKotlinAndroid
```

iOS framework is built via `./gradlew :spacetradersiosdk:assembleSpacetradersiosdkKitReleaseXCFramework`. There is no iOS app in this repo yet — the SDK produces an xcframework (`spacetradersiosdkKit`) for consumption by a native Swift/Xcode project.

## Architecture

Two Gradle modules: `:spacetradersiosdk` (KMP shared library) and `:app` (Android entry point).

`:spacetradersiosdk` has three source sets:

- **`commonMain`** — all shared logic: API layer, domain models, use cases, data repositories. No UI code.
- **`androidMain`** — Platform actuals (`Platform.android.kt`) and Android Ktor engine (OkHttp).
- **`iosMain`** — Platform actuals (`Platform.ios.kt`) and iOS Ktor engine (Darwin).

`:app` is a standalone Android application using Jetpack Compose (not Compose Multiplatform). It owns all UI, ViewModels, navigation, and DI. It depends on `:spacetradersiosdk` for business logic.

The `expect/actual` mechanism in `Platform.kt` is the current example of platform-specific behaviour.

### Layer structure inside `spacetradersiosdk/src/commonMain`

```
api/        <- Ktor HttpClient, endpoint functions, request/response DTOs
api/mapper/ <- DTO-to-domain-model extension functions
domain/     <- Business models, use cases, repository interfaces
data/       <- Repository implementations
```

## Key Dependencies & Versions

| Dependency | Version |
|---|---|
| Kotlin | 2.3.20 |
| Compose BOM (Jetpack, app only) | 2026.03.01 |
| AGP | 9.1.0 |
| androidx.lifecycle (app only) | 2.10.0 |
| Ktor | 3.4.2 |
| kotlinx-serialization | 1.10.0 |
| Napier (logging) | 2.7.1 |
| multiplatform-settings | 1.3.0 |

Ktor uses the `okhttp` engine for Android and `darwin` for iOS — both are already wired in `gradle/libs.versions.toml`.

## Testing

Tests live in `spacetradersiosdk/src/commonTest/`. Android-specific tests use `androidHostTest` (JVM unit) and `androidDeviceTest` (instrumented). Test dependencies in `build.gradle.kts` commonTest block: `kotlin.test`, `ktor-client-mock`.

- **Pure unit tests** (mappers, enums): no extra setup needed beyond `kotlin.test`
- **Use case tests**: back `AccountsApi`/`ContractsApi` with Ktor `MockEngine`; use hand-written fakes for repository interfaces (no mockk); use `runTest` for suspend functions
- **Test `HttpClient` must include `defaultRequest { contentType(ContentType.Application.Json) }`** — omitting it causes `Fail to prepare request body` because `setBody()` requires Content-Type, matching the production `SpaceTradersClient` setup

## Gotchas

- `gradlew` must have the executable bit set in git (`git update-index --chmod=+x gradlew`). Windows does not preserve Unix permissions — omitting this causes CI to fail with exit code 126.
- The repo root IS the KMP project root. Git was initialized inside `KMP/`, so there is no `KMP/` subdirectory on CI runners or in the repo. Do not use `working-directory: KMP` in GitHub Actions workflows.
- `compileKotlinAndroid` is ambiguous in Gradle — always use `compileDebugKotlinAndroid`.
- Package namespace migration is in progress: most SDK files use `com.brokenhuskysledteam.spacetraders.*` (old), target is `com.brokenhuskysledteam.spacetradersio.sdk.*`. Both coexist until migration completes.
- CI workflows (`.github/workflows/`) still reference the old `:composeApp` module and need updating before merging to develop.

## SpaceTraders API

- Base URL: `https://api.spacetraders.io/v2`
- Auth: Bearer token (JWT). Two token types: `AgentToken` (per-agent gameplay) and `AccountToken` (account management). Agent registration returns the bearer token directly.
- The OpenAPI spec (`../OpenAPISpec/space_trader_open_api_spec.json`) is the ground truth — always check it before implementing a new endpoint.
- Pagination is used extensively: requests accept `page` and `limit`, responses include a `meta` object with `total`, `page`, `limit`.

## Mobile MCP (Android Emulator Interaction)

The `mobile-mcp` MCP server enables live interaction with the running Android emulator.

- **Always use `mobile_list_elements_on_screen` for click targets** — never estimate coordinates from screenshots. Screenshots render at half native resolution (e.g. 720px wide) but element coordinates are in native pixel space (1080px). Guessing from screenshots will miss.
- **Workflow:** `mobile_list_elements_on_screen` -> get coordinates -> `mobile_click_on_screen_at_coordinates` -> `mobile_take_screenshot` to verify.
- **Always call `mobile_list_available_devices` first** — never assume a device ID; the connected device changes frequently.
- **Windows gotcha:** The MCP server command must use a `cmd /c` wrapper — `command: "cmd", args: ["/c", "npx", "@mobilenext/mobile-mcp@latest"]` in `.claude.json`. Plain `npx` is a `.cmd` script and cannot be spawned directly on Windows.
- MCP servers connect at session startup — config changes require a session restart to take effect.

## Gradle Config Notes

- Daemon JVM heap: 2 GB (`-Xmx2048m` in `gradle.properties`).
- `kotlin.code.style=official` is set in `gradle.properties`.
