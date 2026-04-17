package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

/**
 * Wire-format representation of the SpaceTraders API `Contract` schema.
 *
 * **Pattern:** Nested DTO hierarchy. When an API response contains a deeply nested object
 * (here: a contract with terms, payment, and delivery requirements), model each level as its
 * own `@Serializable` data class rather than flattening everything into one type. Nesting
 * mirrors the JSON structure, which makes the mapping to/from JSON predictable and keeps
 * each class cohesive. The mapper (`ContractMapper.kt`) then translates the whole tree into
 * the domain model in one pass.
 *
 * **In this project:** Returned by `GET /my/contracts` (inside [PaginatedResponse]) and
 * embedded in [ContractActionResponseDto] and [DeliverCargoResponseDto]. Dates are raw
 * ISO-8601 strings here; the mapper (`ContractMapper.kt`) parses them into
 * `kotlin.time.Instant` for the domain model.
 *
 * @property id Server-assigned unique identifier. Used as the path parameter in all
 *   contract mutation endpoints (`/accept`, `/deliver`, `/fulfill`).
 * @property factionSymbol The faction that issued this contract (e.g. `"COSMIC"`).
 * @property type The mission category as a raw string (e.g. `"PROCUREMENT"`). The mapper
 *   converts this to the typed [com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractType]
 *   enum so the domain layer never works with magic strings.
 * @property terms The payment schedule and delivery deadline. See [ContractTermsDto].
 * @property accepted `true` after the agent has accepted via
 *   `POST /my/contracts/{id}/accept`. Unaccepted contracts expire when [deadlineToAccept]
 *   passes.
 * @property fulfilled `true` after all deliveries are complete and the agent has called
 *   `POST /my/contracts/{id}/fulfill` to collect the final payment.
 * @property expiration ISO-8601 timestamp marking when this contract expires. This field
 *   is **deprecated** by the SpaceTraders API in favour of [deadlineToAccept], but the
 *   server still includes it in responses. The mapper uses [deadlineToAccept] when present
 *   and falls back to this field when it is null, so neither field is safe to ignore.
 * @property deadlineToAccept ISO-8601 timestamp of the latest moment the agent may accept
 *   this contract. `null` on older contracts that only carry the deprecated [expiration]
 *   field. The mapper promotes [expiration] into the domain model's `deadlineToAccept`
 *   property when this value is absent.
 */
@Serializable
data class ContractDto(
    val id: String,
    val factionSymbol: String,
    val type: String,
    val terms: ContractTermsDto,
    val accepted: Boolean,
    val fulfilled: Boolean,
    val expiration: String,           // deprecated by API but still required in response
    val deadlineToAccept: String? = null
)

/**
 * The payment and delivery terms nested inside a [ContractDto].
 *
 * **Pattern:** Nested value DTO. Grouping related fields into a child DTO mirrors the JSON
 * structure and keeps each class focused. This makes it easy to update payment logic in
 * isolation without touching the parent contract type.
 *
 * **In this project:** Mapped to `ContractTerms` in the domain layer. The `deliver` list
 * is optional in the API schema and defaults to an empty list, so procurement-type
 * contracts that have no pre-specified goods still deserialize correctly.
 *
 * @property deadline ISO-8601 timestamp by which all deliveries must be completed to
 *   qualify for the [ContractPaymentDto.onFulfilled] payment.
 * @property payment The credit amounts awarded at acceptance and fulfilment.
 *   See [ContractPaymentDto].
 * @property deliver The list of goods the agent must deliver to satisfy this contract.
 *   Empty for contracts that do not require specific cargo (e.g. some shuttle contracts).
 *   Defaults to an empty list so the field can be absent in the JSON without causing a
 *   deserialization error.
 */
@Serializable
data class ContractTermsDto(
    val deadline: String,
    val payment: ContractPaymentDto,
    val deliver: List<ContractDeliverGoodDto> = emptyList()
)

/**
 * The credit amounts awarded at each stage of a contract.
 *
 * **Pattern:** Flat value DTO. Payment data is simple enough to live in its own small class
 * rather than being inlined into [ContractTermsDto], which would mix financial concerns with
 * time/logistics concerns.
 *
 * **In this project:** Mapped directly to `paymentOnAccepted` and `paymentOnFulfilled` in
 * `ContractTerms`. The UI displays both values so agents can evaluate whether a contract is
 * worth the upfront fuel cost.
 *
 * @property onAccepted Credits awarded immediately upon accepting the contract. Provides
 *   working capital for the mission (fuel, cargo purchases).
 * @property onFulfilled Credits awarded when all delivery requirements are met and
 *   `POST /my/contracts/{id}/fulfill` is called. This is typically the larger amount.
 */
@Serializable
data class ContractPaymentDto(
    val onAccepted: Int,
    val onFulfilled: Int
)

/**
 * A single cargo delivery requirement attached to a contract.
 *
 * **Pattern:** List-item DTO. When an API object contains a list of structured sub-items,
 * each item gets its own DTO type. This keeps the parent class clean and makes it easy to
 * add item-level computed properties in the mapper (e.g. `unitsRemaining`).
 *
 * **In this project:** One entry per good type the agent must deliver. The UI uses
 * [unitsFulfilled] vs [unitsRequired] to render a per-good progress bar in the contracts
 * detail screen.
 *
 * @property tradeSymbol Identifier of the commodity to deliver (e.g. `"IRON_ORE"`).
 *   Corresponds to a good in the SpaceTraders trade catalogue.
 * @property destinationSymbol Waypoint where the cargo must be delivered, in
 *   `<system>-<waypoint>` format.
 * @property unitsRequired Total number of units of [tradeSymbol] needed to satisfy this
 *   line item.
 * @property unitsFulfilled Number of units already delivered and accepted by the API.
 *   Progress is `unitsFulfilled / unitsRequired`. The delivery is complete when
 *   `unitsFulfilled >= unitsRequired`.
 */
@Serializable
data class ContractDeliverGoodDto(
    val tradeSymbol: String,
    val destinationSymbol: String,
    val unitsRequired: Int,
    val unitsFulfilled: Int
)
