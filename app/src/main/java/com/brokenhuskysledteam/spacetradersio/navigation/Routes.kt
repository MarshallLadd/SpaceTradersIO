package com.brokenhuskysledteam.spacetradersio.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for use with the Navigation Compose `composable<T> { }` DSL.
 *
 * **Pattern:** Type-safe Navigation Compose routes. Instead of raw string constants (the legacy
 * approach), each destination is a `@Serializable` Kotlin object or data class. Navigation Compose
 * uses kotlinx.serialization under the hood to convert these types into back-stack entry strings,
 * giving compile-time guarantees that every `navigate()` call passes a valid destination with the
 * correct argument types. To apply this in a new project: add the
 * `androidx.navigation:navigation-compose` and `org.jetbrains.kotlin:kotlin-serialization` plugin
 * dependencies, annotate each route type with `@Serializable`, and match the type in
 * `composable<RouteType> { }` blocks inside your `NavHost`.
 *
 * **In this project:** These routes define the complete navigation graph for the SpaceTraders
 * Android app. They are the single source of truth for destination identity — `NavHost` registers
 * them, and ViewModels emit [NavigationTarget] values that map 1-to-1 onto these route types
 * before being handed to `NavController.navigate()`.
 *
 * **Object vs data class routes:**
 * - Use a plain `object` (e.g. [AuthRoute]) when a screen needs no arguments — there is only ever
 *   one logical instance of that destination.
 * - Use a `data class` (e.g. [ShipDetailRoute]) when a screen must be parameterized — the
 *   serialized fields become the back-stack arguments, and Navigation Compose deserializes them
 *   back into the data class when the composable is built.
 */

/**
 * Route for the authentication screen.
 *
 * **In this project:** The app lands here when [TokenRepository.hasToken] returns `false` at
 * startup, or after the user logs out from the dashboard. It is always cleared from the back stack
 * when navigating forward to [DashboardRoute] so the user cannot press Back and return to a
 * stale auth screen.
 */
@Serializable
object AuthRoute

/**
 * Route for the main dashboard screen.
 *
 * **In this project:** The app lands here at startup when an agent token is already stored, or
 * immediately after successful registration/token import on [AuthRoute]. It is cleared from the
 * back stack when navigating to [AuthRoute] (logout) for the same reason — the dashboard is no
 * longer a valid destination once the token is gone.
 */
@Serializable
object DashboardRoute

/**
 * Route for the ship list screen, which displays all ships owned by the agent.
 *
 * **In this project:** Reached from the dashboard. No arguments are required because the list is
 * always scoped to the currently authenticated agent, whose identity is implicit in the stored
 * token.
 */
@Serializable
object ShipListRoute

/**
 * Route for the ship detail screen, parameterized by the unique ship identifier.
 *
 * **Pattern:** Parameterized route as a data class. Adding `val shipSymbol: String` as a
 * constructor property is all that is needed — Navigation Compose serializes it automatically.
 * Retrieve the value inside the `composable<ShipDetailRoute>` block via
 * `backStackEntry.toRoute<ShipDetailRoute>().shipSymbol`, or simply let Hilt inject the
 * `SavedStateHandle` which Navigation Compose populates for you.
 *
 * **In this project:** Reached from [ShipListRoute] when the user taps a ship card. The symbol
 * (e.g. `"HAULER-1"`) is the API identifier used by every subsequent SpaceTraders API call on
 * this screen.
 *
 * @property shipSymbol The SpaceTraders API identifier for the ship to display (e.g. `"HAULER-1"`).
 */
@Serializable
data class ShipDetailRoute(val shipSymbol: String)

/**
 * Route for the system map screen, optionally focused on a specific waypoint and/or ship.
 *
 * **Pattern:** Optional route parameters via nullable properties with defaults. Nullable fields
 * serialize to absent/null in the back-stack entry, letting a single route type serve multiple
 * entry contexts without creating separate route classes.
 *
 * **In this project:** Can be reached from [ShipDetailRoute] with context about the ship's
 * current location. When `focusWaypointSymbol` is non-null the map camera should pan to that
 * waypoint on first composition; `null` means open the map at its default view. When `shipSymbol`
 * is non-null the map can highlight or follow that specific ship.
 *
 * @property systemSymbol The SpaceTraders API identifier for the star system to display
 *   (e.g. `"X1-DF55"`). Required — a map without a system makes no sense.
 * @property focusWaypointSymbol Optional waypoint to pan the camera to on entry
 *   (e.g. `"X1-DF55-A1"`). `null` means use the map's default viewport.
 * @property shipSymbol Optional ship to highlight on the map (e.g. `"HAULER-1"`). `null` means
 *   no ship is pre-selected.
 */
@Serializable
data class SystemMapRoute(
    val systemSymbol: String,
    val focusWaypointSymbol: String? = null,
    val shipSymbol: String? = null
)

@Serializable
data class ShipyardRoute(val systemSymbol: String, val waypointSymbol: String)

/**
 * Route for the market screen, parameterized by the system, marketplace waypoint, and the ship
 * that will perform trades.
 *
 * @property systemSymbol The star system containing the market (e.g. `"X1-DM91"`).
 * @property waypointSymbol The marketplace waypoint (e.g. `"X1-DM91-A1"`).
 * @property shipSymbol The docked ship that buys/sells (e.g. `"HAULER-1"`). Trades require a
 *   ship docked at the market.
 */
@Serializable
data class MarketRoute(
    val systemSymbol: String,
    val waypointSymbol: String,
    val shipSymbol: String
)

@Serializable
object ContractsRoute
