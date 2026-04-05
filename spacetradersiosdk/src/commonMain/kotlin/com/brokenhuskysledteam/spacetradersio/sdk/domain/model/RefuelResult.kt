package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

data class RefuelResult(
    val agent: Agent,
    val fuel: ShipFuel,
    val transaction: MarketTransaction
)
