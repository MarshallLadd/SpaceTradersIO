package com.brokenhuskysledteam.spacetraders.api.endpoints

import com.brokenhuskysledteam.spacetraders.api.dto.ApiResponse
import com.brokenhuskysledteam.spacetraders.api.dto.RegisterRequestDto
import com.brokenhuskysledteam.spacetraders.api.dto.RegisterResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

// Endpoints under the "Accounts" tag in the OpenAPI spec.
// Registration is the only endpoint here and uses an unauthenticated client —
// no bearer token exists yet at the point of calling this.
class AccountsApi(private val client: HttpClient) {

    // POST /register — creates a new agent and returns the bearer token
    // along with the agent's starting state (faction, contract, ships).
    suspend fun register(symbol: String, faction: String): RegisterResponseDto =
        client.post("register") {
            setBody(RegisterRequestDto(symbol = symbol, faction = faction))
        }.body<ApiResponse<RegisterResponseDto>>().data
}
