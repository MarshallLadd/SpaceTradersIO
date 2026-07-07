package com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ContractStatusTest {

    private val future = Instant.parse("2099-01-01T00:00:00Z")
    private val past   = Instant.parse("2000-01-01T00:00:00Z")
    private val now    = Instant.parse("2026-04-27T12:00:00Z")

    @Test
    fun fulfilled_wins_over_all_other_conditions() {
        assertEquals(ContractStatus.FULFILLED,
            computeContractStatus(accepted = true, fulfilled = true,
                deadlineToAccept = past, termsDeadline = past, now = now))
    }

    @Test
    fun fulfilled_even_when_not_accepted() {
        assertEquals(ContractStatus.FULFILLED,
            computeContractStatus(accepted = false, fulfilled = true,
                deadlineToAccept = past, termsDeadline = future, now = now))
    }

    @Test
    fun expired_when_not_accepted_and_deadline_passed() {
        assertEquals(ContractStatus.EXPIRED,
            computeContractStatus(accepted = false, fulfilled = false,
                deadlineToAccept = past, termsDeadline = future, now = now))
    }

    @Test
    fun unaccepted_when_not_accepted_and_deadline_in_future() {
        assertEquals(ContractStatus.UNACCEPTED,
            computeContractStatus(accepted = false, fulfilled = false,
                deadlineToAccept = future, termsDeadline = future, now = now))
    }

    @Test
    fun unaccepted_when_not_accepted_and_null_deadline() {
        assertEquals(ContractStatus.UNACCEPTED,
            computeContractStatus(accepted = false, fulfilled = false,
                deadlineToAccept = null, termsDeadline = future, now = now))
    }

    @Test
    fun active_when_accepted_and_terms_deadline_in_future() {
        assertEquals(ContractStatus.ACTIVE,
            computeContractStatus(accepted = true, fulfilled = false,
                deadlineToAccept = past, termsDeadline = future, now = now))
    }

    @Test
    fun failed_when_accepted_and_terms_deadline_passed() {
        assertEquals(ContractStatus.FAILED,
            computeContractStatus(accepted = true, fulfilled = false,
                deadlineToAccept = past, termsDeadline = past, now = now))
    }

    @Test
    fun fromString_returns_expired_for_unknown() {
        assertEquals(ContractStatus.EXPIRED, ContractStatus.fromString("GARBAGE"))
    }

    @Test
    fun fromString_roundtrips_all_values() {
        ContractStatus.entries.forEach { status ->
            assertEquals(status, ContractStatus.fromString(status.name))
        }
    }
}
