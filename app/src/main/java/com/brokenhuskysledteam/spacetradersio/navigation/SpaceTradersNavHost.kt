package com.brokenhuskysledteam.spacetradersio.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository
import com.brokenhuskysledteam.spacetradersio.ui.auth.AuthScreen
import com.brokenhuskysledteam.spacetradersio.ui.dashboard.DashboardScreen
import com.brokenhuskysledteam.spacetradersio.ui.ships.ShipDetailScreen
import com.brokenhuskysledteam.spacetradersio.ui.ships.ShipListScreen
import com.brokenhuskysledteam.spacetradersio.ui.systemmap.SystemMapScreen

// Top-level navigation graph for the app. Checks TokenRepository at composition
// time to decide whether to start on the auth screen or the dashboard.
// Each transition clears the back stack (popUpTo inclusive) so the user can't
// navigate back to a screen that's no longer valid (e.g. auth after login).
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
                },
                onNavigateToShipList = {
                    navController.navigate(ShipListRoute)
                }
            )
        }

        composable<ShipListRoute> {
            ShipListScreen(
                onNavigateToShipDetail = { shipSymbol ->
                    navController.navigate(ShipDetailRoute(shipSymbol))
                }
            )
        }

        composable<ShipDetailRoute> {
            ShipDetailScreen(
                onNavigateToSystemMap = { systemSymbol, waypointSymbol, shipSymbol ->
                    navController.navigate(
                        SystemMapRoute(systemSymbol, waypointSymbol, shipSymbol)
                    )
                }
            )
        }

        composable<SystemMapRoute> {
            SystemMapScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
