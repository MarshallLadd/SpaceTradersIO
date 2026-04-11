# SpaceTraders IO — KMP SDK & Android Client

![Android](https://img.shields.io/badge/Android-API%2028%2B-brightgreen?logo=android)
![iOS](https://img.shields.io/badge/iOS-xcframework-lightgrey?logo=apple)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?logo=kotlin)
![Ktor](https://img.shields.io/badge/Ktor-3.4.2-087CFA)
![License](https://img.shields.io/badge/License-MIT-blue)

A **Kotlin Multiplatform** project for the [SpaceTraders IO](https://spacetraders.io) REST API — an open-source, MMO-style space trading game with a fully documented API.

The project is split into two modules:

- **`:spacetradersiosdk`** — a KMP SDK targeting Android and iOS. All business logic, networking, and domain models live here as shared `commonMain` code, with an iOS xcframework output for native Swift consumption.
- **`:app`** — a standalone Android client built with Jetpack Compose that consumes the SDK.

This repository also serves as a **personal reference architecture** for building modern, scalable KMP/Android apps. See [`docs/ARCHITECTURE_REFERENCE_HUMAN.md`](docs/ARCHITECTURE_REFERENCE_HUMAN.md) for the full playbook.

---

## Screenshots

> _Screenshots coming soon — build and run on an emulator to see the terminal aesthetic in action._

| Auth Screen | Ship List | Ship Detail |
|---|---|---|
| _(placeholder)_ | _(placeholder)_ | _(placeholder)_ |

---

## Architecture

The project enforces a strict boundary between the two modules:

| Module | Owns | Must NOT contain |
|---|---|---|
| `:spacetradersiosdk` | Ktor client, API layer, DTOs, domain models, repositories, use cases, state stores | Any Android/Compose import |
| `:app` | Jetpack Compose screens, ViewModels, Hilt DI, navigation graph, theme | Direct Ktor calls, DTO types, business logic |

The UI layer uses a **Unidirectional Data Flow (UDF)** pattern: `StateFlow<UiState>` flows down to screens, `sealed interface Event` flows up from user interactions, and `Channel<NavigationTarget>` handles one-shot navigation signals.

**Full architecture reference:** [`docs/ARCHITECTURE_REFERENCE_HUMAN.md`](docs/ARCHITECTURE_REFERENCE_HUMAN.md)
**AI-optimized reference (for context injection):** [`docs/ARCHITECTURE_REFERENCE_AI.md`](docs/ARCHITECTURE_REFERENCE_AI.md)

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Android Studio | Meerkat (2024.3) or later | Required for AGP 9.1.0 |
| JDK | 17 or later | Bundled with recent Android Studio builds |
| Android SDK | API 28+ | minSdk 28 (Android 9 Pie) |
| Xcode | 15+ | **macOS only** — required for iOS xcframework build only |

> The iOS xcframework build requires a macOS machine. Android development works on Windows, macOS, and Linux.

---

## Getting Started

```bash
# 1. Clone the repository
git clone git@github.com:MarshallLadd/SpaceTradersIO.git
cd SpaceTradersIO

# 2. Open the project in Android Studio
# File → Open → select the KMP/ directory

# 3. Let Gradle sync complete, then run the app
# Run → Run 'app' (or use the toolbar ▶ button)
```

You'll need a SpaceTraders account token to use the app. Register a new agent directly from the app's auth screen, or import an existing bearer token.

Get a token at: [https://spacetraders.io](https://spacetraders.io)

---

## Build Commands

All commands run from the `KMP/` directory. On Windows use `gradlew.bat` instead of `./gradlew`.

```bash
# Build Android debug APK
./gradlew :app:assembleDebug

# Compile SDK — catches type errors before running tests
./gradlew :spacetradersiosdk:compileAndroidHostTestSources

# Build iOS xcframework (macOS only)
./gradlew :spacetradersiosdk:assembleSpacetradersiosdkKitReleaseXCFramework
```

---

## Running Tests

```bash
# Run all SDK tests (androidHostTest + commonTest compiled for JVM)
./gradlew :spacetradersiosdk:allTests

# Run all app ViewModel tests
./gradlew :app:testDebugUnitTest

# Run a single SDK test class
./gradlew :spacetradersiosdk:testAndroidHostTest \
  --tests "com.brokenhuskysledteam.spacetradersio.sdk.MyTest"

# Run a single app test class
./gradlew :app:testDebugUnitTest \
  --tests "com.brokenhuskysledteam.spacetradersio.ui.ships.ShipDetailViewModelTest"
```

**Coverage target:** 100% class, method, line, and branch coverage for all new code.

Tests use [Ktor MockEngine](https://ktor.io/docs/client-testing.html) for HTTP mocking, hand-written fakes over mocking frameworks, and [Turbine](https://github.com/cashapp/turbine) for `Flow`/`Channel` assertions.

---

## Project Structure

```
KMP/
├── app/                            # Android application module
│   └── src/main/java/.../
│       ├── di/                     # Hilt module — bridges SDK into Android DI graph
│       ├── navigation/             # @Serializable routes + NavHost
│       └── ui/
│           ├── auth/               # Registration + token import screen
│           ├── dashboard/          # Agent info + fleet overview
│           ├── ships/              # Ship list + detail screens
│           ├── components/         # Reusable terminal-styled composables
│           └── theme/              # Dark retro-terminal theme (green-on-black)
│
├── spacetradersiosdk/              # KMP SDK module (commonMain / androidMain / iosMain)
│   └── src/commonMain/kotlin/.../sdk/
│       ├── api/                    # Ktor client, endpoint interfaces + impls, DTOs
│       │   └── mapper/             # DTO → domain model extension functions
│       ├── domain/                 # Domain models, repository interfaces, use cases
│       │   ├── model/              # Clean data classes — no serialization annotations
│       │   ├── repository/         # Repository interfaces
│       │   ├── state/              # EntityStateStore — reactive StateFlow-backed stores
│       │   ├── session/            # SessionManager + SpaceTradersSession lifecycle
│       │   ├── scheduler/          # RefreshScheduler — centralized timed refresh engine
│       │   └── usecase/            # Business orchestration (interface + operator invoke)
│       └── data/
│           └── repository/         # Repository implementations (write-through to stores)
│
├── docs/
│   ├── ARCHITECTURE_REFERENCE_HUMAN.md   # Full narrative architecture playbook
│   ├── ARCHITECTURE_REFERENCE_AI.md      # Compressed AI-context version
│   └── superpowers/
│       ├── specs/                  # Design decision records
│       └── plans/                  # Implementation plans
│
├── gradle/
│   └── libs.versions.toml          # Version catalog — single source of truth for all deps
├── CLAUDE.md                       # AI coding assistant instructions for this repo
└── README.md
```

---

## Key Dependencies

### Core / Shared (`commonMain`)

| Library | Version | Purpose |
|---|---|---|
| [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) | 2.3.20 | Shared code across Android and iOS |
| [Ktor Client](https://ktor.io/docs/client-create-multiplatform-application.html) | 3.4.2 | HTTP client for SpaceTraders API |
| [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) | 1.10.0 | JSON serialization/deserialization |
| [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | 1.10.2 | Async and concurrency |
| [multiplatform-settings](https://github.com/russhwolf/multiplatform-settings) | 1.3.0 | Key-value persistence (token storage) |
| [Napier](https://github.com/AAkira/Napier) | 2.7.1 | Multiplatform logging |

### Android (`:app` module)

| Library | Version | Purpose |
|---|---|---|
| [Android Gradle Plugin](https://developer.android.com/build) | 9.1.0 | Android build tooling |
| [Jetpack Compose BOM](https://developer.android.com/jetpack/compose/bom) | 2026.03.01 | Compose UI + Material3 (version-managed) |
| [Navigation Compose](https://developer.android.com/jetpack/compose/navigation) | 2.9.0 | Type-safe navigation with `@Serializable` routes |
| [Hilt](https://dagger.dev/hilt/) | 2.59.2 | Dependency injection |
| [Hilt Navigation Compose](https://developer.android.com/jetpack/compose/libraries#hilt) | 1.2.0 | `hiltViewModel()` integration |
| [AndroidX Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle) | 2.10.0 | ViewModel + `collectAsStateWithLifecycle` |
| [Activity Compose](https://developer.android.com/jetpack/compose/libraries#activity) | 1.13.0 | `ComponentActivity.setContent {}` |
| [Ktor OkHttp Engine](https://ktor.io/docs/client-engines.html#okhttp) | 3.4.2 | Android HTTP engine for Ktor |

### iOS (`iosMain`)

| Library | Version | Purpose |
|---|---|---|
| [Ktor Darwin Engine](https://ktor.io/docs/client-engines.html#darwin) | 3.4.2 | iOS/macOS HTTP engine for Ktor |

### Testing

| Library | Version | Purpose |
|---|---|---|
| [Ktor Client Mock](https://ktor.io/docs/client-testing.html) | 3.4.2 | MockEngine for SDK HTTP tests |
| [kotlinx-coroutines-test](https://github.com/Kotlin/kotlinx.coroutines) | 1.10.2 | `runTest`, `StandardTestDispatcher` |
| [Turbine](https://github.com/cashapp/turbine) | 1.2.0 | `Flow`/`Channel` test assertions |
| kotlin-test | 2.3.20 | Assertions (`assertEquals`, `assertIs`, etc.) |

---

## Contributing

This project follows **Git Flow**. Work happens on short-lived branches that merge into `develop`. `develop` merges into `main` only for releases.

```
main        ← production / releases only
  └── develop ← integration branch; all PRs target here
        ├── feature/my-feature
        ├── bugfix/fix-something
        └── docs/update-readme
```

```bash
# Start new work
git checkout develop && git pull
git checkout -b feature/your-feature-name

# Open a PR targeting develop
git push -u origin feature/your-feature-name
gh pr create --base develop
```

**Branch types:** `feature/`, `bugfix/`, `docs/`, `chore/`, `refactor/`, `hotfix/`

**Commit format:** [Conventional Commits](https://www.conventionalcommits.org/) — `feat: add X`, `fix: resolve Y`, `docs: update Z`

---

## License

MIT License — see [LICENSE](LICENSE) for details.

---

*Built with [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) · [SpaceTraders API](https://spacetraders.io)*
