package com.brokenhuskysledteam.spacetradersio.sdk.data.db

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

import com.brokenhuskysledteam.spacetradersio.sdk.data.db.Contract as DbContract
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.Contract_deliver_good as DbGood

class ContractDbMapperTest {

    private val futureTs = "2099-01-01T00:00:00Z"

    private fun makeDbContract(
        id: String = "C-001",
        factionSymbol: String = "COSMIC",
        type: String = "PROCUREMENT",
        accepted: Long = 0L,
        fulfilled: Long = 0L,
        deadlineToAccept: String? = futureTs,
        termsDeadline: String = futureTs,
        paymentOnAccepted: Long = 100L,
        paymentOnFulfilled: Long = 900L,
        status: String = "UNACCEPTED"
    ) = DbContract(
        id = id,
        faction_symbol = factionSymbol,
        type = type,
        accepted = accepted,
        fulfilled = fulfilled,
        deadline_to_accept = deadlineToAccept,
        terms_deadline = termsDeadline,
        payment_on_accepted = paymentOnAccepted,
        payment_on_fulfilled = paymentOnFulfilled,
        status = status
    )

    private fun makeDbGood(
        contractId: String = "C-001",
        tradeSymbol: String = "IRON_ORE",
        destinationSymbol: String = "X1-AB-001",
        unitsRequired: Long = 50L,
        unitsFulfilled: Long = 12L
    ) = DbGood(
        contract_id = contractId,
        trade_symbol = tradeSymbol,
        destination_symbol = destinationSymbol,
        units_required = unitsRequired,
        units_fulfilled = unitsFulfilled
    )

    @Test
    fun toDomain_maps_basic_fields() {
        val domain = makeDbContract(id = "C-001", factionSymbol = "COSMIC").toDomain(emptyList())
        assertEquals("C-001", domain.id)
        assertEquals("COSMIC", domain.factionSymbol)
        assertEquals(ContractType.PROCUREMENT, domain.type)
    }

    @Test
    fun toDomain_maps_booleans_from_long() {
        val domain = makeDbContract(accepted = 1L, fulfilled = 0L).toDomain(emptyList())
        assertEquals(true, domain.accepted)
        assertEquals(false, domain.fulfilled)
    }

    @Test
    fun toDomain_maps_fulfilled_boolean() {
        val domain = makeDbContract(accepted = 1L, fulfilled = 1L).toDomain(emptyList())
        assertEquals(true, domain.fulfilled)
    }

    @Test
    fun toDomain_maps_null_deadline() {
        val domain = makeDbContract(deadlineToAccept = null).toDomain(emptyList())
        assertNull(domain.deadlineToAccept)
    }

    @Test
    fun toDomain_maps_status() {
        val domain = makeDbContract(status = "ACTIVE").toDomain(emptyList())
        assertEquals(ContractStatus.ACTIVE, domain.status)
    }

    @Test
    fun toDomain_maps_unknown_status_to_expired() {
        val domain = makeDbContract(status = "GARBAGE").toDomain(emptyList())
        assertEquals(ContractStatus.EXPIRED, domain.status)
    }

    @Test
    fun toDomain_maps_deliver_goods() {
        val dbGood = makeDbGood(tradeSymbol = "IRON_ORE", unitsRequired = 50L, unitsFulfilled = 12L)
        val domain = makeDbContract().toDomain(listOf(dbGood))
        assertEquals(1, domain.terms.deliverGoods.size)
        assertEquals("IRON_ORE", domain.terms.deliverGoods[0].tradeSymbol)
        assertEquals(50, domain.terms.deliverGoods[0].unitsRequired)
        assertEquals(12, domain.terms.deliverGoods[0].unitsFulfilled)
    }

    @Test
    fun toDomain_empty_deliver_goods_produces_empty_list() {
        val domain = makeDbContract().toDomain(emptyList())
        assertEquals(emptyList(), domain.terms.deliverGoods)
    }

    @Test
    fun toDomain_maps_payment_fields() {
        val domain = makeDbContract(paymentOnAccepted = 200L, paymentOnFulfilled = 1800L).toDomain(emptyList())
        assertEquals(200, domain.terms.paymentOnAccepted)
        assertEquals(1800, domain.terms.paymentOnFulfilled)
    }
}
