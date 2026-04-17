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

/**
 * Concrete implementation of [AgentRepository] using the offline-first reactive pattern.
 *
 * **Pattern:** Offline-first reactive repository. The exposed [Flow] is driven directly
 * by a SQLDelight database query rather than by in-memory state. Any method that writes
 * to the database (network fetch, credit update, etc.) automatically propagates to all
 * active observers — the ViewModel never needs to be manually notified of changes. To
 * apply this pattern in a new project: (1) expose a [Flow] wired to `query.asFlow()`,
 * (2) make every mutation write to the database, and (3) let the database-to-flow
 * pipeline deliver the update.
 *
 * **In this project:** The single agent row represents the currently authenticated player.
 * It is written on login (via [saveAgent]) and refreshed on demand (via [refreshAgent]).
 * Credit balance is updated surgically after trade/contract actions (via [updateCredits])
 * to avoid a redundant network round-trip.
 *
 * @param agentsApi  Network layer for the SpaceTraders `/my/agent` endpoint.
 * @param database   SQLDelight database holding the `agent` table.
 */
class AgentRepositoryImpl(
    private val agentsApi: AgentsApi,
    private val database: SpaceTradersDatabase
) : AgentRepository {

    // Shorthand accessor — avoids repeating `database.agentQueries` throughout.
    private val queries get() = database.agentQueries

    /**
     * Returns a [Flow] that emits the current agent row whenever it changes in the database.
     *
     * The flow is created by SQLDelight's `asFlow()` extension on the generated query
     * object. `mapToOneOrNull` converts each raw query result into either a domain [Agent]
     * or `null` (if the table is empty, e.g. before login or after logout). The downstream
     * `.map { it?.toDomain() }` translates the SQLDelight-generated DB entity into the
     * project's domain model.
     *
     * **KMP note:** `Dispatchers.Default` is required here — `Dispatchers.IO` is a
     * JVM-only dispatcher that does not exist in `commonMain`. On iOS, `Dispatchers.Default`
     * dispatches to the shared thread pool, which is the appropriate substitute.
     *
     * @return A cold [Flow] backed by the SQLDelight query; emits on every DB write
     *   that touches the `agent` table.
     */
    override fun observeAgent(): Flow<Agent?> =
        queries.selectAgent()
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default) // Dispatchers.IO is JVM-only; Default works on all KMP targets.
            .map { it?.toDomain() }

    /**
     * Fetches the current agent from the network and persists it to the database.
     *
     * Calling this triggers the pipeline: network → [saveAgent] → DB write →
     * [observeAgent] flow emits updated value. The ViewModel does not need to explicitly
     * read the return value; it receives the update through the [observeAgent] flow.
     */
    override suspend fun refreshAgent() {
        val agent = agentsApi.getMyAgent().toDomain()
        // Write to DB; the observeAgent() flow picks up the change automatically.
        queries.upsertAgent(agent)
    }

    /**
     * Persists [agent] to the database without making a network call.
     *
     * Used during the registration/login flow when the API has already returned the
     * initial agent data and a separate network round-trip is unnecessary.
     *
     * The underlying SQL is an upsert (INSERT OR REPLACE), supplied by the
     * `upsertAgent` extension function defined in `AgentDbMapper.kt`.
     *
     * @param agent The [Agent] to write. The `symbol` column acts as the primary key.
     */
    override suspend fun saveAgent(agent: Agent) {
        queries.upsertAgent(agent)
    }

    /**
     * Updates only the credit balance for the agent identified by [symbol].
     *
     * SpaceTraders action endpoints (market purchases, contract payouts, etc.) return
     * the updated credit total directly in the response. This partial update applies
     * that change to the database without re-fetching the full agent, keeping the local
     * state in sync at minimal network cost.
     *
     * @param symbol  The unique agent callsign (primary key in the `agent` table).
     * @param credits The new credit balance reported by the API.
     */
    override suspend fun updateCredits(symbol: String, credits: Long) {
        queries.updateCredits(credits = credits, symbol = symbol)
    }

    /**
     * Deletes all rows from the `agent` table.
     *
     * Called during logout to ensure no player data persists after the session ends.
     * The [observeAgent] flow will emit `null` immediately after this call.
     */
    override suspend fun clearAll() {
        queries.deleteAll()
    }
}
