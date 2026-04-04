package com.brokenhuskysledteam.spacetradersio.sdk.api.client

import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

const val BASE_URL = "https://api.spacetraders.io/v2"

// Manages HttpClient instances for the SpaceTraders API.
// Exposes two clients:
//   - unauthenticated: for pre-login calls (e.g. /register, server status)
//   - authenticated: built on demand using the token from TokenRepository
//     so it always reflects the current stored token without needing a restart.
class SpaceTradersClient(private val tokenRepository: TokenRepository) {

    val unauthenticated: HttpClient = buildHttpClient(token = null)

    // Rebuilt on each access so it picks up a freshly saved token after registration.
    val authenticated: HttpClient
        get() = buildHttpClient(token = tokenRepository.getToken())
}

private fun buildHttpClient(token: String?): HttpClient = HttpClient {

    // Parse JSON leniently: ignore unknown fields so new API properties
    // don't break deserialization as the game evolves.
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }

    // Route all logs through Napier so they respect platform log levels
    // and appear in Logcat on Android / OSLog on iOS.
    install(Logging) {
        level = LogLevel.INFO
        logger = object : Logger {
            override fun log(message: String) {
                Napier.d(message, tag = "SpaceTradersAPI")
            }
        }
    }

    // Apply base URL and default headers to every request.
    defaultRequest {
        url(BASE_URL)
        contentType(ContentType.Application.Json)
        if (token != null) {
            headers.append("Authorization", "Bearer $token")
        }
    }
}
