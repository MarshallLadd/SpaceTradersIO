package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.SupplyLevel

data class ShipyardShip(
    val type: ShipType,
    val name: String,
    val description: String,
    val purchasePrice: Int,
    val supply: SupplyLevel,
    val frameName: String,
    val engineSpeed: Int,
    val reactorPowerOutput: Int,
    val crewRequired: Int,
    val crewCapacity: Int
)
