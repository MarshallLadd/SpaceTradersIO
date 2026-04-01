This is a Kotlin Multiplatform project targeting Android, iOS.

* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./composeApp/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./composeApp/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./composeApp/src/jvmMain/kotlin)
    folder is the appropriate location.

* [/iosApp](./iosApp/iosApp) contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  ```

### Build and Run iOS Application

To build and run the development version of the iOS app, use the run configuration from the run widget
in your IDE’s toolbar or open the [/iosApp](./iosApp) directory in Xcode and run it from there.

---

## Dependencies

### Core / Shared (commonMain)

| Library | Version | Purpose |
|---|---|---|
| [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) | 2.3.0 | Shared code across Android and iOS |
| [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) | 1.10.0 | Shared UI framework |
| [Ktor Client](https://ktor.io/docs/client-create-multiplatform-application.html) | 3.1.2 | HTTP client for SpaceTraders API |
| [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) | 1.8.1 | JSON serialization/deserialization |
| [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | 1.10.2 | Async and concurrency |
| [kotlinx.datetime](https://github.com/Kotlin/kotlinx-datetime) | 0.6.2 | Multiplatform date and time |
| [Koin](https://insert-koin.io/) | 4.2.0 | Dependency injection |
| [Koin Compose](https://insert-koin.io/docs/reference/koin-compose/multiplatform/) | 4.2.0 | DI integration for Compose Multiplatform |
| [multiplatform-settings](https://github.com/russhwolf/multiplatform-settings) | 1.3.0 | Key-value persistence (token storage) |
| [Napier](https://github.com/AAkira/Napier) | 2.7.1 | Multiplatform logging |

### Android (androidMain)

| Library | Version | Purpose |
|---|---|---|
| [Ktor OkHttp Engine](https://ktor.io/docs/client-engines.html#okhttp) | 3.1.2 | Android HTTP engine for Ktor |
| [Koin Android](https://insert-koin.io/docs/reference/koin-android/start/) | 4.2.0 | Android lifecycle-aware DI |
| [kotlinx.coroutines Android](https://github.com/Kotlin/kotlinx.coroutines) | 1.10.2 | Android main thread dispatcher |
| [AndroidX Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle) | 2.9.6 | ViewModel and lifecycle runtime |
| [Activity Compose](https://developer.android.com/jetpack/compose/libraries#activity) | 1.12.2 | Compose integration for Android Activity |

### iOS (iosMain)

| Library | Version | Purpose |
|---|---|---|
| [Ktor Darwin Engine](https://ktor.io/docs/client-engines.html#darwin) | 3.1.2 | iOS/macOS HTTP engine for Ktor |

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)