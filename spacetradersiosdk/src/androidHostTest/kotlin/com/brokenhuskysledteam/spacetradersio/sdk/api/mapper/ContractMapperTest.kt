package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ContractDeliverGoodDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ContractDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ContractPaymentDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ContractTermsDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ContractMapperTest {

    private val futureDeadline = "2099-01-01T00:00:00.000Z"
    private val pastDeadline   = "2000-01-01T00:00:00.000Z"
    private val fixedNow       = Instant.parse("2026-04-27T12:00:00Z")

    private fun makeDto(
        accepted: Boolean = false,
        fulfilled: Boolean = false,
        deadlineToAccept: String? = futureDeadline,
        termsDeadline: String = futureDeadline,
        deliver: List<ContractDeliverGoodDto> = emptyList(),
        type: String = "PROCUREMENT"
    ) = ContractDto(
        id = "C-001",
        factionSymbol = "COSMIC",
        type = type,
        terms = ContractTermsDto(
            deadline = termsDeadline,
            payment = ContractPaymentDto(onAccepted = 100, onFulfilled = 900),
            deliver = deliver
        ),
        accepted = accepted,
        fulfilled = fulfilled,
        expiration = futureDeadline,
        deadlineToAccept = deadlineToAccept
    )

    // --- Basic field mapping ---

    @Test
    fun maps_all_basic_fields() {
        val domain = makeDto().toDomain(fixedNow)
        assertEquals("C-001", domain.id)
        assertEquals("COSMIC", domain.factionSymbol)
        assertEquals(ContractType.PROCUREMENT, domain.type)
        assertEquals(false, domain.accepted)
        assertEquals(false, domain.fulfilled)
        assertEquals(100, domain.terms.paymentOnAccepted)
        assertEquals(900, domain.terms.paymentOnFulfilled)
    }

    @Test
    fun uses_deadlineToAccept_when_present() {
        val domain = makeDto(deadlineToAccept = futureDeadline).toDomain(fixedNow)
        assertEquals(Instant.parse(futureDeadline), domain.deadlineToAccept)
    }

    @Test
    fun falls_back_to_expiration_when_deadlineToAccept_null() {
        val domain = makeDto(deadlineToAccept = null).toDomain(fixedNow)
        // expiration = futureDeadline in makeDto
        assertEquals(Instant.parse(futureDeadline), domain.deadlineToAccept)
        assertEquals(ContractStatus.UNACCEPTED, domain.status)
    }

    @Test
    fun unknown_contract_type_falls_back_to_procurement() {
        val domain = makeDto(type = "FUTURE_TYPE").toDomain(fixedNow)
        assertEquals(ContractType.PROCUREMENT, domain.type)
    }

    // --- Status computation ---

    @Test
    fun maps_unaccepted_status() {
        val domain = makeDto(accepted = false, deadlineToAccept = futureDeadline).toDomain(fixedNow)
        assertEquals(ContractStatus.UNACCEPTED, domain.status)
    }

    @Test
    fun maps_expired_status() {
        val domain = makeDto(accepted = false, deadlineToAccept = pastDeadline).toDomain(fixedNow)
        assertEquals(ContractStatus.EXPIRED, domain.status)
    }

    @Test
    fun maps_active_status() {
        val domain = makeDto(accepted = true, fulfilled = false, termsDeadline = futureDeadline).toDomain(fixedNow)
        assertEquals(ContractStatus.ACTIVE, domain.status)
    }

    @Test
    fun maps_failed_status() {
        val domain = makeDto(accepted = true, fulfilled = false, termsDeadline = pastDeadline).toDomain(fixedNow)
        assertEquals(ContractStatus.FAILED, domain.status)
    }

    @Test
    fun maps_fulfilled_status() {
        val domain = makeDto(accepted = true, fulfilled = true).toDomain(fixedNow)
        assertEquals(ContractStatus.FULFILLED, domain.status)
    }

    // --- Deliver goods mapping ---

    @Test
    fun maps_deliver_goods() {
        val dto = makeDto(deliver = listOf(
            ContractDeliverGoodDto("IRON_ORE", "X1-AB-001", unitsRequired = 50, unitsFulfilled = 12)
        ))
        val domain = dto.toDomain(fixedNow)
        assertEquals(1, domain.terms.deliverGoods.size)
        val good = domain.terms.deliverGoods[0]
        assertEquals("IRON_ORE", good.tradeSymbol)
        assertEquals("X1-AB-001", good.destinationSymbol)
        assertEquals(50, good.unitsRequired)
        assertEquals(12, good.unitsFulfilled)
    }

    @Test
    fun maps_empty_deliver_goods() {
        val domain = makeDto().toDomain(fixedNow)
        assertEquals(emptyList(), domain.terms.deliverGoods)
    }
}
