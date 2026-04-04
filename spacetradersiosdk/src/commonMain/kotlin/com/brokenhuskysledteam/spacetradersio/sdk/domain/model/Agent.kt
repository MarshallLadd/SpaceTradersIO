package com.brokenhuskysledteam.spacetraders.domain.model

// Represents the authenticated player's agent.
// accountId is null when viewing another agent via the public /agents endpoint.
data class Agent(
    val accountId: String?,
    val symbol: String,
    val headquarters: String,
    val credits: Long,
    val startingFaction: String,
    val shipCount: Int
)
