package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

// Mapper: MarketTransactionDto → MarketTransaction
// All DTO-to-domain conversions for the MarketTransaction entity live here.

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MarketTransactionDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.MarketTransaction
import kotlin.time.Instant

/**
 * Maps this [MarketTransactionDto] to a [MarketTransaction] domain model.
 *
 * **Pattern:** Extension function mapper. Placing the conversion as an extension on the DTO
 * type (`dto.toDomain()`) keeps callers readable and isolates the DTO→domain concern in one
 * place. In a new project, create one mapper per entity; even "trivial" mappers like this one
 * earn their place because they own the timestamp parsing and prevent that concern from
 * leaking into repositories or ViewModels.
 *
 * **In this project:** [MarketTransactionDto] is returned by buy and sell market endpoints.
 * It represents a single completed trade event — the good traded, the ship that traded it,
 * the quantity, the price per unit, and the total cost.
 *
 * **Timestamp handling:** [MarketTransactionDto.timestamp] is an ISO-8601 string from the
 * API wire format. [Instant.parse] converts it to [kotlin.time.Instant] here so the domain
 * model and UI code never handle raw timestamp strings. This is the same pattern used by
 * [ContractMapper] and [ShipMapper] — all timestamp parsing lives in the mapper layer.
 *
 * @return The domain model built from this DTO's data.
 */
fun MarketTransactionDto.toDomain(): MarketTransaction = MarketTransaction(
    waypointSymbol = waypointSymbol,
    shipSymbol = shipSymbol,
    tradeSymbol = tradeSymbol,
    type = type,
    units = units,
    pricePerUnit = pricePerUnit,
    totalPrice = totalPrice,
    // Parse the ISO-8601 timestamp string into a typed Instant. All timestamp conversions
    // happen in the mapper layer — the domain model only ever sees Instant values.
    timestamp = Instant.parse(timestamp)
)
