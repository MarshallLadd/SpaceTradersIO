package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractType
import kotlin.time.Instant

// Domain model for a SpaceTraders contract.
// Contracts are missions issued by factions — accept, deliver goods, fulfill for credits.
// Dates are parsed from ISO-8601 strings into [Instant] at the mapper layer.
data class Contract(
    val id: String,
    val factionSymbol: String,
    val type: ContractType,
    val accepted: Boolean,
    val fulfilled: Boolean,
    // deadlineToAccept supersedes the deprecated expiration field.
    // Both are kept here to handle contracts that only provide expiration.
    val deadlineToAccept: Instant?,
    val terms: ContractTerms
)

data class ContractTerms(
    val deadline: Instant,
    val paymentOnAccepted: Int,
    val paymentOnFulfilled: Int
)
