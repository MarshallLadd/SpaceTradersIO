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

/**
 * Root URL for all SpaceTraders v2 API endpoints.
 *
 * Declared as a top-level constant so every API class can reference it without
 * depending on a shared object — keeps the import graph shallow.
 */
const val BASE_URL = "https://api.spacetraders.io/v2"

/**
 * Central factory and holder for Ktor [HttpClient] instances used throughout the SDK.
 *
 * **Pattern:** Authenticated/unauthenticated client split. In any REST API SDK you
 * typically need at least two clients: one for public/pre-login endpoints and one
 * that carries a Bearer token. Holding them here — rather than constructing a new
 * client per API class — means plugin installation (serialization, logging, error
 * handling) is configured in exactly one place.
 *
 * **In this project:** The SpaceTraders API uses two distinct token types:
 * - **AgentToken** — per-agent JWT used for all gameplay endpoints. Stored in
 *   [TokenRepository] after a successful registration or token import.
 * - **AccountToken** — per-account JWT issued from the SpaceTraders dashboard.
 *   Used only for `POST /register`; never stored by the SDK.
 *
 * The [authenticated] property reads the token from [TokenRepository] on every
 * access so that a freshly registered agent's token is picked up automatically
 * without restarting the SDK or re-injecting a new client.
 *
 * @param tokenRepository Source of the stored AgentToken. Queried lazily on each
 *   [authenticated] access.
 * @param httpClientFactory Optional override for the [HttpClient] constructor.
 *   Inject a [io.ktor.client.engine.mock.MockEngine]-backed factory in tests to
 *   intercept all network calls without touching real HTTP.
 */
class SpaceTradersClient(
    private val tokenRepository: TokenRepository,
    internal val httpClientFactory: ((token: String?) -> HttpClient)? = null
) {

    /**
     * An [HttpClient] with no Authorization header attached.
     *
     * Use this for endpoints that do not require authentication, such as
     * fetching the server status or any future public endpoints. The client
     * still has all other plugins (serialization, logging, error handling)
     * installed via [buildHttpClient].
     */
    val unauthenticated: HttpClient
        get() = httpClientFactory?.invoke(null) ?: buildHttpClient(token = null)

    /**
     * An [HttpClient] with an `Authorization: Bearer <AgentToken>` header on
     * every request.
     *
     * **Rebuilt on each access** — this is intentional. Because [TokenRepository]
     * is the source of truth for the current token, reading it here guarantees that
     * a token saved during registration is used immediately on the very next
     * authenticated call, without requiring any manual refresh or re-injection.
     *
     * Ktor [HttpClient] construction is cheap (plugin wiring only; the underlying
     * engine connection pool is created lazily), so the per-access rebuild cost is
     * negligible.
     */
    val authenticated: HttpClient
        get() {
            val token = tokenRepository.getToken()
            return httpClientFactory?.invoke(token) ?: buildHttpClient(token = token)
        }

    /**
     * Builds a one-off authenticated [HttpClient] for the given [token].
     *
     * Use this when you need to call an endpoint with a token that is **not**
     * the stored AgentToken — specifically the AccountToken required by
     * `POST /register`. The AccountToken is ephemeral (never stored), so it
     * cannot come from [TokenRepository].
     *
     * @param token The Bearer token to attach to every request on the returned client.
     * @return A fully configured [HttpClient] that sends `Authorization: Bearer $token`.
     */
    fun authenticatedWith(token: String): HttpClient =
        httpClientFactory?.invoke(token) ?: buildHttpClient(token = token)
}

/**
 * Shared [Json] instance used for all API serialization and deserialization.
 *
 * **Pattern:** Centralised `Json` configuration. Define one `Json` instance for
 * the entire API layer rather than using `Json.Default` or creating a new instance
 * per call — this ensures consistent behaviour and avoids redundant object creation.
 *
 * Key configuration choices:
 * - `ignoreUnknownKeys = true` — The SpaceTraders API evolves continuously; new
 *   fields are added to responses without a version bump. Without this flag, adding
 *   any unknown key to a response would throw a [kotlinx.serialization.SerializationException]
 *   and crash the app. Always set this on API clients that you do not control.
 * - `isLenient = true` — Accepts JSON that deviates slightly from strict RFC 8259
 *   (e.g. unquoted keys, single-quoted strings). Defensive against minor
 *   inconsistencies in the API's error payloads.
 *
 * `internal` visibility: used by [buildHttpClient] and test helpers in this module,
 * but not intended to be part of the public SDK surface.
 */
internal val spaceTradersJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Constructs a fully configured Ktor [HttpClient] for the SpaceTraders API.
 *
 * **Pattern:** Single-function client builder. All plugin installation lives here
 * so every client — authenticated, unauthenticated, and test-injected — shares
 * identical behaviour. In a new project, extract your own equivalent of this
 * function and pass it as a factory parameter so tests can swap the engine without
 * needing to wire mock objects through every API class.
 *
 * The plugins installed, in evaluation order:
 *
 * 1. **[ContentNegotiation]** — Registers [spaceTradersJson] as the codec for
 *    `application/json`. With this installed:
 *    - `setBody(myDto)` in a request automatically serialises the DTO to JSON.
 *    - `body<MyDto>()` on a response automatically deserialises JSON to the type.
 *    Removing this plugin means you must manually call `Json.encodeToString` /
 *    `Json.decodeFromString` on every request and response.
 *
 * 2. **[Logging]** — Routes all Ktor HTTP logs through [Napier] so they appear
 *    in Logcat (Android) and OSLog (iOS) and respect the platform's log-level
 *    filtering. `LogLevel.INFO` captures request/response lines without the verbose
 *    header/body dump of `LogLevel.ALL`.
 *
 * 3. **[HttpCallValidator]** — Centralized non-2xx error handling. Ktor's default
 *    behaviour on a 4xx/5xx response is to proceed normally and return the raw
 *    [io.ktor.client.statement.HttpResponse] to the caller. `validateResponse` runs
 *    before the response body is consumed by `ContentNegotiation`, so this is the
 *    correct place to read the error body, deserialise it as [ErrorResponseDto],
 *    map it to the typed [SpaceTradersError] sealed hierarchy via `toDomain()`, and
 *    throw a [SpaceTradersApiException]. Every API endpoint then gets a typed error
 *    for free — callers `catch (e: SpaceTradersApiException)` and `when`-match on
 *    `e.error`.
 *
 * 4. **[defaultRequest]** — Sets the base URL and two default headers on *every*
 *    request built with this client:
 *    - `url(BASE_URL)` — callers write `get("my/agent")` rather than the full URL.
 *    - `contentType(ContentType.Application.Json)` — sets `Content-Type: application/json`
 *      globally.
 *    - `Authorization: Bearer $token` — added only when [token] is non-null.
 *
 * > **GOTCHA — empty-body POSTs:** Because `defaultRequest` sets `Content-Type:
 * > application/json` on *every* request, action endpoints like orbit and dock that
 * > have no request body must still call `setBody("{}")`. Sending an empty body with
 * > `Content-Type: application/json` causes the SpaceTraders API to return HTTP 422.
 * > Always pass `setBody("{}")` for no-body POST endpoints.
 *
 * @param token Optional Bearer token. When non-null, every request built with the
 *   returned client includes `Authorization: Bearer $token`.
 */
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
                    // If the error body isn't valid JSON (e.g. a gateway timeout HTML page),
                    // fall back to an Unknown error carrying the raw body text.
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
    // IMPORTANT: contentType(ContentType.Application.Json) is set here globally.
    // This means POST endpoints with no body must still call setBody("{}") —
    // sending an empty body with Content-Type: application/json returns HTTP 422.
    defaultRequest {
        url(BASE_URL)
        contentType(ContentType.Application.Json)
        if (token != null) {
            headers.append("Authorization", "Bearer $token")
        }
    }
}
