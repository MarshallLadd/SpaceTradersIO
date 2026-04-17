package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.AgentDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ApiResponse
import io.ktor.client.call.body
import io.ktor.client.request.get

/**
 * Endpoints under the "Agents" tag in the SpaceTraders OpenAPI spec.
 *
 * **Pattern:** Interface + Impl for API clients. Define a Kotlin interface listing
 * every endpoint as a `suspend fun`, then provide a single `Impl` class that makes
 * the real Ktor network calls. This split pays off in two ways:
 *
 * 1. **Testability** — Unit tests for use cases and repositories inject a hand-written
 *    fake (or a [io.ktor.client.engine.mock.MockEngine]-backed implementation) that
 *    returns controlled data without touching the network.
 * 2. **Replaceability** — Swapping the HTTP library or mocking the entire API tier
 *    in a future project requires only a new `Impl`, not changes to every call site.
 *
 * **In this project:** All gameplay endpoints require an authenticated client
 * carrying the AgentToken. Both methods here delegate to [SpaceTradersClient.authenticated],
 * which reads the stored token from
 * [com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository]
 * on each access.
 */
interface AgentsApi {

    /**
     * Fetches the profile of the currently authenticated agent.
     *
     * Calls `GET /my/agent`. Returns credits, headquarters waypoint, ship count,
     * and the agent's call-sign. This is the primary "who am I?" endpoint used
     * to populate the dashboard after login.
     *
     * @return [AgentDto] containing the authenticated agent's current state.
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersApiException
     *   if the AgentToken is missing or expired.
     */
    suspend fun getMyAgent(): AgentDto

    /**
     * Fetches the public profile of any agent by their call-sign symbol.
     *
     * Calls `GET /agents/{agentSymbol}`. Agent profiles are public — no special
     * permissions are required beyond holding a valid AgentToken. Useful for
     * leaderboard displays or inspecting competitor agents.
     *
     * @param symbol The agent's call-sign (e.g. `"MYAGENT"`). Case-sensitive;
     *   the API normalises symbols to uppercase on registration, so always pass
     *   an uppercase value.
     * @return [AgentDto] for the requested agent.
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersApiException
     *   if no agent with the given symbol exists.
     */
    suspend fun getAgent(symbol: String): AgentDto
}

/**
 * Production implementation of [AgentsApi] backed by the real SpaceTraders HTTP API.
 *
 * Each method calls [SpaceTradersClient.authenticated] to obtain an [io.ktor.client.HttpClient]
 * with the stored AgentToken attached as a Bearer header, issues the Ktor request, and
 * unwraps the `data` field from the [ApiResponse] wrapper that the SpaceTraders API
 * uses for all successful responses.
 *
 * @param client The shared [SpaceTradersClient]. Must have a valid AgentToken stored
 *   in its [com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository]
 *   before any method is called.
 */
class AgentsApiImpl(private val client: SpaceTradersClient) : AgentsApi {

    /**
     * Fetches the authenticated agent's profile via `GET /my/agent`.
     *
     * `client.authenticated.get("my/agent")` demonstrates the standard Ktor call
     * pattern: the path is relative to the `BASE_URL` set in `defaultRequest`,
     * `body<ApiResponse<AgentDto>>()` triggers [ContentNegotiation] to deserialise
     * the JSON response, and `.data` unwraps the envelope.
     */
    override suspend fun getMyAgent(): AgentDto =
        client.authenticated.get("my/agent").body<ApiResponse<AgentDto>>().data

    /**
     * Fetches a public agent's profile via `GET /agents/{agentSymbol}`.
     *
     * The [symbol] is interpolated directly into the path segment. Ktor does not
     * percent-encode path segments automatically when using string interpolation,
     * so callers must ensure [symbol] contains only valid URL characters (A–Z, 0–9,
     * hyphens) — which the SpaceTraders API guarantees for all agent symbols.
     */
    override suspend fun getAgent(symbol: String): AgentDto =
        client.authenticated.get("agents/$symbol").body<ApiResponse<AgentDto>>().data
}
