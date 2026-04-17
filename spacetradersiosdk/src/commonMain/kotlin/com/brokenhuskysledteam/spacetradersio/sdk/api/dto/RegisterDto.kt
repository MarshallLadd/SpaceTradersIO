package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

/**
 * Wire-format request body for `POST /register` — the only unauthenticated endpoint.
 *
 * **Pattern:** Request body DTO for an unauthenticated endpoint. In a new project, model the
 * request body as a typed DTO even for public endpoints. The serializer ensures field names
 * match the API contract, and the constructor makes required vs. optional fields explicit.
 *
 * **In this project:** Registration requires a callsign (`symbol`) and a starting faction.
 * The API validates that `faction` is one of the enumerated faction symbols (e.g. `"COSMIC"`,
 * `"VOID"`). `email` is intentionally absent from this DTO — the SpaceTraders API accepts it
 * but it is optional and not used by this application.
 *
 * @property symbol The desired agent callsign (2–14 characters, alphanumeric + hyphens).
 * Displayed as the agent's name in-game.
 * @property faction The symbol of the starting faction. Determines the agent's starting
 * system and first waypoint.
 */
@Serializable
data class RegisterRequestDto(
    val symbol: String,
    val faction: String
)

/**
 * Wire-format response from `POST /register`.
 *
 * **Pattern:** Bootstrap response DTO. The register endpoint is a special case: it returns
 * the complete initial game state in a single response so the client can start playing
 * immediately without follow-up requests. In a new project, where a "bootstrap" endpoint
 * bootstraps multiple resources at once, model the full bundle as a single response DTO and
 * let the mapper distribute the pieces to their respective domain models or state stores.
 *
 * **In this project:** Registration is the entry point for new players. The response provides
 * everything needed to begin the game: a bearer token for all subsequent authenticated calls,
 * the new agent's profile, their starting faction, their first contract, and their starting
 * ships. The `AccountsApi.register()` function maps this DTO to a `RegisterResult` domain
 * model that the `AuthViewModel` uses to persist the token and seed the initial app state.
 *
 * **Auth note:** `POST /register` must be called with an `AccountToken` (not an `AgentToken`).
 * The API validates the JWT `sub` claim and returns a clear error if the wrong token type is
 * sent. The resulting `token` field in this response is the `AgentToken` used for all gameplay
 * requests after registration.
 *
 * @property token The agent's bearer token (JWT). Store this securely — it is required in
 * the `Authorization: Bearer <token>` header for every authenticated API call.
 * @property agent The newly created agent, including the callsign, faction, headquarters
 * waypoint, and starting credit balance.
 * @property faction The agent's starting faction, including its name, description, and
 * headquarters system.
 * @property contract The agent's first contract, offered by the starting faction. Completing
 * it is the recommended first goal for new players.
 * @property ships The list of ships the agent starts with. Typically one starter ship at the
 * faction's headquarters waypoint.
 */
@Serializable
data class RegisterResponseDto(
    val token: String,
    val agent: AgentDto,
    val faction: FactionDto,
    val contract: ContractDto,
    val ships: List<ShipDto>
)
