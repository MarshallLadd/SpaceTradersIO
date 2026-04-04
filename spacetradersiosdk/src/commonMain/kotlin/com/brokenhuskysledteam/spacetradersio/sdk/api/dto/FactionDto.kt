package com.brokenhuskysledteam.spacetraders.api.dto

import kotlinx.serialization.Serializable

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
