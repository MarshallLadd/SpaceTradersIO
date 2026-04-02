package com.brokenhuskysledteam.spacetraders.api.mapper

import com.brokenhuskysledteam.spacetraders.api.dto.ContractDeliverGoodDto
import com.brokenhuskysledteam.spacetraders.api.dto.ContractDto
import com.brokenhuskysledteam.spacetraders.api.dto.ContractPaymentDto
import com.brokenhuskysledteam.spacetraders.api.dto.ContractTermsDto
import com.brokenhuskysledteam.spacetraders.domain.model.enums.ContractType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ContractMapperTest {

    private val deadline = "2025-06-15T12:00:00.000Z"
    private val deadlineInstant = Instant.parse(deadline)

    private fun minimalDto(
        type: String = "PROCUREMENT",
        expiration: String = "2025-05-01T00:00:00.000Z",
        deadlineToAccept: String? = "2025-05-10T00:00:00.000Z"
    ) = ContractDto(
        id = "contract-1",
        factionSymbol = "COSMIC",
        type = type,
        terms = ContractTermsDto(
            deadline = deadline,
            payment = ContractPaymentDto(onAccepted = 1000, onFulfilled = 5000),
            deliver = emptyList()
        ),
        accepted = false,
        fulfilled = false,
        expiration = expiration,
        deadlineToAccept = deadlineToAccept
    )

    @Test
    fun toDomain_allFieldsMapCorrectly() {
        val dto = minimalDto()
        val domain = dto.toDomain()

        assertEquals("contract-1", domain.id)
        assertEquals("COSMIC", domain.factionSymbol)
        assertEquals(ContractType.PROCUREMENT, domain.type)
        assertEquals(false, domain.accepted)
        assertEquals(false, domain.fulfilled)
        assertEquals(deadlineInstant, domain.terms.deadline)
        assertEquals(1000, domain.terms.paymentOnAccepted)
        assertEquals(5000, domain.terms.paymentOnFulfilled)
    }

    @Test
    fun toDomain_deadlineToAcceptPresent_usesDeadlineToAccept() {
        val acceptBy = "2025-05-10T00:00:00.000Z"
        val dto = minimalDto(deadlineToAccept = acceptBy)

        assertEquals(Instant.parse(acceptBy), dto.toDomain().deadlineToAccept)
    }

    @Test
    fun toDomain_deadlineToAcceptNull_fallsBackToExpiration() {
        val expiration = "2025-05-01T00:00:00.000Z"
        val dto = minimalDto(expiration = expiration, deadlineToAccept = null)

        assertEquals(Instant.parse(expiration), dto.toDomain().deadlineToAccept)
    }

    @Test
    fun toDomain_unknownContractType_fallsBackToProcurement() {
        val dto = minimalDto(type = "FUTURE_TYPE")

        assertEquals(ContractType.PROCUREMENT, dto.toDomain().type)
    }
}
