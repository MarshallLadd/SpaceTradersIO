package com.brokenhuskysledteam.spacetraders.api.client

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

// Builds a configured Ktor HttpClient for the SpaceTraders API.
// The token parameter is nullable — unauthenticated endpoints (e.g. /register,
// server status) can be called without a bearer token.
fun buildHttpClient(token: String? = null): HttpClient = HttpClient {

    // Parse JSON leniently: ignore unknown fields so new API properties
    // don't break deserialization, and use default values for missing fields.
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }

    // Route all logs through Napier so they respect platform log levels
    // and show up in Logcat on Android / OSLog on iOS.
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
