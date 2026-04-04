package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

// Raw DTO matching the SpaceTraders API "Faction" schema.
// headquarters is nullable because some factions have no public HQ.
@Serializable
data class FactionDto(
    val symbol: String,
    val name: String,
    val description: String,
    val headquarters: String? = null,
    val traits: List<FactionTraitDto>,
    val isRecruiting: Boolean
)

@Serializable
data class FactionTraitDto(
    val symbol: String,
    val name: String,
    val description: String
)
