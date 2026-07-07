package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import app.cash.turbine.test
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ContractsApi
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractTab
import com.brokenhuskysledteam.spacetradersio.sdk.testing.FakeTokenRepository
import com.brokenhuskysledteam.spacetradersio.sdk.testing.buildMockSpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.testing.createTestDatabase
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContractRepositoryImplTest {

    private val contractListJson = """
        {
          "data": [
            {
              "id": "C-001",
              "factionSymbol": "COSMIC",
              "type": "PROCUREMENT",
              "terms": {
                "deadline": "2099-01-01T00:00:00.000Z",
                "payment": { "onAccepted": 100, "onFulfilled": 900 },
                "deliver": [
                  { "tradeSymbol": "IRON_ORE", "destinationSymbol": "X1-A-001",
                    "unitsRequired": 50, "unitsFulfilled": 0 }
                ]
              },
              "accepted": false,
              "fulfilled": false,
              "expiration": "2099-01-01T00:00:00.000Z",
              "deadlineToAccept": "2099-01-01T00:00:00.000Z"
            }
          ],
          "meta": { "total": 1, "page": 1, "limit": 10 }
        }
    """.trimIndent()

    private val acceptResponseJson = """
        {
          "data": {
            "contract": {
              "id": "C-001",
              "factionSymbol": "COSMIC",
              "type": "PROCUREMENT",
              "terms": {
                "deadline": "2099-01-01T00:00:00.000Z",
                "payment": { "onAccepted": 100, "onFulfilled": 900 },
                "deliver": []
              },
              "accepted": true,
              "fulfilled": false,
              "expiration": "2099-01-01T00:00:00.000Z",
              "deadlineToAccept": "2099-01-01T00:00:00.000Z"
            },
            "agent": { "accountId": "ACC-1", "symbol": "AGENT", "headquarters": "X1-A",
              "credits": 200, "startingFaction": "COSMIC", "shipCount": 1 }
          }
        }
    """.trimIndent()

    private val fulfillResponseJson = """
        {
          "data": {
            "contract": {
              "id": "C-001",
              "factionSymbol": "COSMIC",
              "type": "PROCUREMENT",
              "terms": {
                "deadline": "2099-01-01T00:00:00.000Z",
                "payment": { "onAccepted": 100, "onFulfilled": 900 },
                "deliver": []
              },
              "accepted": true,
              "fulfilled": true,
              "expiration": "2099-01-01T00:00:00.000Z",
              "deadlineToAccept": "2099-01-01T00:00:00.000Z"
            },
            "agent": { "accountId": "ACC-1", "symbol": "AGENT", "headquarters": "X1-A",
              "credits": 1100, "startingFaction": "COSMIC", "shipCount": 1 }
          }
        }
    """.trimIndent()

    private fun MockRequestHandleScope.okJson(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    )

    private fun buildRepo(vararg responses: String): ContractRepositoryImpl {
        val responseQueue = ArrayDeque(responses.toList())
        val client = buildMockSpaceTradersClient(
            tokenRepository = FakeTokenRepository()
        ) { okJson(responseQueue.removeFirst()) }
        val api = ContractsApi(client)
        val database = createTestDatabase()
        return ContractRepositoryImpl(api, database)
    }

    @Test
    fun refreshContracts_returns_correct_meta() = runTest {
        val repo = buildRepo(contractListJson)
        val meta = repo.refreshContracts(page = 1, limit = 10)
        assertEquals(1, meta.total)
        assertEquals(1, meta.page)
        assertEquals(10, meta.limit)
    }

    @Test
    fun refreshContracts_upserts_deliver_goods_into_db() = runTest {
        val repo = buildRepo(contractListJson)
        repo.refreshContracts(1, 10)
        repo.observeContracts(ContractTab.ACTIVE, limit = 10L, offset = 0L).test {
            val contracts = awaitItem()
            assertEquals(1, contracts.size)
            assertEquals("C-001", contracts[0].id)
            assertEquals(1, contracts[0].terms.deliverGoods.size)
            assertEquals("IRON_ORE", contracts[0].terms.deliverGoods[0].tradeSymbol)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeContracts_active_tab_returns_unaccepted_contracts() = runTest {
        val repo = buildRepo(contractListJson)
        repo.refreshContracts(1, 10)
        repo.observeContracts(ContractTab.ACTIVE, limit = 10L, offset = 0L).test {
            val contracts = awaitItem()
            assertEquals(1, contracts.size)
            assertEquals(ContractStatus.UNACCEPTED, contracts[0].status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeContracts_history_tab_returns_empty_when_no_history() = runTest {
        val repo = buildRepo(contractListJson)
        repo.refreshContracts(1, 10)
        repo.observeContracts(ContractTab.HISTORY, limit = 10L, offset = 0L).test {
            val contracts = awaitItem()
            assertTrue(contracts.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun acceptContract_updates_db_and_returns_active_contract() = runTest {
        val repo = buildRepo(contractListJson, acceptResponseJson)
        repo.refreshContracts(1, 10)
        val accepted = repo.acceptContract("C-001")
        assertEquals(true, accepted.accepted)
        assertEquals(ContractStatus.ACTIVE, accepted.status)
        repo.observeContracts(ContractTab.ACTIVE, 10L, 0L).test {
            val contracts = awaitItem()
            assertEquals(1, contracts.size)
            assertEquals(true, contracts[0].accepted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun fulfillContract_updates_db_and_returns_fulfilled_contract() = runTest {
        val repo = buildRepo(contractListJson, acceptResponseJson, fulfillResponseJson)
        repo.refreshContracts(1, 10)
        repo.acceptContract("C-001")
        val fulfilled = repo.fulfillContract("C-001")
        assertEquals(true, fulfilled.fulfilled)
        assertEquals(ContractStatus.FULFILLED, fulfilled.status)
        repo.observeContracts(ContractTab.HISTORY, 10L, 0L).test {
            val contracts = awaitItem()
            assertEquals(1, contracts.size)
            assertEquals(ContractStatus.FULFILLED, contracts[0].status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun upsertContract_emits_on_observation_flow() = runTest {
        val repo = buildRepo(contractListJson)
        repo.refreshContracts(1, 10)
        repo.observeContracts(ContractTab.ACTIVE, 10L, 0L).test {
            val initial = awaitItem()
            assertEquals(1, initial.size)
            // Modify and upsert — should re-emit
            val updated = initial[0].copy(accepted = true,
                status = com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractStatus.ACTIVE)
            repo.upsertContract(updated)
            val afterUpdate = awaitItem()
            assertEquals(1, afterUpdate.size)
            assertEquals(true, afterUpdate[0].accepted)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
