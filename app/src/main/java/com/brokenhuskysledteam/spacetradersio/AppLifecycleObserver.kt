package com.brokenhuskysledteam.spacetradersio

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManager
import javax.inject.Inject

/**
 * Process-level lifecycle observer that notifies the SDK when the app returns to the foreground.
 *
 * **Pattern:** [DefaultLifecycleObserver] for foreground-resume callbacks. Implement
 * [DefaultLifecycleObserver] (rather than the deprecated [androidx.lifecycle.LifecycleObserver]
 * annotation approach) and override only the lifecycle methods you need. Register an instance
 * against [androidx.lifecycle.ProcessLifecycleOwner] (not an Activity's lifecycle) to receive
 * app-scoped foreground/background transitions. To apply this in a new project: inject the class
 * with `@Inject constructor`, override `onStart`, and register in your [Application.onCreate].
 *
 * **In this project:** [AppLifecycleObserver] bridges the Android lifecycle into the SDK's
 * [SessionManager]. When the user returns to the app after backgrounding it, any in-transit ship
 * timers that expired while the app was paused need to be re-evaluated. [onStart] triggers that
 * re-evaluation by calling [SessionManager.requireSession][SessionManager].onResume().
 *
 * @param sessionManager The SDK session coordinator whose refresh scheduler must be re-evaluated
 *   on foreground resume.
 */
class AppLifecycleObserver @Inject constructor(
    private val sessionManager: SessionManager
) : DefaultLifecycleObserver {

    /**
     * Called when the app process comes to the foreground (i.e. at least one Activity becomes
     * started). When using [androidx.lifecycle.ProcessLifecycleOwner], this fires once per
     * foreground transition rather than once per Activity start.
     *
     * Delegates to [SessionManager].requireSession().onResume() so the SDK's refresh scheduler
     * can re-arm any timers that elapsed while the process was in the background.
     *
     * The [IllegalStateException] is caught — not re-thrown — because [onStart] can fire before
     * the user has authenticated. In that case [SessionManager.requireSession] throws because no
     * session exists yet, and there is nothing to resume. This is a normal, expected state on
     * first launch or after logout.
     */
    override fun onStart(owner: LifecycleOwner) {
        try {
            sessionManager.requireSession().onResume()
        } catch (_: IllegalStateException) {
            // No active session (user not authenticated) — nothing to resume
        }
    }
}
