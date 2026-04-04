## Dependencies

### Core / Shared (commonMain)

| Library                                                                           | Version | Purpose                                  |
|-----------------------------------------------------------------------------------|---------|------------------------------------------|
| [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)            | 2.3.20  | Shared code across Android and iOS       |
| [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)         | 1.10.3  | Shared UI framework                      |
| [Ktor Client](https://ktor.io/docs/client-create-multiplatform-application.html)  | 3.4.2   | HTTP client for SpaceTraders API         |
| [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)          | 1.10.0  | JSON serialization/deserialization       |
| [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines)                | 1.10.2  | Async and concurrency                    |
| [kotlinx.datetime](https://github.com/Kotlin/kotlinx-datetime)                    | 0.7.1   | Multiplatform date and time              |
| [Koin](https://insert-koin.io/)                                                   | 4.2.0   | Dependency injection                     |
| [Koin Compose](https://insert-koin.io/docs/reference/koin-compose/multiplatform/) | 4.2.0   | DI integration for Compose Multiplatform |
| [multiplatform-settings](https://github.com/russhwolf/multiplatform-settings)     | 1.3.0   | Key-value persistence (token storage)    |
| [Napier](https://github.com/AAkira/Napier)                                        | 2.7.1   | Multiplatform logging                    |

### Android (androidMain)

| Library                                                                                 | Version | Purpose                                  |
|-----------------------------------------------------------------------------------------|---------|------------------------------------------|
| [Ktor OkHttp Engine](https://ktor.io/docs/client-engines.html#okhttp)                   | 3.4.2   | Android HTTP engine for Ktor             |
| [Koin Android](https://insert-koin.io/docs/reference/koin-android/start/)               | 4.2.0   | Android lifecycle-aware DI               |
| [kotlinx.coroutines Android](https://github.com/Kotlin/kotlinx.coroutines)              | 1.10.2  | Android main thread dispatcher           |
| [AndroidX Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle) | 2.10.0  | ViewModel and lifecycle runtime          |
| [Activity Compose](https://developer.android.com/jetpack/compose/libraries#activity)    | 1.13.0  | Compose integration for Android Activity |

### iOS (iosMain)

| Library                                                               | Version | Purpose                        |
|-----------------------------------------------------------------------|---------|--------------------------------|
| [Ktor Darwin Engine](https://ktor.io/docs/client-engines.html#darwin) | 3.4.2   | iOS/macOS HTTP engine for Ktor |

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)