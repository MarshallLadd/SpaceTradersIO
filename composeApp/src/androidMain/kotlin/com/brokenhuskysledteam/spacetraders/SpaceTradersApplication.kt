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

        // Initialize Napier logging. DebugAntilog routes to Android Logcat.
        Napier.base(DebugAntilog())

        // Start Koin with Android context so android-specific bindings
        // (e.g. koin-android ViewModels) can resolve ApplicationContext.
        startKoin {
            androidContext(this@SpaceTradersApplication)
            modules(appModule)
        }
    }
}
