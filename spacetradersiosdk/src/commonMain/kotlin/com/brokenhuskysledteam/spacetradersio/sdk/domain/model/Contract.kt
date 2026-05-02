package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractType
import kotlin.time.Instant

/**
 * Represents a single SpaceTraders contract offered to or held by the player's agent.
 *
 * **Pattern:** Immutable domain model. Like all models in `domain/model/`, this class has
 * no serialization annotations and no platform imports — it is pure Kotlin and usable in
 * `commonMain` across Android and iOS. The wire DTO is translated into this type by
 * `ContractMapper` in `api/mapper/`; callers in the UI and ViewModel layers never touch
 * the DTO directly.
 *
 * **In this project:** `Contract` objects are observed from the local SQLDelight cache via
 * [com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.ContractRepository.observeContracts].
 * The ContractsScreen displays them in a tabbed list (ACTIVE vs HISTORY). Accept and Fulfill
 * actions call back through the repository and return an updated `Contract`.
 *
 * **Contract lifecycle:** `UNACCEPTED → ACTIVE → FULFILLED` (or `EXPIRED`/`CANCELLED`).
 * Only `UNACCEPTED` contracts show an Accept button; only `ACTIVE` contracts with all goods
 * delivered show a Fulfill button.
 *
 * @property id Unique contract identifier. Used as the primary key in the local database
 *   and as the argument to `acceptContract` / `fulfillContract` API calls.
 * @property factionSymbol The faction that issued this contract (e.g. `"COSMIC"`).
 * @property type The contract category — `PROCUREMENT`, `TRANSPORT`, or `SHUTTLE`.
 *   Determines whether [ContractTerms.deliverGoods] will be non-empty.
 * @property accepted Raw API field — `true` once the player has accepted the contract.
 *   Used by [ContractMapper] to compute [status]; prefer reading [status] in UI code.
 * @property fulfilled Raw API field — `true` once the player has fulfilled the contract.
 *   Used by [ContractMapper] to compute [status]; prefer reading [status] in UI code.
 * @property deadlineToAccept The timestamp by which the contract must be accepted, or
 *   `null` if the contract has already been accepted (the API omits this field after
 *   acceptance). Only relevant for `UNACCEPTED` contracts.
 * @property terms Payment and delivery obligations — see [ContractTerms].
 * @property status Computed lifecycle status synthesised from [accepted] and [fulfilled]
 *   at the mapper boundary (see `ContractMapper`). This field is **not** present in the
 *   raw API response; it is derived and stored locally so the repository can filter by
 *   tab (ACTIVE vs HISTORY) with a simple SQL `WHERE status = ?` clause.
 */
data class Contract(
    val id: String,
    val factionSymbol: String,
    val type: ContractType,
    val accepted: Boolean,
    val fulfilled: Boolean,
    val deadlineToAccept: Instant?,
    val terms: ContractTerms,
    val status: ContractStatus
)

/**
 * Payment schedule and delivery requirements for a [Contract].
 *
 * **In this project:** Rendered by `ContractItem` in `ContractsScreen` and by the delivery
 * dialog in `ShipDetailScreen`. `paymentOnAccepted` is paid immediately when the player
 * accepts; `paymentOnFulfilled` is paid when the player calls Fulfill after all goods
 * are delivered.
 *
 * @property deadline The UTC timestamp by which all goods must be delivered and the
 *   contract fulfilled. Displayed in the accept-confirmation dialog.
 * @property paymentOnAccepted Credits deposited immediately on acceptance. May be `0`
 *   for some contract types.
 * @property paymentOnFulfilled Credits deposited when the contract is fulfilled.
 * @property deliverGoods The list of cargo deliveries required to complete the contract.
 *   Empty for `TRANSPORT` and `SHUTTLE` contracts that have no cargo requirement;
 *   non-empty for `PROCUREMENT` contracts. See [ContractDeliverGood].
 */
data class ContractTerms(
    val deadline: Instant,
    val paymentOnAccepted: Int,
    val paymentOnFulfilled: Int,
    val deliverGoods: List<ContractDeliverGood> = emptyList()
)

/**
 * A single cargo delivery requirement within a [ContractTerms].
 *
 * **In this project:** Each `ContractDeliverGood` is rendered as one progress row inside
 * `ContractItem` (e.g. `"IRON_ORE  42/100 @ X1-OE-A005"`). The `ShipDetailScreen` uses
 * this type to populate the deliver-cargo dialog's contract and good dropdowns. A contract
 * is considered ready to fulfill when every good has `unitsFulfilled >= unitsRequired`.
 *
 * @property tradeSymbol The cargo type to deliver (e.g. `"IRON_ORE"`). Matches the
 *   `symbol` of a ship's cargo inventory item.
 * @property destinationSymbol The waypoint where the cargo must be delivered
 *   (e.g. `"X1-OE-A005"`). The ship must be docked at this waypoint to call
 *   the deliver-cargo endpoint.
 * @property unitsRequired The total units of [tradeSymbol] the contract requires.
 * @property unitsFulfilled The units already delivered. Updated by the API after each
 *   successful deliver-cargo call and stored in the local `contract_deliver_good` table.
 */
data class ContractDeliverGood(
    val tradeSymbol: String,
    val destinationSymbol: String,
    val unitsRequired: Int,
    val unitsFulfilled: Int
)
