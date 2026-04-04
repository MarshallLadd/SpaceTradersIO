package com.brokenhuskysledteam.spacetradersio.sdk.api.client

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ErrorResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersApiException
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersError
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpCallValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

const val BASE_URL = "https://api.spacetraders.io/v2"

// Manages HttpClient instances for the SpaceTraders API.
// Exposes two clients:
//   - unauthenticated: for pre-login calls (e.g. /register, server status)
//   - authenticated: built on demand using the token from TokenRepository
//     so it always reflects the current stored token without needing a restart.
class SpaceTradersClient(
    private val tokenRepository: TokenRepository,
    internal val httpClientFactory: ((token: String?) -> HttpClient)? = null
) {

    val unauthenticated: HttpClient
        get() = httpClientFactory?.invoke(null) ?: buildHttpClient(token = null)

    // Rebuilt on each access so it picks up a freshly saved token after registration.
    val authenticated: HttpClient
        get() {
            val token = tokenRepository.getToken()
            return httpClientFactory?.invoke(token) ?: buildHttpClient(token = token)
        }
}

internal val spaceTradersJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private fun buildHttpClient(token: String?): HttpClient = HttpClient {

    // Parse JSON leniently: ignore unknown fields so new API properties
    // don't break deserialization as the game evolves.
    install(ContentNegotiation) {
        json(spaceTradersJson)
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

    // Intercept non-2xx responses before ContentNegotiation tries to
    // deserialize the body as a success type. Parses the API error
    // payload and throws a typed SpaceTradersApiException.
    install(HttpCallValidator) {
        validateResponse { response ->
            if (!response.status.isSuccess()) {
                val bodyText = response.bodyAsText()
                val error = try {
                    val errorDto = spaceTradersJson.decodeFromString<ErrorResponseDto>(bodyText)
                    errorDto.error.toDomain()
                } catch (_: Exception) {
                    SpaceTradersError.Unknown(code = 0, message = bodyText)
                }
                throw SpaceTradersApiException(
                    error = error,
                    httpStatus = response.status.value
                )
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
