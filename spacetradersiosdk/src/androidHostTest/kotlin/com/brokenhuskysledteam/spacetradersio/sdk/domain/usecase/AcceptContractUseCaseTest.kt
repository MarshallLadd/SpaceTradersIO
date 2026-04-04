package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ContractsApi
import com.brokenhuskysledteam.spacetradersio.sdk.testing.buildMockSpaceTradersClient
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
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
        val client = buildMockSpaceTradersClient { respond(
            content = ACCEPT_RESPONSE,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        )}
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
