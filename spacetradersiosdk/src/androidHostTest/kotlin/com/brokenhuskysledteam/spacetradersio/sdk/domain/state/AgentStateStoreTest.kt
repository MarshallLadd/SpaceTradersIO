package com.brokenhuskysledteam.spacetradersio.sdk.domain.state

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentStateStoreTest {

    private fun testAgent(credits: Long = 100000L) = Agent(
        accountId = "acc-1",
        symbol = "LADD",
        headquarters = "X1-DF55-20250Z",
        credits = credits,
        startingFaction = "COSMIC",
        shipCount = 2
    )

    @Test
    fun initialAgentIsNull() {
        val store = AgentStateStore()
        assertNull(store.agent.value)
    }

    @Test
    fun updateSetsAgent() {
        val store = AgentStateStore()
        val agent = testAgent()
        store.update(agent)
        assertEquals(agent, store.agent.value)
    }

    @Test
    fun updateReplacesAgent() {
        val store = AgentStateStore()
        store.update(testAgent(credits = 100L))
        store.update(testAgent(credits = 200L))
        assertEquals(200L, store.agent.value?.credits)
    }

    @Test
    fun clearSetsAgentToNull() {
        val store = AgentStateStore()
        store.update(testAgent())
        store.clear()
        assertNull(store.agent.value)
    }
}
