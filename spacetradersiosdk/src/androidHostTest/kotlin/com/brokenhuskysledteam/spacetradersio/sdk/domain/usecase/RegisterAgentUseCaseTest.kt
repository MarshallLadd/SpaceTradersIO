package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AccountsApi
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.FactionSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManagerImpl
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
import kotlin.test.assertNotNull

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

    private fun buildUseCase(tokenRepo: FakeTokenRepository): RegisterAgentUseCase {
        val client = buildMockSpaceTradersClient(
            tokenRepository = FakeTokenRepository(storedToken = null)
        ) { respond(
            content = REGISTER_RESPONSE,
            status = HttpStatusCode.Created,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        )}
        return RegisterAgentUseCaseImpl(AccountsApi(client), SessionManagerImpl(tokenRepo))
    }

    @Test
    fun invoke_savesTokenToRepository() = runTest {
        val tokenRepo = FakeTokenRepository(storedToken = null)
        buildUseCase(tokenRepo).invoke("TEST_AGENT", FactionSymbol.COSMIC, "test-account-token")

        assertEquals("test-bearer-token", tokenRepo.storedToken)
    }

    @Test
    fun invoke_returnsAgentWithCorrectSymbol() = runTest {
        val result = buildUseCase(FakeTokenRepository(storedToken = null)).invoke("TEST_AGENT", FactionSymbol.COSMIC, "test-account-token")

        assertEquals("TEST_AGENT", result.agent.symbol)
    }

    @Test
    fun invoke_returnsTokenInResult() = runTest {
        val result = buildUseCase(FakeTokenRepository(storedToken = null)).invoke("TEST_AGENT", FactionSymbol.COSMIC, "test-account-token")

        assertEquals("test-bearer-token", result.token)
    }

    @Test
    fun invoke_agentAccountIdMappedFromResponse() = runTest {
        val result = buildUseCase(FakeTokenRepository(storedToken = null)).invoke("TEST_AGENT", FactionSymbol.COSMIC, "test-account-token")

        assertEquals("acc-abc123", result.agent.accountId)
    }

    @Test
    fun invoke_createsActiveSession() = runTest {
        val tokenRepo = FakeTokenRepository(storedToken = null)
        val sessionManager = SessionManagerImpl(tokenRepo)
        val client = buildMockSpaceTradersClient(
            tokenRepository = FakeTokenRepository(storedToken = null)
        ) { respond(
            content = REGISTER_RESPONSE,
            status = HttpStatusCode.Created,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        )}
        RegisterAgentUseCaseImpl(AccountsApi(client), sessionManager)
            .invoke("TEST_AGENT", FactionSymbol.COSMIC, "test-account-token")

        assertNotNull(sessionManager.requireSession())
    }
}
