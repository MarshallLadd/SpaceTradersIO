package com.brokenhuskysledteam.spacetraders.api.mapper

import com.brokenhuskysledteam.spacetraders.api.dto.AgentDto
import com.brokenhuskysledteam.spacetraders.domain.model.Agent

fun AgentDto.toDomain(): Agent = Agent(
    accountId = accountId,
    symbol = symbol,
    headquarters = headquarters,
    credits = credits,
    startingFaction = startingFaction,
    shipCount = shipCount
)
