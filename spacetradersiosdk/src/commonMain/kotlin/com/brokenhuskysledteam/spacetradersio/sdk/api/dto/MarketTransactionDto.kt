package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class MarketTransactionDto(
    val waypointSymbol: String,
    val shipSymbol: String,
    val tradeSymbol: String,
    val type: String,
    val units: Int,
    val pricePerUnit: Int,
    val totalPrice: Int,
    val timestamp: String
)
