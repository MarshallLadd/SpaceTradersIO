package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

/**
 * Wire-format response from `POST /my/contracts/{id}/accept` and
 * `POST /my/contracts/{id}/fulfill`.
 *
 * **Pattern:** Composite response DTO. Both operations atomically update two resources —
 * the contract (its `accepted` or `fulfilled` flag) and the agent (credit balance changes
 * on acceptance and fulfilment). Returning both together avoids a follow-up
 * `GET /my/agent` round trip. In a new project, wherever an action touches multiple
 * resources, prefer a composite response DTO over requiring the caller to refresh each
 * resource individually.
 *
 * **In this project:** `ContractsApi.acceptContract()` and `ContractsApi.fulfillContract()`
 * both decode this DTO. The mapper produces a `ContractActionResult` domain model that
 * carries the updated [ContractDto] and [AgentDto] so the SDK can update both state stores
 * in one callback.
 *
 * @property contract The updated contract after the action was applied. On accept,
 *   `accepted` will be `true`. On fulfil, `fulfilled` will be `true`.
 * @property agent The agent's updated state, primarily reflecting any credit change:
 *   the `onAccepted` payment is added on acceptance, and `onFulfilled` on fulfilment.
 */
@Serializable
data class ContractActionResponseDto(
    val contract: ContractDto,
    val agent: AgentDto
)

/**
 * Wire-format response from `POST /my/contracts/{id}/deliver`.
 *
 * **Pattern:** Composite response DTO. Delivering cargo updates two resources: the
 * contract's delivery progress and the ship's cargo hold (units are removed). Bundling
 * both in the response keeps the caller's state consistent without extra requests.
 *
 * **In this project:** `ContractsApi.deliverCargo()` decodes this DTO. The mapper produces
 * a `DeliverCargoResult` carrying the updated contract (incremented `unitsFulfilled`) and
 * the ship's updated cargo (reduced `units`).
 *
 * @property contract The updated contract, with [ContractDeliverGoodDto.unitsFulfilled]
 *   incremented for the delivered good.
 * @property cargo The delivering ship's updated cargo state, reflecting the units removed
 *   from the hold during delivery.
 */
@Serializable
data class DeliverCargoResponseDto(
    val contract: ContractDto,
    val cargo: ShipCargoDto
)

/**
 * Request body for `POST /my/contracts/{id}/deliver`.
 *
 * **Pattern:** Typed request body DTO. Using a typed data class with `setBody(dto)` is
 * cleaner than building raw JSON strings. Ktor's `ContentNegotiation` plugin serializes
 * it automatically when configured with `kotlinx.serialization`. In a new project, create
 * a matching request DTO for every endpoint that accepts a JSON body — constructor
 * parameters make required fields explicit and the compiler catches missing arguments.
 *
 * **In this project:** The delivery endpoint needs to know which ship is delivering, what
 * good it is delivering, and how many units. All three are required by the API.
 *
 * @property shipSymbol The symbol of the ship performing the delivery. The ship must be
 *   docked at the contract's destination waypoint and have the specified cargo in its hold.
 * @property tradeSymbol The commodity identifier to deliver (e.g. `"IRON_ORE"`). Must
 *   match one of the goods listed in [ContractDto.terms] `deliver` entries.
 * @property units The number of units to deliver in this call. Must not exceed the ship's
 *   current hold quantity for this trade symbol.
 */
@Serializable
data class DeliverCargoRequestDto(
    val shipSymbol: String,
    val tradeSymbol: String,
    val units: Int
)
