package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

// Raw DTO matching the SpaceTraders API "Agent" schema exactly.
// accountId is nullable here because it is only included on your own agent,
// not when fetching other agents via /agents/{symbol}.
@Serializable
data class AgentDto(
    val accountId: String? = null,
    val symbol: String,
    val headquarters: String,
    val credits: Long,
    val startingFaction: String,
    val shipCount: Int
)
