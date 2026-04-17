package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

/**
 * Represents the authenticated player's in-game character (or a publicly visible agent).
 *
 * **Pattern:** Immutable domain model. In any project that separates API concerns from
 * business logic, create a plain `data class` like this — no `@Serializable`, no Android
 * imports — so the model can be used in `commonMain` and shared across all platforms.
 * Mappers in `api/mapper/` translate the wire DTO into this type; the rest of the codebase
 * never touches the DTO directly.
 *
 * **In this project:** `Agent` is the central identity object. It is returned by the register
 * and login flows and stored in [com.brokenhuskysledteam.spacetradersio.sdk.data.AgentRepository],
 * then observed by the Dashboard ViewModel to drive the HUD display.
 *
 * @property accountId The account-level identifier. `null` when this object represents
 *   another player's agent viewed through the public `/agents` endpoint — the SpaceTraders
 *   API only includes this field in the authenticated agent's own response.
 * @property symbol The agent's unique callsign (e.g. `"COSMIC_HAULER"`). Used as the
 *   display name throughout the UI and as a stable identifier in log messages.
 * @property headquarters The waypoint symbol of the agent's home base
 *   (e.g. `"X1-OE-PM"`). Format is `<system>-<waypoint>`.
 * @property credits The agent's current credit balance. Stored as `Long` because
 *   late-game credit counts can exceed `Int.MAX_VALUE` (~2.1 billion).
 * @property startingFaction The faction the agent joined at registration
 *   (e.g. `"COSMIC"`). Determines starting location and early mission availability.
 * @property shipCount The total number of ships currently owned by the agent.
 *   Cached here so the UI can display it without loading the full ship list.
 */
data class Agent(
    val accountId: String?,
    val symbol: String,
    val headquarters: String,
    // Long instead of Int: credits can exceed 2^31 in late-game play.
    val credits: Long,
    val startingFaction: String,
    val shipCount: Int
)
