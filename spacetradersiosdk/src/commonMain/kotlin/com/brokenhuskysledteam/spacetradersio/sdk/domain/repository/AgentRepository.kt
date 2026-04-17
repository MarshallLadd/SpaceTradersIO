package com.brokenhuskysledteam.spacetradersio.sdk.domain.repository

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import kotlinx.coroutines.flow.Flow

/**
 * Defines all data operations for the player's agent (character) record.
 *
 * **Pattern:** Repository interface in the domain layer. The interface lives here in `domain/`
 * so that use cases and ViewModels can depend on it without any knowledge of how or where
 * the data is stored. To apply this pattern in a new project: define every data operation
 * your domain needs as a function on this interface, then place the actual storage logic in
 * an `Impl` class inside the `data/` layer.
 *
 * **Benefits of the interface boundary:**
 * - ViewModels depend on `AgentRepository`, not `AgentRepositoryImpl` → tests can supply a
 *   hand-written fake with zero mocking libraries.
 * - The domain layer stays free of `data/` imports; the dependency arrow points inward.
 * - The same interface can be implemented differently for Android, iOS, or test contexts
 *   without touching a single line of domain or UI code.
 *
 * **In this project:** The implementation (`AgentRepositoryImpl`) uses SQLDelight to persist
 * the agent and Ktor to fetch it from `GET /my/agent`. Callers only see this interface and
 * are unaware of those details.
 */
interface AgentRepository {

    /**
     * Returns a hot [Flow] that emits the locally-cached [Agent] record and re-emits whenever
     * the underlying database row changes.
     *
     * **Pattern:** Reactive offline-first observation. The ViewModel collects this flow once
     * and receives all future updates automatically — it never needs to poll. To replicate this
     * pattern, back the flow with a SQLDelight `asFlow().mapToOneOrNull()` query; any write
     * to the table (upsert, update, delete) triggers a new emission automatically.
     *
     * **In this project:** The implementation maps a SQLDelight `AgentQueries.selectAgent()`
     * query into a `Flow<Agent?>` using `Dispatchers.Default` to keep DB reads off the main
     * thread.
     *
     * @return A [Flow] that emits `null` when no agent row exists in the database (e.g. before
     *   the player has registered or after [clearAll] is called), or the current [Agent] once
     *   one has been saved.
     */
    fun observeAgent(): Flow<Agent?>

    /**
     * Fetches the authenticated player's agent from the network and writes the result to the
     * local database.
     *
     * **Pattern:** Network-then-cache write. Callers do not receive a return value; instead,
     * [observeAgent] automatically emits the new value to all active collectors once the DB
     * write completes. This keeps the data flow strictly unidirectional: network → DB → Flow.
     *
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.domain.error.SpaceTradersApiException
     *   if the API call fails (e.g. invalid token, network error).
     */
    suspend fun refreshAgent()

    /**
     * Writes an [Agent] record directly to the local database without a network call.
     *
     * This is the fast path used immediately after registration: the `POST /register` response
     * already includes the full agent, so there is no need to make a second round-trip to
     * `GET /my/agent`.
     *
     * @param agent The [Agent] to persist.
     */
    suspend fun saveAgent(agent: Agent)

    /**
     * Updates only the credit balance for the agent identified by [symbol].
     *
     * **Why a partial update exists:** Many SpaceTraders API responses return updated credit
     * totals without returning the full agent object. Patching only the credits column avoids
     * a redundant network fetch while still keeping [observeAgent] collectors up to date.
     *
     * @param symbol The agent's call sign (primary key in the local database).
     * @param credits The new credit balance to store.
     */
    suspend fun updateCredits(symbol: String, credits: Long)

    /**
     * Deletes all agent data from the local database.
     *
     * Called during logout to ensure no player data persists after the session ends. After this
     * call, [observeAgent] will emit `null` until a new agent is fetched or saved.
     */
    suspend fun clearAll()
}
