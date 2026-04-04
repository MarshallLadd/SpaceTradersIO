package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.AgentDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent

fun AgentDto.toDomain(): Agent = Agent(
    accountId = accountId,
    symbol = symbol,
    headquarters = headquarters,
    credits = credits,
    startingFaction = startingFaction,
    shipCount = shipCount
)
