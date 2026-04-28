package com.brokenhuskysledteam.spacetradersio.sdk.data.db

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ContractDeliverGood
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ContractTerms
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractType
import kotlin.time.Instant

import com.brokenhuskysledteam.spacetradersio.sdk.data.db.Contract as DbContract
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.Contract_deliver_good as DbGood

fun DbContract.toDomain(goods: List<DbGood>): Contract = Contract(
    id = id,
    factionSymbol = faction_symbol,
    type = ContractType.fromString(type),
    accepted = accepted != 0L,
    fulfilled = fulfilled != 0L,
    deadlineToAccept = deadline_to_accept?.let { Instant.parse(it) },
    terms = ContractTerms(
        deadline = Instant.parse(terms_deadline),
        paymentOnAccepted = payment_on_accepted.toInt(),
        paymentOnFulfilled = payment_on_fulfilled.toInt(),
        deliverGoods = goods.map { it.toDomain() }
    ),
    status = ContractStatus.fromString(status)
)

private fun DbGood.toDomain(): ContractDeliverGood = ContractDeliverGood(
    tradeSymbol = trade_symbol,
    destinationSymbol = destination_symbol,
    unitsRequired = units_required.toInt(),
    unitsFulfilled = units_fulfilled.toInt()
)

fun ContractQueries.upsert(contract: Contract) {
    upsert(
        id = contract.id,
        faction_symbol = contract.factionSymbol,
        type = contract.type.name,
        accepted = if (contract.accepted) 1L else 0L,
        fulfilled = if (contract.fulfilled) 1L else 0L,
        deadline_to_accept = contract.deadlineToAccept?.toString(),
        terms_deadline = contract.terms.deadline.toString(),
        payment_on_accepted = contract.terms.paymentOnAccepted.toLong(),
        payment_on_fulfilled = contract.terms.paymentOnFulfilled.toLong(),
        status = contract.status.name
    )
}

fun ContractDeliverGoodQueries.upsert(contractId: String, good: ContractDeliverGood) {
    upsert(
        contract_id = contractId,
        trade_symbol = good.tradeSymbol,
        destination_symbol = good.destinationSymbol,
        units_required = good.unitsRequired.toLong(),
        units_fulfilled = good.unitsFulfilled.toLong()
    )
}
