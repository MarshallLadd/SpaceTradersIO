# KMP Module Restructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the single `composeApp` module (which incorrectly combines `kotlin.multiplatform` + `com.android.application`) into a KMP shared library module (`:composeApp`) and a thin Android application module (`:androidApp`), resolving the AGP 9.0.0+ incompatibility warning.

**Architecture:** Keep `:composeApp` as the KMP shared library by swapping its plugin from `com.android.application` to `com.android.library`. Create `:androidApp` as a plain Android module that holds only the Android entry point (`MainActivity`, `SpaceTradersApplication`, `AndroidManifest.xml`, and app icons/strings) and depends on `:composeApp`.

**Tech Stack:** Kotlin 2.3.20, AGP 9.1.0, Compose Multiplatform 1.10.3, Koin 4.2.0

---

## File Map

**Create:**
- `androidApp/build.gradle.kts` — Android application module definition
- `androidApp/src/main/kotlin/com/brokenhuskysledteam/spacetraders/MainActivity.kt` — moved from composeApp/androidMain
- `androidApp/src/main/kotlin/com/brokenhuskysledteam/spacetraders/SpaceTradersApplication.kt` — moved from composeApp/androidMain
- `androidApp/src/main/AndroidManifest.xml` — moved from composeApp/androidMain
- `androidApp/src/main/res/` — all icon/string resources moved from composeApp/androidMain/res/

**Modify:**
- `settings.gradle.kts` — add `include(":androidApp")`
- `gradle/libs.versions.toml` — add `kotlinAndroid` plugin alias
- `composeApp/build.gradle.kts` — swap `androidApplication` → `androidLibrary`, restructure android config block, remove entry-point dependencies
- `composeApp/src/androidMain/AndroidManifest.xml` — replace with minimal library manifest
- `CLAUDE.md` — update build commands

**Delete (after moving):**
- `composeApp/src/androidMain/kotlin/com/brokenhuskysledteam/spacetraders/MainActivity.kt`
- `composeApp/src/androidMain/kotlin/com/brokenhuskysledteam/spacetraders/SpaceTradersApplication.kt`
- `composeApp/src/androidMain/res/` (entire directory)

---

## Task 1: Add kotlinAndroid plugin alias and register androidApp in settings

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Add `kotlinAndroid` plugin to `libs.versions.toml`**

In `gradle/libs.versions.toml`, add to the `[plugins]` section (after `kotlinMultiplatform`):

```toml
kotlinAndroid = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

- [ ] **Step 2: Include `:androidApp` in `settings.gradle.kts`**

Replace the final line of `settings.gradle.kts`:

```kotlin
// Before:
include(":composeApp")

// After:
include(":composeApp")
include(":androidApp")
```

- [ ] **Step 3: Verify Gradle sync is still valid**

```bash
gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL (no `:androidApp` yet but Gradle will warn about missing module, which is fine — we create it next)

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml settings.gradle.kts
git commit -m "chore: add kotlinAndroid plugin alias and register androidApp module"
```

---

## Task 2: Create the androidApp module

**Files:**
- Create: `androidApp/build.gradle.kts`

- [ ] **Step 1: Create `androidApp/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "com.brokenhuskysledteam.spacetraders"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.brokenhuskysledteam.spacetraders"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    implementation(libs.napier)        // SpaceTradersApplication uses Napier directly
    debugImplementation(libs.compose.uiTooling)
}
```

- [ ] **Step 2: Verify Gradle picks up the new module**

```bash
gradlew :androidApp:dependencies
```

Expected: dependency tree prints (even though the module has no sources yet). BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add androidApp/build.gradle.kts
git commit -m "chore: create androidApp module with application plugin"
```

---

## Task 3: Move Android entry point sources and resources to androidApp

**Files:**
- Create: `androidApp/src/main/kotlin/com/brokenhuskysledteam/spacetraders/MainActivity.kt`
- Create: `androidApp/src/main/kotlin/com/brokenhuskysledteam/spacetraders/SpaceTradersApplication.kt`
- Create: `androidApp/src/main/AndroidManifest.xml`
- Create: `androidApp/src/main/res/` — all subdirectories/files from `composeApp/src/androidMain/res/`

- [ ] **Step 1: Create `androidApp/src/main/kotlin/com/brokenhuskysledteam/spacetraders/MainActivity.kt`**

```kotlin
package com.brokenhuskysledteam.spacetraders

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
```

- [ ] **Step 2: Create `androidApp/src/main/kotlin/com/brokenhuskysledteam/spacetraders/SpaceTradersApplication.kt`**

```kotlin
package com.brokenhuskysledteam.spacetraders

import android.app.Application
import com.brokenhuskysledteam.spacetraders.di.appModule
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SpaceTradersApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Napier.base(DebugAntilog())

        startKoin {
            androidContext(this@SpaceTradersApplication)
            modules(appModule)
        }
    }
}
```

- [ ] **Step 3: Create `androidApp/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".SpaceTradersApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:exported="true"
            android:name=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 4: Copy all resource files from `composeApp/src/androidMain/res/` to `androidApp/src/main/res/`**

Run from the KMP root:

```bash
cp -r composeApp/src/androidMain/res/. androidApp/src/main/res/
```

The following files should now exist in `androidApp/src/main/res/`:
- `drawable/ic_launcher_background.xml`
- `drawable-v24/ic_launcher_foreground.xml`
- `mipmap-anydpi-v26/ic_launcher.xml`
- `mipmap-anydpi-v26/ic_launcher_round.xml`
- `mipmap-hdpi/ic_launcher.png` and `ic_launcher_round.png`
- `mipmap-mdpi/ic_launcher.png` and `ic_launcher_round.png`
- `mipmap-xhdpi/ic_launcher.png` and `ic_launcher_round.png`
- `mipmap-xxhdpi/ic_launcher.png` and `ic_launcher_round.png`
- `mipmap-xxxhdpi/ic_launcher.png` and `ic_launcher_round.png`
- `values/strings.xml`

- [ ] **Step 5: Commit (before deleting originals)**

```bash
git add androidApp/
git commit -m "chore: add androidApp entry point sources and resources"
```

---

## Task 4: Update composeApp to a KMP library module

**Files:**
- Modify: `composeApp/build.gradle.kts`
- Modify: `composeApp/src/androidMain/AndroidManifest.xml`

- [ ] **Step 1: Replace `composeApp/build.gradle.kts` entirely**

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SpaceTradersSDK"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.napier)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

configure<LibraryExtension> {
    namespace = "com.brokenhuskysledteam.spacetraders"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
```

Key changes from the original:
- Removed `import org.jetbrains.compose.desktop.application.dsl.TargetFormat` (desktop-only import)
- Swapped `import com.android.build.api.dsl.ApplicationExtension` → `LibraryExtension`
- Swapped `alias(libs.plugins.androidApplication)` → `alias(libs.plugins.androidLibrary)`
- Removed `koin.android`, `androidx.activity.compose` from `androidMain.dependencies` (moved to `:androidApp`)
- Changed `configure<ApplicationExtension>` → `configure<LibraryExtension>`
- Removed `applicationId`, `versionCode`, `versionName`, `packaging` block (library doesn't own these)
- Removed `dependencies { debugImplementation(libs.compose.uiTooling) }` (moved to `:androidApp`)

- [ ] **Step 2: Replace `composeApp/src/androidMain/AndroidManifest.xml` with minimal library manifest**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

- [ ] **Step 3: Verify shared module compiles**

```bash
gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL with no errors. You may see the deprecation warning is now gone (or at minimum reduced — the library+multiplatform combination is valid).

- [ ] **Step 4: Commit**

```bash
git add composeApp/build.gradle.kts composeApp/src/androidMain/AndroidManifest.xml
git commit -m "chore: convert composeApp to KMP library module"
```

---

## Task 5: Delete original entry point files from composeApp

**Files:**
- Delete: `composeApp/src/androidMain/kotlin/com/brokenhuskysledteam/spacetraders/MainActivity.kt`
- Delete: `composeApp/src/androidMain/kotlin/com/brokenhuskysledteam/spacetraders/SpaceTradersApplication.kt`
- Delete: `composeApp/src/androidMain/res/` (entire directory)

- [ ] **Step 1: Delete the moved files**

```bash
rm composeApp/src/androidMain/kotlin/com/brokenhuskysledteam/spacetraders/MainActivity.kt
rm composeApp/src/androidMain/kotlin/com/brokenhuskysledteam/spacetraders/SpaceTradersApplication.kt
rm -rf composeApp/src/androidMain/res/
```

- [ ] **Step 2: Verify shared module still compiles after deletion**

```bash
gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL. `Platform.android.kt` remains and compiles fine.

- [ ] **Step 3: Build the full APK via androidApp**

```bash
gradlew :androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL. The APK is produced at `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore: remove entry point files from composeApp after moving to androidApp"
```

---

## Task 6: Run tests and update CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Run all tests to confirm nothing broke**

```bash
gradlew :composeApp:testDebugUnitTest
```

Expected: All existing tests pass. BUILD SUCCESSFUL.

- [ ] **Step 2: Update build commands in `CLAUDE.md`**

Find the Build Commands section and update:

```markdown
# Before:
./gradlew :composeApp:assembleDebug

# After:
./gradlew :androidApp:assembleDebug
```

The following commands are unchanged (tests and compilation check still target `:composeApp`):
```bash
./gradlew :composeApp:testDebugUnitTest
./gradlew :composeApp:compileDebugKotlinAndroid
```

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md
git commit -m "docs(claude): update build command to androidApp:assembleDebug after module restructure"
```

---

## Task 7: End-to-end verification on emulator

- [ ] **Step 1: Install and launch on Android emulator**

```bash
gradlew :androidApp:installDebug
```

Expected: App installs to the connected emulator. BUILD SUCCESSFUL.

- [ ] **Step 2: Verify app launches**

Use `mobile_list_available_devices` → `mobile_launch_app` (package: `com.brokenhuskysledteam.spacetraders`) → `mobile_take_screenshot` to confirm the app opens and renders its current placeholder UI.

- [ ] **Step 3: Open a PR**

```bash
gh pr create \
  --base develop \
  --title "chore: restructure KMP modules to fix AGP 9.0+ incompatibility" \
  --body "$(cat <<'EOF'
## What this does

Splits the single \`composeApp\` module (which combined \`kotlin.multiplatform\` + \`com.android.application\`, incompatible with AGP 9.0+) into two modules:

- \`:composeApp\` — KMP shared library (\`kotlin.multiplatform\` + \`com.android.library\`)
- \`:androidApp\` — thin Android entry point (\`com.android.application\` only), depends on \`:composeApp\`

Resolves the deprecation warning introduced in AGP 9.0.0.

## How to test

1. \`gradlew :composeApp:compileDebugKotlinAndroid\` — shared module compiles
2. \`gradlew :composeApp:testDebugUnitTest\` — all tests pass
3. \`gradlew :androidApp:assembleDebug\` — APK builds successfully
4. Install on emulator — app launches and renders correctly

## Notes

- All \`commonMain\` code is untouched; only entry point files moved
- Build command for APK is now \`:androidApp:assembleDebug\`
- Test and compile-check commands still target \`:composeApp\`
EOF
)"
```
