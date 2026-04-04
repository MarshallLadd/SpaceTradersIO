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

// Endpoints under the "Contracts" tag in the OpenAPI spec.
// All endpoints require an authenticated client (AgentToken).
class ContractsApi(private val client: SpaceTradersClient) {

    // GET /my/contracts — lists all contracts for the authenticated agent.
    suspend fun getMyContracts(page: Int = 1, limit: Int = 20): PaginatedResponse<ContractDto> =
        client.authenticated.get("my/contracts") {
            parameter("page", page)
            parameter("limit", limit)
        }.body()

    // GET /my/contracts/{contractId} — fetches a single contract by ID.
    suspend fun getContract(contractId: String): ContractDto =
        client.authenticated.get("my/contracts/$contractId").body<ApiResponse<ContractDto>>().data

    // POST /my/contracts/{contractId}/accept — accepts a contract.
    // Returns the updated contract and agent (credits deducted for advance payment).
    suspend fun acceptContract(contractId: String): ContractActionResponseDto =
        client.authenticated.post("my/contracts/$contractId/accept")
            .body<ApiResponse<ContractActionResponseDto>>().data

    // POST /my/contracts/{contractId}/deliver — delivers cargo toward a contract.
    // Returns the updated contract progress and the ship's updated cargo hold.
    suspend fun deliverCargo(
        contractId: String,
        shipSymbol: String,
        tradeSymbol: String,
        units: Int
    ): DeliverCargoResponseDto =
        client.authenticated.post("my/contracts/$contractId/deliver") {
            setBody(DeliverCargoRequestDto(shipSymbol, tradeSymbol, units))
        }.body<ApiResponse<DeliverCargoResponseDto>>().data

    // POST /my/contracts/{contractId}/fulfill — fulfills a completed contract.
    // Returns the updated contract and agent (reward credits added).
    suspend fun fulfillContract(contractId: String): ContractActionResponseDto =
        client.authenticated.post("my/contracts/$contractId/fulfill")
            .body<ApiResponse<ContractActionResponseDto>>().data
}
