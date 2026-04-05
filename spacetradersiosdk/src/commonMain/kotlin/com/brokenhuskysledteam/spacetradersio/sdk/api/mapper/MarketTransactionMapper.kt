package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MarketTransactionDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.MarketTransaction
import kotlin.time.Instant

fun MarketTransactionDto.toDomain(): MarketTransaction = MarketTransaction(
    waypointSymbol = waypointSymbol,
    shipSymbol = shipSymbol,
    tradeSymbol = tradeSymbol,
    type = type,
    units = units,
    pricePerUnit = pricePerUnit,
    totalPrice = totalPrice,
    timestamp = Instant.parse(timestamp)
)
