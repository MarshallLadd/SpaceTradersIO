package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ApiResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ContractActionResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ContractDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.DeliverCargoRequestDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.DeliverCargoResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.PaginatedResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Endpoints under the "Contracts" tag in the SpaceTraders OpenAPI spec.
 *
 * **Pattern:** Concrete API class (no interface). Use a plain class instead of
 * interface + Impl when the class is unlikely to need a test double — for example,
 * when the calling repository is the testable boundary and this class is always
 * mocked at the repository level. Compare with [AgentsApi] and [FleetApi] which
 * expose interfaces because their direct callers are use cases that need injection.
 *
 * **In this project:** All contract endpoints require an authenticated client
 * (AgentToken). The contract lifecycle is linear: fetch → accept → deliver
 * (repeat) → fulfill. Each state transition is a separate POST endpoint and returns
 * the updated contract so the UI can reflect progress without a separate GET.
 *
 * @param client The shared [SpaceTradersClient]. Must have a valid AgentToken stored
 *   before any method on this class is called.
 */
class ContractsApi(private val client: SpaceTradersClient) {

    /**
     * Returns a paginated list of all contracts visible to the authenticated agent.
     *
     * Calls `GET /my/contracts`. This includes contracts in all states:
     * offered, accepted, in-progress, fulfilled, and expired. Callers should filter
     * by `ContractDto.accepted` / `ContractDto.fulfilled` as needed.
     *
     * **Pagination:** The SpaceTraders API uses 1-based pages. Pass [page] = 1 on
     * the first call, then increment using `MetaDto.total` and [limit] to determine
     * whether additional pages exist. Example: if `total` = 45 and `limit` = 20,
     * pages 1, 2, and 3 are needed (page 3 returns the remaining 5 items).
     *
     * @param page 1-based page index. Defaults to 1 (first page).
     * @param limit Maximum items per page. Defaults to 20; the API maximum is 20.
     * @return [PaginatedResponse] wrapping a list of [ContractDto] plus [com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MetaDto]
     *   for total-count-based pagination.
     */
    suspend fun getMyContracts(page: Int = 1, limit: Int = 20): PaginatedResponse<ContractDto> =
        client.authenticated.get("my/contracts") {
            parameter("page", page)
            parameter("limit", limit)
        }.body()

    /**
     * Fetches a single contract by its unique ID.
     *
     * Calls `GET /my/contracts/{contractId}`. Use this to refresh a specific
     * contract's state (e.g. after delivering cargo) without re-fetching the full list.
     *
     * @param contractId The unique server-assigned contract identifier
     *   (e.g. `"clxxxxx..."`).
     * @return The current [ContractDto] for the given ID.
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersApiException
     *   if the contract does not belong to the authenticated agent.
     */
    suspend fun getContract(contractId: String): ContractDto =
        client.authenticated.get("my/contracts/$contractId").body<ApiResponse<ContractDto>>().data

    /**
     * Accepts an offered contract, committing the agent to its terms.
     *
     * Calls `POST /my/contracts/{contractId}/accept`. The response includes the
     * updated contract (now marked `accepted = true`) and the agent's updated
     * credit balance, because some contracts provide an upfront advance payment
     * on acceptance.
     *
     * **Empty-body POST:** This endpoint has no request body in the OpenAPI spec,
     * but the global `Content-Type: application/json` set by `defaultRequest` in
     * [SpaceTradersClient] means an empty body would cause HTTP 422. No `setBody`
     * call is made here — if the API starts rejecting this, add `setBody("{}")`.
     * See [dockShip][com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApiImpl.dockShip]
     * for the canonical example of an empty-body workaround.
     *
     * @param contractId The ID of the offered contract to accept.
     * @return [ContractActionResponseDto] containing the updated [ContractDto] and
     *   the updated [com.brokenhuskysledteam.spacetradersio.sdk.api.dto.AgentDto]
     *   (reflecting any advance-payment credit change).
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersApiException
     *   if the contract is already accepted, already fulfilled, or has expired.
     */
    suspend fun acceptContract(contractId: String): ContractActionResponseDto =
        client.authenticated.post("my/contracts/$contractId/accept")
            .body<ApiResponse<ContractActionResponseDto>>().data

    /**
     * Records a cargo delivery against an accepted contract's requirements.
     *
     * Calls `POST /my/contracts/{contractId}/deliver`. The ship must be at the
     * contract's designated delivery waypoint and must have sufficient units of the
     * required trade good in its cargo hold. The API deducts the delivered units from
     * the cargo hold and increments the contract's `unitsFulfilled` counter.
     *
     * Unlike the other contract action endpoints, this POST has a real request body
     * ([DeliverCargoRequestDto]) specifying which ship is delivering, which good, and
     * how many units. `setBody(...)` triggers [ContentNegotiation] to serialise the DTO.
     *
     * @param contractId The ID of the accepted contract being fulfilled.
     * @param shipSymbol The call-sign of the ship carrying the cargo (e.g. `"MYAGENT-1"`).
     * @param tradeSymbol The trade good identifier matching the contract requirement
     *   (e.g. `"IRON_ORE"`).
     * @param units The number of units to deliver. Must not exceed the ship's current
     *   cargo hold quantity of that good.
     * @return [DeliverCargoResponseDto] containing the updated [ContractDto] (progress)
     *   and the updated ship cargo hold.
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersApiException
     *   if the ship is not at the delivery waypoint, the cargo is insufficient, or
     *   the contract is not in the `accepted` state.
     */
    suspend fun deliverCargo(
        contractId: String,
        shipSymbol: String,
        tradeSymbol: String,
        units: Int
    ): DeliverCargoResponseDto =
        client.authenticated.post("my/contracts/$contractId/deliver") {
            setBody(DeliverCargoRequestDto(shipSymbol, tradeSymbol, units))
        }.body<ApiResponse<DeliverCargoResponseDto>>().data

    /**
     * Fulfills a contract once all delivery requirements have been met.
     *
     * Calls `POST /my/contracts/{contractId}/fulfill`. This is the final step in the
     * contract lifecycle. On success the contract is marked `fulfilled = true` and
     * the reward credits are added to the agent's balance. The API returns the updated
     * contract and agent in a single response, avoiding a round-trip to refresh state.
     *
     * **Empty-body POST:** Same consideration as [acceptContract] — this endpoint has
     * no request body. The current implementation omits `setBody`; add `setBody("{}")`
     * if the API returns 422.
     *
     * @param contractId The ID of the fully-delivered contract to close out.
     * @return [ContractActionResponseDto] containing the fulfilled [ContractDto] and
     *   the updated [com.brokenhuskysledteam.spacetradersio.sdk.api.dto.AgentDto]
     *   (reflecting the reward credit addition).
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersApiException
     *   if the contract's delivery requirements have not yet been met, or if the
     *   contract is already fulfilled/expired.
     */
    suspend fun fulfillContract(contractId: String): ContractActionResponseDto =
        client.authenticated.post("my/contracts/$contractId/fulfill")
            .body<ApiResponse<ContractActionResponseDto>>().data
}
