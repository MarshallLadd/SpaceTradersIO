package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

// Domain model for a SpaceTraders agent (player character).
// This is the UI-facing representation — free of serialization annotations.
// accountId is null when viewing another agent via the public /agents endpoint
// (the API only includes it for the authenticated agent's own data).
data class Agent(
    val accountId: String?,
    val symbol: String,
    val headquarters: String,
    val credits: Long,
    val startingFaction: String,
    val shipCount: Int
)
