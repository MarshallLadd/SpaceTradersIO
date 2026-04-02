package com.brokenhuskysledteam.spacetraders.domain.usecase

import com.brokenhuskysledteam.spacetraders.api.endpoints.ContractsApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val ACCEPT_RESPONSE = """
{
  "data": {
    "contract": {
      "id": "contract-xyz",
      "factionSymbol": "COSMIC",
      "type": "PROCUREMENT",
      "terms": {
        "deadline": "2025-07-01T00:00:00.000Z",
        "payment": { "onAccepted": 2000, "onFulfilled": 10000 },
        "deliver": []
      },
      "accepted": true,
      "fulfilled": false,
      "expiration": "2025-05-15T00:00:00.000Z",
      "deadlineToAccept": "2025-05-20T00:00:00.000Z"
    },
    "agent": {
      "accountId": "acc-1",
      "symbol": "COMMANDER",
      "headquarters": "X1-DF55-20250Z",
      "credits": 98000,
      "startingFaction": "COSMIC",
      "shipCount": 1
    }
  }
}
"""

class AcceptContractUseCaseTest {

    private fun buildUseCase(): AcceptContractUseCase {
        val engine = MockEngine { respond(
            content = ACCEPT_RESPONSE,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        )}
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { contentType(ContentType.Application.Json) }
        }
        return AcceptContractUseCase(ContractsApi(client))
    }

    @Test
    fun invoke_returnsContractWithAcceptedTrue() = runTest {
        val contract = buildUseCase().invoke("contract-xyz")
        assertTrue(contract.accepted)
    }

    @Test
    fun invoke_returnsContractWithCorrectId() = runTest {
        val contract = buildUseCase().invoke("contract-xyz")
        assertEquals("contract-xyz", contract.id)
    }
}
