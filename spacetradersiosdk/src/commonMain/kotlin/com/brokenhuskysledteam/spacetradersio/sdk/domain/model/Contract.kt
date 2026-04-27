package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractType
import kotlin.time.Instant

data class Contract(
    val id: String,
    val factionSymbol: String,
    val type: ContractType,
    val accepted: Boolean,
    val fulfilled: Boolean,
    val deadlineToAccept: Instant?,
    val terms: ContractTerms,
    val status: ContractStatus
)

data class ContractTerms(
    val deadline: Instant,
    val paymentOnAccepted: Int,
    val paymentOnFulfilled: Int,
    val deliverGoods: List<ContractDeliverGood> = emptyList()
)

data class ContractDeliverGood(
    val tradeSymbol: String,
    val destinationSymbol: String,
    val unitsRequired: Int,
    val unitsFulfilled: Int
)
