import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.android.lint)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}


kotlin {

    // Target declarations - add or remove as needed below. These define
    // which platforms this KMP module supports.
    // See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets
    android {
        namespace = "com.brokenhuskysledteam.spacetradersio.sdk"
        compileSdk {
            version =
                release(36) {
                    minorApiLevel = 1
                }
        }
        minSdk = 28

        withHostTestBuilder {
        }

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    // For iOS targets, this is also where you should
    // configure native binary output. For more information, see:
    // https://kotlinlang.org/docs/multiplatform-build-native-binaries.html#build-xcframeworks

    // A step-by-step guide on how to include this library in an XCode
    // project can be found here:
    // https://developer.android.com/kotlin/multiplatform/migrate
    val xcfName = "spacetradersiosdkKit"

    iosX64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosSimulatorArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    // Source set declarations.
    // Declaring a target automatically creates a source set with the same name. By default, the
    // Kotlin Gradle Plugin creates additional source sets that depend on each other, since it is
    // common to share sources between related targets.
    // See: https://kotlinlang.org/docs/multiplatform-hierarchy.html
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                // Add KMP dependencies here

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.napier)
                implementation(libs.multiplatform.settings)
                implementation(libs.multiplatform.settings.no.arg)
                implementation(libs.sqldelight.coroutines)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.ktor.client.mock)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.sqldelight.jvm.driver)
                implementation(libs.turbine)
            }
        }

        androidMain {
            dependencies {
                // Add Android-specific dependencies here. Note that this source set depends on
                // commonMain by default and will correctly pull the Android artifacts of any KMP
                // dependencies declared in commonMain.
                implementation(libs.ktor.client.okhttp)
                implementation(libs.sqldelight.android.driver)
            }
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.runner)
                implementation(libs.androidx.core)
                implementation(libs.androidx.junit)
            }
        }

        iosMain {
            dependencies {

                // Add iOS-specific dependencies here. This a source set created by Kotlin Gradle
                // Plugin (KGP) that each specific iOS target (e.g., iosX64) depends on as
                // part of KMP’s default source set hierarchy. Note that this source set depends
                // on common by default and will correctly pull the iOS artifacts of any
                // KMP dependencies declared in commonMain.
                implementation(libs.ktor.client.darwin)
                implementation(libs.sqldelight.native.driver)
            }
        }
    }
}

sqldelight {
    databases {
        create("SpaceTradersDatabase") {
            packageName.set("com.brokenhuskysledteam.spacetradersio.sdk.data.db")
            dialect(libs.sqldelight.dialect)
        }
    }
}

// ── Live end-to-end tests (opt-in) ───────────────────────────────────────────
// Tests named *LiveTest hit the real SpaceTraders API. They self-skip unless
// enabled here, so normal builds and CI never touch the network. Enable with:
//
//   ./gradlew :spacetradersiosdk:testAndroidHostTest -Pe2e --tests "*LiveTest"
//
// The account token is read from secrets.properties at the repo root (gitignored)
// or the SPACETRADERS_ACCOUNT_TOKEN environment variable, and forwarded to the test
// JVM as a system property. -Pe2e is used (not an env var) because Gradle does not
// reliably forward shell environment variables to the test worker JVM.
tasks.withType<Test>().configureEach {
    val e2eEnabled = project.hasProperty("e2e") || System.getenv("SPACETRADERS_E2E") == "1"
    if (e2eEnabled) {
        systemProperty("spacetraders.e2e.enabled", "true")
        val secrets = rootProject.file("secrets.properties")
        val fileToken = if (secrets.exists()) {
            Properties()
                .apply { secrets.inputStream().use { load(it) } }
                .getProperty("spacetraders.accountToken")
        } else null
        val token = System.getenv("SPACETRADERS_ACCOUNT_TOKEN") ?: fileToken
        if (token != null) systemProperty("spacetraders.e2e.accountToken", token)
    }
}
