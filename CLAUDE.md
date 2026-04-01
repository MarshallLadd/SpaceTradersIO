# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SpaceTraders is a Kotlin Multiplatform (KMP) client for the [SpaceTraders IO](https://spacetraders.io) REST API MMO. The full OpenAPI spec lives at `../OpenAPISpec/space_trader_open_api_spec.json` relative to this directory and is the authoritative reference for all API endpoints, request/response shapes, and auth schemes.

**Targets:** Android (minSdk 28) and iOS (arm64 + simulator). Desktop/web are out of scope for now.

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
./gradlew :composeApp:compileKotlinAndroid
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

Ktor and `kotlinx-serialization` are **not yet added** — they are the next dependency milestone. When adding them, declare versions in `gradle/libs.versions.toml` and use engine `okhttp` for Android and `darwin` for iOS.

## SpaceTraders API

- Base URL: `https://api.spacetraders.io/v2`
- Auth: Bearer token (JWT). Two token types: `AgentToken` (per-agent gameplay) and `AccountToken` (account management). Agent registration returns the bearer token directly.
- The OpenAPI spec (`../OpenAPISpec/space_trader_open_api_spec.json`) is the ground truth — always check it before implementing a new endpoint.
- Pagination is used extensively: requests accept `page` and `limit`, responses include a `meta` object with `total`, `page`, `limit`.

## Gradle Config Notes

- Configuration cache and build cache are both enabled (`gradle.properties`).
- Daemon JVM heap: 3 GB; build JVM heap: 4 GB.
- `TYPESAFE_PROJECT_ACCESSORS` feature preview is enabled in `settings.gradle.kts`.
