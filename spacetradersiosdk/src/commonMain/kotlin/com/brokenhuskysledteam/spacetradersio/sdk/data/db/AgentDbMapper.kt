package com.brokenhuskysledteam.spacetradersio.sdk.data.db

import com.brokenhuskysledteam.spacetradersio.sdk.data.db.Agent as DbAgent
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent

fun DbAgent.toDomain(): Agent = Agent(
    accountId = account_id,
    symbol = symbol,
    headquarters = headquarters,
    credits = credits,
    startingFaction = starting_faction,
    shipCount = ship_count.toInt()
)

fun AgentQueries.upsertAgent(agent: Agent) {
    upsertAgent(
        symbol = agent.symbol,
        account_id = agent.accountId,
        headquarters = agent.headquarters,
        credits = agent.credits,
        starting_faction = agent.startingFaction,
        ship_count = agent.shipCount.toLong()
    )
}
