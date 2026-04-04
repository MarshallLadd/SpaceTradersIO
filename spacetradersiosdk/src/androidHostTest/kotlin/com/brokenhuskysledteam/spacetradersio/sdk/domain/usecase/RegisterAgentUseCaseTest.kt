package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AccountsApi
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.FactionSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository
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

// Hand-written fake — avoids mockk which is not yet a dependency.
private class FakeTokenRepository : TokenRepository {
    var savedToken: String? = null
    override fun getToken(): String? = savedToken
    override fun saveToken(token: String) { savedToken = token }
    override fun clearToken() { savedToken = null }
    override fun hasToken(): Boolean = savedToken != null
}

private const val REGISTER_RESPONSE = """
{
  "data": {
    "token": "test-bearer-token",
    "agent": {
      "accountId": "acc-abc123",
      "symbol": "TEST_AGENT",
      "headquarters": "X1-DF55-20250Z",
      "credits": 100000,
      "startingFaction": "COSMIC",
      "shipCount": 1
    },
    "faction": {
      "symbol": "COSMIC",
      "name": "Cosmic Engineers",
      "description": "A faction description.",
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

class RegisterAgentUseCaseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun buildUseCase(tokenRepo: FakeTokenRepository): RegisterAgentUseCase {
        val engine = MockEngine { respond(
            content = REGISTER_RESPONSE,
            status = HttpStatusCode.Created,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        )}
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
            defaultRequest { contentType(ContentType.Application.Json) }
        }
        return RegisterAgentUseCase(AccountsApi(client), tokenRepo)
    }

    @Test
    fun invoke_savesTokenToRepository() = runTest {
        val tokenRepo = FakeTokenRepository()
        buildUseCase(tokenRepo).invoke("TEST_AGENT", FactionSymbol.COSMIC)

        assertEquals("test-bearer-token", tokenRepo.savedToken)
    }

    @Test
    fun invoke_returnsAgentWithCorrectSymbol() = runTest {
        val result = buildUseCase(FakeTokenRepository()).invoke("TEST_AGENT", FactionSymbol.COSMIC)

        assertEquals("TEST_AGENT", result.agent.symbol)
    }

    @Test
    fun invoke_returnsTokenInResult() = runTest {
        val result = buildUseCase(FakeTokenRepository()).invoke("TEST_AGENT", FactionSymbol.COSMIC)

        assertEquals("test-bearer-token", result.token)
    }

    @Test
    fun invoke_agentAccountIdMappedFromResponse() = runTest {
        val result = buildUseCase(FakeTokenRepository()).invoke("TEST_AGENT", FactionSymbol.COSMIC)

        assertEquals("acc-abc123", result.agent.accountId)
    }
}
