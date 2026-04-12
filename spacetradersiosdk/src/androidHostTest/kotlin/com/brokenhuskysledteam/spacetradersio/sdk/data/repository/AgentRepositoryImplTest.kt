package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.AgentDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AgentsApi
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.SpaceTradersDatabase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import com.brokenhuskysledteam.spacetradersio.sdk.testing.createTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class FakeAgentsApi(
    var agentResult: AgentDto = AgentDto(
        accountId = "acc-1",
        symbol = "LADD",
        headquarters = "X1-DF55-20250Z",
        credits = 150000L,
        startingFaction = "COSMIC",
        shipCount = 3
    ),
    var exception: Exception? = null
) : AgentsApi {
    override suspend fun getMyAgent(): AgentDto {
        exception?.let { throw it }
        return agentResult
    }
    override suspend fun getAgent(symbol: String): AgentDto = agentResult
}

class AgentRepositoryImplTest {

    private fun createRepo(
        db: SpaceTradersDatabase = createTestDatabase(),
        api: FakeAgentsApi = FakeAgentsApi()
    ) = AgentRepositoryImpl(api, db) to db

    @Test
    fun observeAgent_emptyDb_emitsNull() = runTest {
        val (repo, _) = createRepo()
        val result = repo.observeAgent().first()
        assertNull(result)
    }

    @Test
    fun refreshAgent_writesToDb_observeEmitsAgent() = runTest {
        val (repo, _) = createRepo()
        repo.refreshAgent()
        val result = repo.observeAgent().first()
        assertEquals("LADD", result?.symbol)
        assertEquals(150000L, result?.credits)
    }

    @Test
    fun refreshAgent_networkFails_dbUnchanged() = runTest {
        val api = FakeAgentsApi()
        val (repo, _) = createRepo(api = api)
        repo.refreshAgent() // seed DB with credits = 150000
        api.exception = RuntimeException("Offline")
        try { repo.refreshAgent() } catch (_: RuntimeException) {}
        val result = repo.observeAgent().first()
        assertEquals(150000L, result?.credits) // pre-existing data unchanged
    }

    @Test
    fun saveAgent_writesToDb() = runTest {
        val (repo, _) = createRepo()
        val agent = Agent("acc-1", "LADD", "X1-DF55-20250Z", 100L, "COSMIC", 1)
        repo.saveAgent(agent)
        val result = repo.observeAgent().first()
        assertEquals(100L, result?.credits)
    }

    @Test
    fun updateCredits_changesOnlyCredits() = runTest {
        val (repo, _) = createRepo()
        repo.refreshAgent()
        repo.updateCredits("LADD", 999L)
        val result = repo.observeAgent().first()
        assertEquals(999L, result?.credits)
        assertEquals(3, result?.shipCount) // unchanged
    }

    @Test
    fun clearAll_emptiesTable() = runTest {
        val (repo, _) = createRepo()
        repo.refreshAgent()
        repo.clearAll()
        val result = repo.observeAgent().first()
        assertNull(result)
    }
}
