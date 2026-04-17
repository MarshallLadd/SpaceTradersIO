package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

/**
 * Wire-format representation of the SpaceTraders API `Agent` schema.
 *
 * **Pattern:** Data Transfer Object (DTO). In any project that calls a REST API, create a
 * dedicated `@Serializable` data class whose shape matches the JSON exactly. Keep it in an
 * `api/dto/` package and never let it leak into business logic. A mapper (`AgentMapper.kt`)
 * translates this type into the framework-free domain model ([com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent])
 * that the rest of the codebase uses. This separation means an API field rename or a new
 * nullable property only ever touches the DTO and the mapper — not use cases, ViewModels,
 * or UI.
 *
 * **In this project:** `AgentDto` is returned by `GET /my/agent` (your own agent) and
 * `GET /agents/{symbol}` (any public agent). It is also embedded in registration and
 * contract-action responses. The mapper lives at `api/mapper/AgentMapper.kt`.
 *
 * @property accountId The platform-level account UUID. Present only when the response
 *   describes the authenticated player's own agent; `null` for all other agents fetched
 *   via the public `/agents/{symbol}` endpoint. Modelled as nullable here so the DTO
 *   faithfully represents what the API actually sends — the domain model propagates the
 *   same nullability so callers can distinguish "my agent" from "another player's agent".
 * @property symbol The agent's unique callsign (e.g. `"COSMIC_HAULER"`). Acts as the
 *   display name and stable identifier across all API endpoints.
 * @property headquarters The waypoint symbol of the agent's starting base
 *   (e.g. `"X1-OE-PM"`), in `<system>-<waypoint>` format.
 * @property credits The agent's current credit balance. `Long` rather than `Int` because
 *   late-game balances can exceed `Int.MAX_VALUE` (~2.1 billion credits).
 * @property startingFaction The faction chosen at registration (e.g. `"COSMIC"`).
 *   Determines the agent's starting location and available early contracts.
 * @property shipCount Total number of ships currently owned by this agent. Included in
 *   the agent summary so the UI can show a ship count without loading the full fleet.
 */
@Serializable
data class AgentDto(
    val accountId: String? = null,
    val symbol: String,
    val headquarters: String,
    val credits: Long,
    val startingFaction: String,
    val shipCount: Int
)
