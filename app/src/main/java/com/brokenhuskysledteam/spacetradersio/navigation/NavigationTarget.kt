package com.brokenhuskysledteam.spacetradersio.navigation

// One-shot navigation signals emitted by ViewModels via Channel.
// These are consumed by LaunchedEffect in screen composables and translated
// into NavController.navigate() calls. Using a sealed interface (rather than
// route objects directly) keeps ViewModel code decoupled from navigation internals.
sealed interface NavigationTarget {
    data object Dashboard : NavigationTarget
    data object Auth : NavigationTarget
    data object ShipList : NavigationTarget
    data class ShipDetail(val shipSymbol: String) : NavigationTarget
}
