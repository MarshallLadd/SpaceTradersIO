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

// Single-activity host for the entire app. Hilt injects the TokenRepository
// so the NavHost can check auth state before any ViewModel is created.
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Field-injected because ComponentActivity doesn't support constructor injection.
    // Used only to determine the initial navigation destination (auth vs dashboard).
    @Inject
    lateinit var tokenRepository: TokenRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpaceTradersIOTheme {
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
