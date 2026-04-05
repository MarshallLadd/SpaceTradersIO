package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import kotlin.time.Instant

data class MarketTransaction(
    val waypointSymbol: String,
    val shipSymbol: String,
    val tradeSymbol: String,
    val type: String,
    val units: Int,
    val pricePerUnit: Int,
    val totalPrice: Int,
    val timestamp: Instant
)
