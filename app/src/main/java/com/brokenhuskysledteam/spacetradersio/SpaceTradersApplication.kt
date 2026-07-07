package com.brokenhuskysledteam.spacetradersio

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.brokenhuskysledteam.loggerkit.LoggerKit
import com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application subclass that bootstraps the Hilt dependency injection graph and the SDK session.
 *
 * **Pattern:** [@HiltAndroidApp][HiltAndroidApp] Application subclass. Hilt requires exactly one
 * class annotated with `@HiltAndroidApp` in the app module. The annotation triggers code
 * generation that creates the root Hilt component, which lives for the lifetime of the process.
 * To apply this in a new project: create an [Application] subclass, add `@HiltAndroidApp`, and
 * register it as `android:name` in `AndroidManifest.xml`.
 *
 * **In this project:** [SpaceTradersApplication] performs two critical startup tasks before any
 * Activity or ViewModel runs: it restores the SDK session from persistent storage, and it
 * registers [AppLifecycleObserver] to handle foreground-resume events for the entire process.
 */
@HiltAndroidApp
class SpaceTradersApplication : Application() {

    /**
     * The SDK's session coordinator, responsible for holding the authenticated client and
     * managing the background refresh scheduler.
     */
    @Inject lateinit var sessionManager: SessionManager

    /**
     * The process-level lifecycle observer that forwards foreground-resume events to the SDK.
     * Injected here so Hilt can satisfy its own dependencies (e.g. [SessionManager]).
     */
    @Inject lateinit var appLifecycleObserver: AppLifecycleObserver

    override fun onCreate() {
        super.onCreate()

        // Restore a previously authenticated session from persistent storage before any
        // ViewModel is created. If skipped, the first API call after a process restart would
        // fail silently because the SDK would have no token loaded, even though the user had
        // already logged in during a prior session.
        sessionManager.restoreIfAuthenticated()

        // Attach the lifecycle observer to ProcessLifecycleOwner rather than any individual
        // Activity's lifecycle. ProcessLifecycleOwner fires onStart when the app comes to the
        // foreground from any Activity and onStop when the last visible Activity is stopped —
        // exactly the coarse app-level signal we need for refresh-timer management.
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)

        LoggerKit.configure(isDebug = true)
    }
}
