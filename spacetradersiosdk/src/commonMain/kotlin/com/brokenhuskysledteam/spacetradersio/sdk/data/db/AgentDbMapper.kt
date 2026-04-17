package com.brokenhuskysledteam.spacetradersio.sdk.data.db

import com.brokenhuskysledteam.spacetradersio.sdk.data.db.Agent as DbAgent
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent

/**
 * Converts a SQLDelight-generated [DbAgent] row into an [Agent] domain model.
 *
 * **Pattern:** DB mapper (storage → domain). Every offline-first feature needs two
 * parallel mapper paths that converge on the same domain type: one that reads from
 * the network DTO (`AgentDto.toDomain()` in `api/mapper/`) and one that reads from
 * the local database (this function). Keeping both paths in separate files makes it
 * easy to update one without touching the other.
 *
 * **In this project:** `AgentDto.toDomain()` is called immediately after an API
 * response is parsed. `DbAgent.toDomain()` is called when the repository serves
 * data from the SQLDelight cache. The [Agent] type is identical in both cases; the
 * domain layer never knows which source was used.
 *
 * **Why two mappers for one domain type?**
 * The DTO and the DB row are structurally independent. Column names and types are
 * dictated by the `.sq` schema (e.g. `ship_count` as `Long`); DTO field names are
 * dictated by the JSON key casing. Separate mapper files let both evolve without
 * coupling the database schema to the network contract.
 */
fun DbAgent.toDomain(): Agent = Agent(
    accountId = account_id,
    symbol = symbol,
    headquarters = headquarters,
    credits = credits,
    startingFaction = starting_faction,
    // SQLite has no native integer-size distinction; SQLDelight stores all INTEGER
    // columns as Long. Convert back to Int to match the domain model's type.
    shipCount = ship_count.toInt()
)

/**
 * Upserts [agent] into the database using the SQLDelight-generated [AgentQueries].
 *
 * **Pattern:** Queries extension for upsert mapping (domain → storage). Rather than
 * placing the domain-to-column translation inside the repository, push it into the
 * mapper layer as an extension on the generated Queries class. The repository then
 * reads as a single call (`agentQueries.upsertAgent(agent)`), and all field-mapping
 * logic is co-located with `DbAgent.toDomain()` in this file — the two directions of
 * the same mapping concern live together.
 *
 * **In this project:** Called by `AgentRepository` whenever a fresh [Agent] arrives
 * from the API. SQLDelight's generated `upsertAgent` SQL uses `INSERT OR REPLACE`,
 * so the first call creates the row and subsequent calls overwrite it.
 *
 * @param agent The domain model to persist.
 */
fun AgentQueries.upsertAgent(agent: Agent) {
    upsertAgent(
        symbol = agent.symbol,
        account_id = agent.accountId,
        headquarters = agent.headquarters,
        credits = agent.credits,
        starting_faction = agent.startingFaction,
        // Domain model holds Int; SQLDelight expects Long for INTEGER columns.
        ship_count = agent.shipCount.toLong()
    )
}
