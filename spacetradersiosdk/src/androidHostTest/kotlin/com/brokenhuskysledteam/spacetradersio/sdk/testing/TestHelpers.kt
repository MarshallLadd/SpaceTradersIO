package com.brokenhuskysledteam.spacetradersio.sdk.testing

import com.brokenhuskysledteam.spacetradersio.sdk.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

// In-memory TokenRepository for SDK tests. Defaults to a non-null token
// so authenticated API calls work out of the box; pass storedToken = null
// to simulate a logged-out state.
class FakeTokenRepository(var storedToken: String? = "test-token") : TokenRepository {
    override fun getToken(): String? = storedToken
    override fun saveToken(token: String) { storedToken = token }
    override fun clearToken() { storedToken = null }
    override fun hasToken(): Boolean = storedToken != null
}

// Creates a SpaceTradersClient backed by Ktor's MockEngine.
// The httpClientFactory overrides the default platform engine so no real
// HTTP calls are made. The handler receives every request and must return
// a mock response (typically via respond() or respondError()).
fun buildMockSpaceTradersClient(
    tokenRepository: TokenRepository = FakeTokenRepository(),
    handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
): SpaceTradersClient {
    return SpaceTradersClient(
        tokenRepository = tokenRepository,
        httpClientFactory = { token ->
            HttpClient(MockEngine(handler)) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                defaultRequest {
                    contentType(ContentType.Application.Json)
                    if (token != null) headers.append("Authorization", "Bearer $token")
                }
            }
        }
    )
}
