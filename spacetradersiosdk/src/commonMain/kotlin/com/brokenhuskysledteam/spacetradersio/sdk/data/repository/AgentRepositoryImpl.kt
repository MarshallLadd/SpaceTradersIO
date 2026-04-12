package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AgentsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.SpaceTradersDatabase
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.upsertAgent
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.AgentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AgentRepositoryImpl(
    private val agentsApi: AgentsApi,
    private val database: SpaceTradersDatabase
) : AgentRepository {

    private val queries get() = database.agentQueries

    override fun observeAgent(): Flow<Agent?> =
        queries.selectAgent()
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.toDomain() }

    override suspend fun refreshAgent() {
        val agent = agentsApi.getMyAgent().toDomain()
        queries.upsertAgent(agent)
    }

    override suspend fun saveAgent(agent: Agent) {
        queries.upsertAgent(agent)
    }

    override suspend fun updateCredits(symbol: String, credits: Long) {
        queries.updateCredits(credits = credits, symbol = symbol)
    }

    override suspend fun clearAll() {
        queries.deleteAll()
    }
}
