package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.AgentDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent

// DTO → Domain mappers live in the api/mapper package so the domain layer
// stays free of serialization concerns. Each mapper is an extension function
// on its DTO, keeping the conversion co-located with the API types.

fun AgentDto.toDomain(): Agent = Agent(
    accountId = accountId,
    symbol = symbol,
    headquarters = headquarters,
    credits = credits,
    startingFaction = startingFaction,
    shipCount = shipCount
)
