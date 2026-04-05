package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipRole

data class ShipRegistration(
    val role: ShipRole,
    val factionSymbol: String
)
