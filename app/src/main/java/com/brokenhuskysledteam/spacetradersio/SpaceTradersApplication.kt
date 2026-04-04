package com.brokenhuskysledteam.spacetradersio

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

// Required by Hilt — triggers code generation for the dependency graph.
// Must be declared in AndroidManifest.xml as android:name=".SpaceTradersApplication".
@HiltAndroidApp
class SpaceTradersApplication : Application()
