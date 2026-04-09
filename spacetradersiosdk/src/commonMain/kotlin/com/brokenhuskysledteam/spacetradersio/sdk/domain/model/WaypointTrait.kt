package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol

data class WaypointTrait(
    val symbol: WaypointTraitSymbol,
    val name: String,
    val description: String
)
