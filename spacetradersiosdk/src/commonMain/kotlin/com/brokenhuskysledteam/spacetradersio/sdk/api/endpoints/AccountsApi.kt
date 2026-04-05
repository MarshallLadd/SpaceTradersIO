package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ApiResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.RegisterRequestDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.RegisterResponseDto
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

// Endpoints under the "Accounts" tag in the OpenAPI spec.
// Registration requires an AccountToken — a per-account Bearer issued from the
// SpaceTraders dashboard. It is never stored; it is used once here and then dropped.
class AccountsApi(private val spaceTradersClient: SpaceTradersClient) {

    // POST /register — authenticated with AccountToken (not AgentToken).
    // Creates a new agent linked to the account and returns the AgentToken
    // along with the agent's starting state (faction, contract, ships).
    suspend fun register(symbol: String, faction: String, accountToken: String): RegisterResponseDto =
        spaceTradersClient.authenticatedWith(accountToken).post("register") {
            setBody(RegisterRequestDto(symbol = symbol, faction = faction))
        }.body<ApiResponse<RegisterResponseDto>>().data
}
