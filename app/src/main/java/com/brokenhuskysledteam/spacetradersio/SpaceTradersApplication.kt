package com.brokenhuskysledteam.spacetradersio

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SpaceTradersApplication : Application() {

    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var appLifecycleObserver: AppLifecycleObserver

    override fun onCreate() {
        super.onCreate()
        sessionManager.restoreIfAuthenticated()
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
    }
}
