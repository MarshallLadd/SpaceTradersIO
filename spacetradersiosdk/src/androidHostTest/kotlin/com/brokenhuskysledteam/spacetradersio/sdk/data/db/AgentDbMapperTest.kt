package com.brokenhuskysledteam.spacetradersio.sdk.data.db

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import com.brokenhuskysledteam.spacetradersio.sdk.testing.createTestDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentDbMapperTest {

    private val testAgent = Agent(
        accountId = "acc-123",
        symbol = "LADD",
        headquarters = "X1-DF55-20250Z",
        credits = 150000L,
        startingFaction = "COSMIC",
        shipCount = 3
    )

    @Test
    fun roundTrip_upsertThenSelect_returnsEqualAgent() {
        val db = createTestDatabase()
        db.agentQueries.upsertAgent(testAgent)
        val result = db.agentQueries.selectAgent().executeAsOneOrNull()?.toDomain()
        assertEquals(testAgent, result)
    }

    @Test
    fun roundTrip_nullAccountId_preservesNull() {
        val db = createTestDatabase()
        val agentNoAccount = testAgent.copy(accountId = null)
        db.agentQueries.upsertAgent(agentNoAccount)
        val result = db.agentQueries.selectAgent().executeAsOneOrNull()?.toDomain()
        assertNull(result?.accountId)
    }

    @Test
    fun upsert_sameSymbol_replacesExisting() {
        val db = createTestDatabase()
        db.agentQueries.upsertAgent(testAgent)
        val updated = testAgent.copy(credits = 200000L)
        db.agentQueries.upsertAgent(updated)
        val result = db.agentQueries.selectAgent().executeAsOneOrNull()?.toDomain()
        assertEquals(200000L, result?.credits)
    }

    @Test
    fun updateCredits_changesOnlyCreditsField() {
        val db = createTestDatabase()
        db.agentQueries.upsertAgent(testAgent)
        db.agentQueries.updateCredits(credits = 999L, symbol = "LADD")
        val result = db.agentQueries.selectAgent().executeAsOneOrNull()?.toDomain()
        assertEquals(999L, result?.credits)
        assertEquals("X1-DF55-20250Z", result?.headquarters) // headquarters unchanged
    }

    @Test
    fun deleteAll_emptiesTable() {
        val db = createTestDatabase()
        db.agentQueries.upsertAgent(testAgent)
        db.agentQueries.deleteAll()
        val result = db.agentQueries.selectAgent().executeAsOneOrNull()
        assertNull(result)
    }

    @Test
    fun selectAgent_emptyTable_returnsNull() {
        val db = createTestDatabase()
        val result = db.agentQueries.selectAgent().executeAsOneOrNull()
        assertNull(result)
    }
}
