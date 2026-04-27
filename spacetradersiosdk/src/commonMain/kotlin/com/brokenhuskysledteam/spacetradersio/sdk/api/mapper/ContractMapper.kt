package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ContractDeliverGoodDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ContractDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ContractDeliverGood
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ContractTerms
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.computeContractStatus
import kotlin.time.Clock
import kotlin.time.Instant

fun ContractDto.toDomain(now: Instant = Clock.System.now()): Contract {
    val deadline = (deadlineToAccept ?: expiration).let { Instant.parse(it) }
    val termsDeadline = Instant.parse(terms.deadline)
    return Contract(
        id = id,
        factionSymbol = factionSymbol,
        type = ContractType.fromString(type),
        accepted = accepted,
        fulfilled = fulfilled,
        deadlineToAccept = deadline,
        terms = ContractTerms(
            deadline = termsDeadline,
            paymentOnAccepted = terms.payment.onAccepted,
            paymentOnFulfilled = terms.payment.onFulfilled,
            deliverGoods = terms.deliver.map { it.toDomain() }
        ),
        status = computeContractStatus(accepted, fulfilled, deadline, termsDeadline, now)
    )
}

private fun ContractDeliverGoodDto.toDomain(): ContractDeliverGood = ContractDeliverGood(
    tradeSymbol = tradeSymbol,
    destinationSymbol = destinationSymbol,
    unitsRequired = unitsRequired,
    unitsFulfilled = unitsFulfilled
)
