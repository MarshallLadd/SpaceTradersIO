package com.brokenhuskysledteam.spacetradersio.navigation

/**
 * One-shot navigation signals that ViewModels emit to trigger screen transitions.
 *
 * **Pattern:** One-shot ViewModel navigation via `Channel`. ViewModels expose a
 * `Channel<NavigationTarget>` (as `receiveAsFlow()` at the call site). Screen composables collect
 * it inside a `LaunchedEffect` and translate each value into a `NavController.navigate()` call.
 *
 * The critical design choice here is **`Channel` over `SharedFlow`**:
 * - `SharedFlow` (with `replay = 0`) still re-delivers the most recent event to any new collector.
 *   On a configuration change (rotation), the composable is recomposed and a new `LaunchedEffect`
 *   starts collecting — causing the navigation event to fire a second time, pushing a duplicate
 *   destination onto the back stack.
 * - `Channel` is a one-shot queue: each value is consumed exactly once by exactly one collector,
 *   and unconsumed values are not replayed. A new collector after a config change sees nothing.
 *
 * To apply this pattern in a new project:
 * 1. Declare `private val _navigation = Channel<NavigationTarget>(Channel.BUFFERED)` in the ViewModel.
 * 2. Expose `val navigationEvents = _navigation.receiveAsFlow()`.
 * 3. In the composable, `LaunchedEffect(Unit) { viewModel.navigationEvents.collect { target -> ... } }`.
 *
 * **In this project:** [NavigationTarget] mirrors the [Routes] hierarchy but lives in the domain
 * layer of the UI — ViewModels know *where* to go (semantically) without importing any Navigation
 * Compose types. The [SpaceTradersNavHost] owns the translation from [NavigationTarget] values to
 * actual `NavController` calls.
 *
 * **Why a sealed interface instead of a sealed class?** A sealed interface allows subtypes to also
 * implement other interfaces, which can be useful when a navigation event is also a domain event
 * (e.g. carries a result). It also avoids the implicit `copy()` / `componentN()` boilerplate of a
 * sealed class with no properties. For navigation signals that are pure singletons, `data object`
 * inside a sealed interface is the most idiomatic Kotlin choice.
 */
sealed interface NavigationTarget {

    /**
     * Navigate to the main dashboard screen.
     *
     * **In this project:** Emitted by [AuthViewModel] after a successful agent registration or
     * token import. The receiving composable clears [AuthRoute] from the back stack so the user
     * cannot press Back and return to the auth screen.
     */
    data object Dashboard : NavigationTarget

    /**
     * Navigate to the authentication screen (effectively, log out).
     *
     * **In this project:** Emitted by [DashboardViewModel] after the user confirms logout. The
     * receiving composable clears [DashboardRoute] from the back stack so pressing Back cannot
     * return to a now-unauthenticated dashboard.
     */
    data object Auth : NavigationTarget

    /**
     * Navigate to the ship list screen.
     *
     * **In this project:** Emitted by [DashboardViewModel] when the user taps the "Ships" entry
     * point. No parameters are needed because the list is always scoped to the active agent.
     */
    data object ShipList : NavigationTarget

    /**
     * Navigate to the detail screen for a specific ship.
     *
     * **In this project:** Emitted by [ShipListViewModel] when the user taps a ship card. The
     * [shipSymbol] is forwarded directly to [ShipDetailRoute] and then to the ViewModel on the
     * detail screen to load that ship's data.
     *
     * @property shipSymbol The SpaceTraders API identifier for the ship (e.g. `"HAULER-1"`).
     */
    data class ShipDetail(val shipSymbol: String) : NavigationTarget

    /**
     * Navigate to the system map, optionally pre-focused on a waypoint and/or ship.
     *
     * **In this project:** Emitted by [ShipDetailViewModel] when the user taps "View on Map". The
     * optional fields allow the map to open pre-focused on the ship's current location without
     * requiring a separate route type for each entry context.
     *
     * @property systemSymbol The star system to open (e.g. `"X1-DF55"`).
     * @property focusWaypointSymbol Waypoint to pan to on entry. `null` uses the map's default
     *   viewport.
     * @property shipSymbol Ship to highlight on the map. `null` means no pre-selection.
     */
    data class SystemMap(
        val systemSymbol: String,
        val focusWaypointSymbol: String? = null,
        val shipSymbol: String? = null
    ) : NavigationTarget

    data class ShipyardScreen(val systemSymbol: String, val waypointSymbol: String) : NavigationTarget

    data object ContractsScreen : NavigationTarget
}
