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
import com.brokenhuskysledteam.spacetradersio.ui.shipyard.ShipyardScreen
import com.brokenhuskysledteam.spacetradersio.ui.systemmap.SystemMapScreen

/**
 * Top-level navigation graph for the SpaceTraders app.
 *
 * **Pattern:** Single-activity Compose navigation with an auth gate. This composable owns the
 * [NavController] and the [NavHost] that wires all destinations together. It is the only place
 * where `NavController.navigate()` is called — screens receive plain lambda callbacks and remain
 * unaware of the navigation library. To apply this in a new project:
 * 1. Create a sealed route hierarchy (see [Routes.kt]).
 * 2. Create this composable, decide a `startDestination` based on stored auth state, and register
 *    all `composable<Route> { }` blocks.
 * 3. Pass navigation lambdas down to each screen — keep `NavController` out of ViewModels.
 *
 * **Auth gate:** [TokenRepository.hasToken] is read synchronously at composition time (not inside
 * a `LaunchedEffect`) so the correct start destination is committed before the first frame is
 * drawn, avoiding a visible flash of the wrong screen.
 *
 * **Stateful/stateless split:** Each `composable<Route> { }` block instantiates the matching
 * *stateful* `Screen` composable (e.g. [AuthScreen], [DashboardScreen]). Stateful screens own
 * their ViewModel via `hiltViewModel()` and manage all UI state internally. [SpaceTradersNavHost]
 * itself is purely stateless — it only mediates route registration and navigation callbacks. This
 * keeps the NavHost recomposable-safe and easy to test in isolation.
 *
 * **In this project:** Placed directly inside `MainActivity`'s `setContent { }` block, making it
 * the single root of the Compose UI tree.
 *
 * @param tokenRepository Used once at composition time to determine whether to start at
 *   [AuthRoute] (no token) or [DashboardRoute] (token already stored). Injected via Hilt.
 * @param modifier Optional modifier forwarded to [NavHost], e.g. to apply padding from a
 *   `Scaffold`.
 */
@Composable
fun SpaceTradersNavHost(
    tokenRepository: TokenRepository,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    // Determine the first screen to show based on whether an agent token is already stored.
    // Reading this synchronously (not in a LaunchedEffect) ensures the correct destination is
    // set before the first draw pass — no blank screen or unwanted auth flash.
    val startDestination: Any = if (tokenRepository.hasToken()) DashboardRoute else AuthRoute

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // --- Auth screen ---
        // Entry point for new users and for returning users whose token has been cleared.
        composable<AuthRoute> {
            AuthScreen(
                onNavigateToDashboard = {
                    navController.navigate(DashboardRoute) {
                        // Remove AuthRoute from the back stack so pressing Back from the dashboard
                        // exits the app rather than returning to the auth screen. Using
                        // `inclusive = true` removes AuthRoute itself, not just entries above it.
                        popUpTo<AuthRoute> { inclusive = true }
                    }
                }
            )
        }

        // --- Dashboard screen ---
        // Home screen for authenticated users; hub for all major navigation actions.
        composable<DashboardRoute> {
            DashboardScreen(
                onNavigateToAuth = {
                    navController.navigate(AuthRoute) {
                        // Remove DashboardRoute (inclusive) on logout so the now-unauthenticated
                        // dashboard is not reachable via Back after the user is sent to Auth.
                        popUpTo<DashboardRoute> { inclusive = true }
                    }
                },
                onNavigateToShipList = {
                    // Standard forward navigation — DashboardRoute stays on the back stack so
                    // Back from the ship list returns here as expected.
                    navController.navigate(ShipListRoute)
                }
            )
        }

        // --- Ship list screen ---
        // Shows all ships owned by the currently authenticated agent.
        composable<ShipListRoute> {
            ShipListScreen(
                onNavigateToShipDetail = { shipSymbol ->
                    // Wrap the raw string in a typed route so Navigation Compose serializes it
                    // correctly; the detail screen retrieves it from SavedStateHandle / toRoute().
                    navController.navigate(ShipDetailRoute(shipSymbol))
                }
            )
        }

        // --- Ship detail screen ---
        // Shows operational controls and status for a single ship identified by its symbol.
        // Navigation Compose deserializes the ShipDetailRoute arguments from the back-stack entry
        // and makes them available to the ViewModel via SavedStateHandle automatically.
        composable<ShipDetailRoute> {
            ShipDetailScreen(
                onNavigateToSystemMap = { systemSymbol, waypointSymbol, shipSymbol ->
                    navController.navigate(
                        SystemMapRoute(systemSymbol, waypointSymbol, shipSymbol)
                    )
                },
                onNavigateToShipyard = { systemSymbol, waypointSymbol ->
                    navController.navigate(ShipyardRoute(systemSymbol, waypointSymbol))
                }
            )
        }

        // --- Shipyard screen ---
        // Lists ships available for purchase at a specific waypoint.
        // Navigation Compose deserializes systemSymbol and waypointSymbol from the back-stack entry.
        composable<ShipyardRoute> {
            ShipyardScreen(onNavigateBack = { navController.popBackStack() })
        }

        // --- System map screen ---
        // Renders a 2-D map of a star system, optionally pre-focused on a waypoint or ship.
        // Navigation Compose deserializes optional nullable fields from the back-stack entry, so
        // null defaults declared on SystemMapRoute are honoured when the caller omits them.
        composable<SystemMapRoute> {
            SystemMapScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
