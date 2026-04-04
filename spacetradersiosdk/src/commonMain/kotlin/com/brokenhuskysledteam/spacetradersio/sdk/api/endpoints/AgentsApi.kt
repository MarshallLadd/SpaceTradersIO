package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.AgentDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ApiResponse
import io.ktor.client.call.body
import io.ktor.client.request.get

// Endpoints under the "Agents" tag in the OpenAPI spec.
// Requires an authenticated client (AgentToken).
class AgentsApi(private val client: SpaceTradersClient) {

    // GET /my/agent — fetches the authenticated agent's details.
    suspend fun getMyAgent(): AgentDto =
        client.authenticated.get("my/agent").body<ApiResponse<AgentDto>>().data

    // GET /agents/{agentSymbol} — fetches a public agent by symbol.
    suspend fun getAgent(symbol: String): AgentDto =
        client.authenticated.get("agents/$symbol").body<ApiResponse<AgentDto>>().data
}
