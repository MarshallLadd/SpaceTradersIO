package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

/**
 * Wire-format representation of the SpaceTraders API `Faction` schema.
 *
 * **Pattern:** Data Transfer Object (DTO). DTOs are `@Serializable` data classes that carry
 * the API wire format. A mapper converts Dto → Domain at the API boundary, keeping
 * serialization concerns out of business logic. In a new project, create one DTO per
 * API response/request schema and a corresponding mapper.
 *
 * **In this project:** `FactionDto` is embedded in [RegisterResponseDto] (the agent's
 * starting faction) and returned by `GET /factions` (paginated list) and
 * `GET /factions/{symbol}` (single faction). The player's choice of faction at registration
 * determines their starting star system, first waypoint, and initial contract giver. The
 * mapper lives at `api/mapper/FactionMapper.kt`.
 *
 * @property symbol The faction's unique identifier (e.g. `"COSMIC"`, `"VOID"`). Used as
 *   the `faction` field in [RegisterRequestDto] when registering a new agent.
 * @property name The faction's human-readable display name (e.g. `"Cosmic Engineers"`).
 * @property description Flavour text explaining the faction's lore and focus.
 * @property headquarters The waypoint symbol of the faction's home base. `null` for some
 *   minor or hidden factions that do not publicly disclose their headquarters location in
 *   the API response.
 * @property traits The list of gameplay-relevant attributes describing this faction's
 *   specialisation. See [FactionTraitDto].
 * @property isRecruiting `true` when new agents may register with this faction. Factions
 *   with `isRecruiting = false` should be hidden from the registration UI. Note: the JSON
 *   key is `isRecruiting` (camelCase), which matches the Kotlin property name directly —
 *   no `@SerialName` annotation is required.
 */
@Serializable
data class FactionDto(
    val symbol: String,
    val name: String,
    val description: String,
    // Nullable because minor/hidden factions do not expose their HQ in the API.
    val headquarters: String? = null,
    val traits: List<FactionTraitDto>,
    val isRecruiting: Boolean
)

/**
 * A single trait describing a faction's gameplay specialisation.
 *
 * **Pattern:** List-item DTO. When a parent DTO contains a list of structured sub-items,
 * each item gets its own DTO type. This keeps the parent class cohesive and makes the
 * mapper easy to extend if trait-level computed properties are needed later.
 *
 * **In this project:** Traits are purely informational — they are displayed in the faction
 * detail screen and in the registration flow so players can make an informed faction
 * choice. The mapper converts the list to `List<FactionTrait>` in the domain model.
 *
 * @property symbol The stable trait identifier (e.g. `"INNOVATIVE"`, `"BUREAUCRATIC"`).
 *   Used as a key for icon or colour lookups in the UI if needed.
 * @property name The trait's short display name (e.g. `"Innovative"`).
 * @property description Longer flavour text explaining what the trait means in gameplay.
 */
@Serializable
data class FactionTraitDto(
    val symbol: String,
    val name: String,
    val description: String
)
