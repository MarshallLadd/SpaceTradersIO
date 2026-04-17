package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ApiResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.RegisterRequestDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.RegisterResponseDto
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Endpoints under the "Accounts" tag in the SpaceTraders OpenAPI spec.
 *
 * **Pattern:** Single-responsibility API class. Group all endpoints that share
 * an OpenAPI tag into one class. Each class receives the shared [SpaceTradersClient]
 * rather than an [io.ktor.client.HttpClient] directly, so it can choose the correct
 * authentication level (unauthenticated, agent-authenticated, or a one-off token)
 * without knowing how tokens are stored.
 *
 * **In this project:** This class is currently limited to registration. Unlike all
 * other API classes, registration does **not** use the stored AgentToken — it uses
 * a separate AccountToken. The AccountToken is obtained outside the SDK (from the
 * SpaceTraders dashboard) and is never persisted.
 *
 * @param spaceTradersClient The shared SDK client. [SpaceTradersClient.authenticatedWith]
 *   is used here rather than [SpaceTradersClient.authenticated] because the
 *   AccountToken is not stored in the [com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository].
 */
class AccountsApi(private val spaceTradersClient: SpaceTradersClient) {

    /**
     * Registers a new agent linked to the caller's SpaceTraders account.
     *
     * Calls `POST /register` authenticated with the **AccountToken** (not the AgentToken).
     * The SpaceTraders API validates the JWT `sub` claim on this endpoint and returns
     * HTTP 401 with a clear error message if an AgentToken is supplied instead.
     *
     * On success, the response includes:
     * - The new **AgentToken** — a per-agent JWT that must be saved to
     *   [com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository]
     *   for all subsequent gameplay calls.
     * - The agent's starting state: faction, initial contract, starting ships, and
     *   headquarters waypoint.
     *
     * The [accountToken] is used for this single call via
     * [SpaceTradersClient.authenticatedWith] and then discarded — the SDK never stores it.
     *
     * @param symbol The desired call-sign for the new agent (3–14 characters, A–Z 0–9).
     * @param faction The starting faction symbol (e.g. `"COSMIC"`, `"VOID"`).
     *   Determines headquarter system and initial contract type.
     * @param accountToken The AccountToken JWT issued from the SpaceTraders dashboard.
     *   Required for this endpoint only; never used or stored elsewhere in the SDK.
     * @return [RegisterResponseDto] containing the new AgentToken plus the agent's
     *   starting faction, contract, and ship data.
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersApiException
     *   if the symbol is already taken, the faction is invalid, or the AccountToken
     *   is missing / wrong type.
     */
    suspend fun register(symbol: String, faction: String, accountToken: String): RegisterResponseDto =
        // authenticatedWith() builds a one-off client carrying accountToken as the Bearer value.
        // This is the only endpoint in the SDK that requires the AccountToken rather than
        // the stored AgentToken — hence the explicit one-off client rather than client.authenticated.
        spaceTradersClient.authenticatedWith(accountToken).post("register") {
            setBody(RegisterRequestDto(symbol = symbol, faction = faction))
        }.body<ApiResponse<RegisterResponseDto>>().data
}
