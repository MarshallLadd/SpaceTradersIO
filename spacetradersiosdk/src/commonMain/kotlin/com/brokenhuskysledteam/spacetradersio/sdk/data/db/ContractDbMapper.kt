package com.brokenhuskysledteam.spacetradersio.sdk.data.db

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ContractDeliverGood
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ContractTerms
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractType
import kotlin.time.Instant

import com.brokenhuskysledteam.spacetradersio.sdk.data.db.Contract as DbContract
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.Contract_deliver_good as DbGood

/**
 * Converts a SQLDelight-generated [DbContract] row (plus its associated deliver-good rows)
 * into a [Contract] domain model.
 *
 * **Pattern:** DB mapper (storage → domain). Every offline-first feature needs two parallel
 * mapper paths that converge on the same domain type: one that reads from the network DTO
 * (`ContractMapper.kt` in `api/mapper/`) and one that reads from the local database (this
 * file). Keeping both paths separate lets each evolve independently. See `AgentDbMapper.kt`
 * in this package for the same pattern applied to the simpler, single-table Agent entity.
 *
 * **In this project:** `ContractRepositoryImpl` calls `toDomain(goods)` inside its
 * `observeContracts` flow, after joining the `contract` table with the
 * `contract_deliver_good` table by `contract_id`. The returned [Contract] is identical
 * whether the data arrived via the API or from the cache — the UI layer never knows
 * which source was used.
 *
 * **SQLite type impedance mismatches handled here:**
 * - `Boolean` → `Long` (0/1): SQLite has no boolean column type.
 * - `Instant` → `String` (ISO-8601): SQLite has no timestamp type; stored as text.
 * - `Int` → `Long`: SQLDelight maps all `INTEGER` schema columns to `Long`; convert back.
 *
 * @param goods Deliver-good rows fetched from the `contract_deliver_good` table for this
 *   contract. Passed in by the repository after a separate query or join, because SQLDelight
 *   does not automatically resolve one-to-many relationships.
 */
fun DbContract.toDomain(goods: List<DbGood>): Contract = Contract(
    id = id,
    factionSymbol = faction_symbol,
    type = ContractType.fromString(type),
    // SQLite stores booleans as INTEGER 0/1; compare to 0L to restore the Boolean.
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
    // SQLDelight INTEGER → Long; domain model uses Int for unit counts.
    unitsRequired = units_required.toInt(),
    unitsFulfilled = units_fulfilled.toInt()
)

/**
 * Upserts [contract] into the `contract` table using SQLDelight-generated [ContractQueries].
 *
 * **Pattern:** Queries extension for upsert mapping (domain → storage). Rather than placing
 * domain-to-column translation inside the repository, push it into the mapper layer as an
 * extension on the generated Queries class. The repository reads as a single call
 * (`contractQueries.upsert(contract)`), and all field-mapping logic is co-located with
 * [DbContract.toDomain] — the two directions of the same mapping concern live together.
 *
 * **In this project:** Called by `ContractRepositoryImpl` whenever contracts arrive from the
 * API (after a `refreshContracts`, `acceptContract`, or `fulfillContract` call). SQLDelight's
 * generated `upsert` SQL uses `INSERT OR REPLACE`, so the first call creates the row and
 * subsequent calls overwrite it. The child `contract_deliver_good` rows are upserted
 * separately via [ContractDeliverGoodQueries.upsert].
 *
 * @param contract The domain model to persist.
 */
fun ContractQueries.upsert(contract: Contract) {
    upsert(
        id = contract.id,
        faction_symbol = contract.factionSymbol,
        type = contract.type.name,
        // Boolean → Long: SQLite INTEGER column; 1L = true, 0L = false.
        accepted = if (contract.accepted) 1L else 0L,
        fulfilled = if (contract.fulfilled) 1L else 0L,
        // Instant → String: store as ISO-8601 text; nullable for post-acceptance contracts.
        deadline_to_accept = contract.deadlineToAccept?.toString(),
        terms_deadline = contract.terms.deadline.toString(),
        // Int → Long: SQLDelight INTEGER columns are Long.
        payment_on_accepted = contract.terms.paymentOnAccepted.toLong(),
        payment_on_fulfilled = contract.terms.paymentOnFulfilled.toLong(),
        status = contract.status.name
    )
}

/**
 * Upserts a [ContractDeliverGood] row into the `contract_deliver_good` child table.
 *
 * **In this project:** Called once per deliver-good entry whenever a contract is written to
 * the database. The child table is keyed on `(contract_id, trade_symbol)` so repeated
 * upserts safely update delivery progress (`units_fulfilled`) without creating duplicate rows.
 *
 * @param contractId The parent [Contract.id] this good belongs to (foreign key).
 * @param good The delivery requirement to persist.
 */
fun ContractDeliverGoodQueries.upsert(contractId: String, good: ContractDeliverGood) {
    upsert(
        contract_id = contractId,
        trade_symbol = good.tradeSymbol,
        destination_symbol = good.destinationSymbol,
        units_required = good.unitsRequired.toLong(),
        units_fulfilled = good.unitsFulfilled.toLong()
    )
}
