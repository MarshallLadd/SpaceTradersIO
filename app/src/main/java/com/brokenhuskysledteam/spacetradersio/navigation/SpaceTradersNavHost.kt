package com.brokenhuskysledteam.spacetradersio.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository
import com.brokenhuskysledteam.spacetradersio.ui.auth.AuthScreen
import com.brokenhuskysledteam.spacetradersio.ui.dashboard.DashboardScreen

@Composable
fun SpaceTradersNavHost(
    tokenRepository: TokenRepository,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val startDestination: Any = if (tokenRepository.hasToken()) DashboardRoute else AuthRoute

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable<AuthRoute> {
            AuthScreen(
                onNavigateToDashboard = {
                    navController.navigate(DashboardRoute) {
                        popUpTo<AuthRoute> { inclusive = true }
                    }
                }
            )
        }

        composable<DashboardRoute> {
            DashboardScreen(
                onNavigateToAuth = {
                    navController.navigate(AuthRoute) {
                        popUpTo<DashboardRoute> { inclusive = true }
                    }
                }
            )
        }
    }
}
