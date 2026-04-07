package com.brokenhuskysledteam.spacetradersio

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManager
import javax.inject.Inject

class AppLifecycleObserver @Inject constructor(
    private val sessionManager: SessionManager
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        try {
            sessionManager.requireSession().onResume()
        } catch (_: IllegalStateException) {
            // No active session (user not authenticated) — nothing to resume
        }
    }
}
