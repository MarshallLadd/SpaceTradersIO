package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ContractsApi
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractType
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

private const val CONTRACTS_RESPONSE = """
{
  "data": [
    {
      "id": "contract-abc",
      "factionSymbol": "COSMIC",
      "type": "PROCUREMENT",
      "terms": {
        "deadline": "2025-07-01T00:00:00.000Z",
        "payment": { "onAccepted": 2000, "onFulfilled": 10000 },
        "deliver": []
      },
      "accepted": false,
      "fulfilled": false,
      "expiration": "2025-05-15T00:00:00.000Z",
      "deadlineToAccept": "2025-05-20T00:00:00.000Z"
    }
  ],
  "meta": { "total": 1, "page": 1, "limit": 20 }
}
"""

class GetMyContractsUseCaseTest {

    private fun buildUseCase(): GetMyContractsUseCase {
        val engine = MockEngine { respond(
            content = CONTRACTS_RESPONSE,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        )}
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { contentType(ContentType.Application.Json) }
        }
        return GetMyContractsUseCase(ContractsApi(client))
    }

    @Test
    fun invoke_returnsListWithCorrectSize() = runTest {
        val result = buildUseCase().invoke()
        assertEquals(1, result.size)
    }

    @Test
    fun invoke_mapsContractIdCorrectly() = runTest {
        val result = buildUseCase().invoke()
        assertEquals("contract-abc", result.first().id)
    }

    @Test
    fun invoke_mapsContractTypeCorrectly() = runTest {
        val result = buildUseCase().invoke()
        assertEquals(ContractType.PROCUREMENT, result.first().type)
    }
}
