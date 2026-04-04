package com.brokenhuskysledteam.spacetradersio.navigation

sealed interface NavigationTarget {
    data object Dashboard : NavigationTarget
    data object Auth : NavigationTarget
}
