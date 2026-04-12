package com.brokenhuskysledteam.spacetradersio.sdk.domain.repository

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import kotlinx.coroutines.flow.Flow

interface AgentRepository {
    fun observeAgent(): Flow<Agent?>
    suspend fun refreshAgent()
    suspend fun saveAgent(agent: Agent)
    suspend fun updateCredits(symbol: String, credits: Long)
    suspend fun clearAll()
}
