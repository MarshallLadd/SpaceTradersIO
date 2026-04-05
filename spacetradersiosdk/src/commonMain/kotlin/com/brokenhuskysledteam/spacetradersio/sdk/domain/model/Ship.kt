package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

data class Ship(
    val symbol: String,
    val registration: ShipRegistration,
    val nav: ShipNav,
    val cargo: ShipCargo,
    val fuel: ShipFuel,
    val frameName: String,
    val cooldown: Cooldown
)
