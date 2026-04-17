package com.brokenhuskysledteam.spacetradersio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.brokenhuskysledteam.spacetradersio.navigation.SpaceTradersNavHost
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository
import com.brokenhuskysledteam.spacetradersio.ui.theme.SpaceTradersIOTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The single Activity that hosts the entire application.
 *
 * **Pattern:** Single-Activity Compose app. In this architecture there is exactly one
 * [ComponentActivity]. Every screen is a Composable inside a single [NavHost], so the OS never
 * creates a second Activity for navigation. To apply this in a new project: subclass
 * [ComponentActivity], annotate with [@AndroidEntryPoint][dagger.hilt.android.AndroidEntryPoint],
 * call [setContent] once in [onCreate], and place your [NavHost] inside the Compose tree.
 *
 * **In this project:** [MainActivity] owns the root [Scaffold] and delegates all screen routing
 * to [SpaceTradersNavHost]. It injects [TokenRepository] directly — before any ViewModel exists —
 * so the NavHost can decide the correct start destination (auth vs. dashboard) on cold launch.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * The persisted auth token store, injected at field level.
     *
     * Field injection (rather than constructor injection) is required here because
     * [ComponentActivity] is instantiated by the Android framework, not by Hilt's generated
     * component — Hilt cannot call a custom constructor on an Activity.
     *
     * This repository is passed directly to [SpaceTradersNavHost] so it can synchronously
     * determine the start destination before the Compose tree is composed. Delegating this
     * decision to a ViewModel would introduce an asynchronous gap during which the wrong screen
     * could briefly appear.
     */
    @Inject
    lateinit var tokenRepository: TokenRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the system bars (status bar + navigation bar) for a full-bleed UI.
        enableEdgeToEdge()
        // setContent is the bridge between the Android View system and the Compose world.
        // Everything inside this lambda is Compose; everything outside remains traditional Android.
        setContent {
            SpaceTradersIOTheme {
                // Scaffold provides the standard Material 3 layout slots (top bar, FAB, etc.).
                // Here we only use innerPadding to respect the system bar insets established by
                // enableEdgeToEdge() above.
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SpaceTradersNavHost(
                        tokenRepository = tokenRepository,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
