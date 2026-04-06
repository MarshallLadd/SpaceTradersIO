package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.testing.FakeTokenRepository
import com.brokenhuskysledteam.spacetradersio.sdk.testing.buildMockSpaceTradersClient
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val REGISTER_RESPONSE = """
{
  "data": {
    "token": "agent-bearer-token",
    "agent": {
      "accountId": "acc-abc123",
      "symbol": "TEST_AGENT",
      "headquarters": "X1-DF55-20250Z",
      "credits": 175000,
      "startingFaction": "COSMIC",
      "shipCount": 2
    },
    "faction": {
      "symbol": "COSMIC",
      "name": "Cosmic Engineers",
      "description": "A faction.",
      "headquarters": null,
      "traits": [],
      "isRecruiting": true
    },
    "contract": {
      "id": "contract-1",
      "factionSymbol": "COSMIC",
      "type": "PROCUREMENT",
      "terms": {
        "deadline": "2025-06-15T12:00:00.000Z",
        "payment": { "onAccepted": 1000, "onFulfilled": 5000 },
        "deliver": []
      },
      "accepted": false,
      "fulfilled": false,
      "expiration": "2025-05-01T00:00:00.000Z",
      "deadlineToAccept": "2025-05-10T00:00:00.000Z"
    },
    "ships": []
  }
}
"""

class AccountsApiTest {

    private var capturedAuthHeader: String? = null

    private fun buildApi(): AccountsApi {
        val client = buildMockSpaceTradersClient(
            tokenRepository = FakeTokenRepository(storedToken = null)
        ) { request ->
            capturedAuthHeader = request.headers[HttpHeaders.Authorization]
            respond(
                content = REGISTER_RESPONSE,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        return AccountsApi(client)
    }

    @Test
    fun register_sendsAccountTokenAsAuthorizationHeader() = runTest {
        buildApi().register("TEST_AGENT", "COSMIC", "my-account-token")
        assertEquals("Bearer my-account-token", capturedAuthHeader)
    }

    @Test
    fun register_returnsAgentToken() = runTest {
        val result = buildApi().register("TEST_AGENT", "COSMIC", "my-account-token")
        assertEquals("agent-bearer-token", result.token)
    }

    @Test
    fun register_returnsAgentSymbol() = runTest {
        val result = buildApi().register("TEST_AGENT", "COSMIC", "my-account-token")
        assertEquals("TEST_AGENT", result.agent.symbol)
    }
}
