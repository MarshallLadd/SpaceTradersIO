package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ContractDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ContractTerms
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractType
import kotlin.time.Instant

fun ContractDto.toDomain(): Contract = Contract(
    id = id,
    factionSymbol = factionSymbol,
    type = ContractType.fromString(type),
    accepted = accepted,
    fulfilled = fulfilled,
    // Prefer deadlineToAccept; fall back to the deprecated expiration field.
    deadlineToAccept = (deadlineToAccept ?: expiration).let { Instant.parse(it) },
    terms = ContractTerms(
        deadline = Instant.parse(terms.deadline),
        paymentOnAccepted = terms.payment.onAccepted,
        paymentOnFulfilled = terms.payment.onFulfilled
    )
)
