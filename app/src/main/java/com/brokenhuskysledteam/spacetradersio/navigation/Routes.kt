package com.brokenhuskysledteam.spacetradersio.navigation

import kotlinx.serialization.Serializable

// Type-safe navigation routes used by Navigation Compose.
// @Serializable is required for the type-safe composable<T> { } DSL —
// Navigation Compose serializes these objects to build route strings internally.

@Serializable
object AuthRoute

@Serializable
object DashboardRoute

@Serializable
object ShipListRoute

@Serializable
data class ShipDetailRoute(val shipSymbol: String)
