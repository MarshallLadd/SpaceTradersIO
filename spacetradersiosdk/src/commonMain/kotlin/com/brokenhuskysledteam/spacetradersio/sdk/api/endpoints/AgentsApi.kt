package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.AgentDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ApiResponse
import io.ktor.client.call.body
import io.ktor.client.request.get

// Endpoints under the "Agents" tag in the OpenAPI spec.
// Requires an authenticated client (AgentToken).
interface AgentsApi {
    suspend fun getMyAgent(): AgentDto
    suspend fun getAgent(symbol: String): AgentDto
}

class AgentsApiImpl(private val client: SpaceTradersClient) : AgentsApi {

    // GET /my/agent — fetches the authenticated agent's details.
    override suspend fun getMyAgent(): AgentDto =
        client.authenticated.get("my/agent").body<ApiResponse<AgentDto>>().data

    // GET /agents/{agentSymbol} — fetches a public agent by symbol.
    override suspend fun getAgent(symbol: String): AgentDto =
        client.authenticated.get("agents/$symbol").body<ApiResponse<AgentDto>>().data
}
