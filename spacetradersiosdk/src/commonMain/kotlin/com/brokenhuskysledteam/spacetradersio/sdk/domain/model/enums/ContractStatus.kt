package com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums

import kotlin.time.Instant

enum class ContractStatus {
    UNACCEPTED,
    ACTIVE,
    FULFILLED,
    EXPIRED,
    FAILED;

    companion object {
        fun fromString(value: String): ContractStatus =
            entries.firstOrNull { it.name == value } ?: EXPIRED
    }
}

fun computeContractStatus(
    accepted: Boolean,
    fulfilled: Boolean,
    deadlineToAccept: Instant?,
    termsDeadline: Instant,
    now: Instant
): ContractStatus = when {
    fulfilled -> ContractStatus.FULFILLED
    !accepted && deadlineToAccept != null && now > deadlineToAccept -> ContractStatus.EXPIRED
    !accepted -> ContractStatus.UNACCEPTED
    now > termsDeadline -> ContractStatus.FAILED
    else -> ContractStatus.ACTIVE
}
