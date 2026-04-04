package com.brokenhuskysledteam.spacetraders.domain.model

import com.brokenhuskysledteam.spacetraders.domain.model.enums.ContractType
import kotlin.time.Instant

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
