package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ContractDto(
    val id: String,
    val factionSymbol: String,
    val type: String,
    val terms: ContractTermsDto,
    val accepted: Boolean,
    val fulfilled: Boolean,
    val expiration: String,           // deprecated by API but still required in response
    val deadlineToAccept: String? = null
)

@Serializable
data class ContractTermsDto(
    val deadline: String,
    val payment: ContractPaymentDto,
    val deliver: List<ContractDeliverGoodDto> = emptyList()
)

@Serializable
data class ContractPaymentDto(
    val onAccepted: Int,
    val onFulfilled: Int
)

@Serializable
data class ContractDeliverGoodDto(
    val tradeSymbol: String,
    val destinationSymbol: String,
    val unitsRequired: Int,
    val unitsFulfilled: Int
)
