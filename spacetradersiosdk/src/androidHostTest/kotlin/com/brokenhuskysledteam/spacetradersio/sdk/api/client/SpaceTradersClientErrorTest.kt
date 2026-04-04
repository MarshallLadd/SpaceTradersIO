package com.brokenhuskysledteam.spacetradersio.sdk.api.client

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ErrorResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersApiException
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersError
import com.brokenhuskysledteam.spacetradersio.sdk.testing.FakeTokenRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpCallValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class SpaceTradersClientErrorTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun buildClientWithValidator(
        status: HttpStatusCode,
        body: String
    ): HttpClient {
        return HttpClient(MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }) {
            install(ContentNegotiation) { json(json) }
            install(HttpCallValidator) {
                validateResponse { response ->
                    if (!response.status.isSuccess()) {
                        val bodyText = response.bodyAsText()
                        val error = try {
                            val errorDto = json.decodeFromString<ErrorResponseDto>(bodyText)
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
            defaultRequest { contentType(ContentType.Application.Json) }
        }
    }

    @Test
    fun errorResponse_throwsSpaceTradersApiExceptionWithCorrectType() = runTest {
        val errorBody = """{"error":{"code":4100,"message":"Token is empty."}}"""
        val client = buildClientWithValidator(HttpStatusCode.Unauthorized, errorBody)

        val exception = assertFailsWith<SpaceTradersApiException> {
            client.get("/test")
        }

        assertIs<SpaceTradersError.AuthError.TokenEmpty>(exception.error)
        assertEquals(401, exception.httpStatus)
        assertEquals("Token is empty.", exception.message)
    }

    @Test
    fun errorResponse_preservesHttpStatus() = runTest {
        val errorBody = """{"error":{"code":4228,"message":"Ship cargo is full."}}"""
        val client = buildClientWithValidator(HttpStatusCode.Conflict, errorBody)

        val exception = assertFailsWith<SpaceTradersApiException> {
            client.get("/test")
        }

        assertIs<SpaceTradersError.ShipOperationError.CargoFull>(exception.error)
        assertEquals(409, exception.httpStatus)
    }

    @Test
    fun malformedErrorBody_fallsBackToUnknown() = runTest {
        val client = buildClientWithValidator(HttpStatusCode.InternalServerError, "<html>Server Error</html>")

        val exception = assertFailsWith<SpaceTradersApiException> {
            client.get("/test")
        }

        assertIs<SpaceTradersError.Unknown>(exception.error)
        assertEquals(0, exception.error.code)
        assertEquals("<html>Server Error</html>", exception.error.message)
        assertEquals(500, exception.httpStatus)
    }

    @Test
    fun successResponse_doesNotThrow() = runTest {
        val client = buildClientWithValidator(HttpStatusCode.OK, """{"data":{"symbol":"TEST"}}""")

        val response = client.get("/test")
        assertEquals(200, response.status.value)
    }

    @Test
    fun unknownErrorCode_mapsToUnknownType() = runTest {
        val errorBody = """{"error":{"code":9999,"message":"Future error."}}"""
        val client = buildClientWithValidator(HttpStatusCode.BadRequest, errorBody)

        val exception = assertFailsWith<SpaceTradersApiException> {
            client.get("/test")
        }

        assertIs<SpaceTradersError.Unknown>(exception.error)
        assertEquals(9999, exception.error.code)
        assertEquals(400, exception.httpStatus)
    }

    @Test
    fun spaceTradersClient_authenticatedClient_interceptsErrors() = runTest {
        val tokenRepo = FakeTokenRepository("test-token")
        val errorBody = """{"error":{"code":4107,"message":"Agent not found."}}"""
        val client = SpaceTradersClient(
            tokenRepository = tokenRepo,
            httpClientFactory = { _ ->
                buildClientWithValidator(HttpStatusCode.NotFound, errorBody)
            }
        )

        val exception = assertFailsWith<SpaceTradersApiException> {
            client.authenticated.get("/my/agent")
        }

        assertIs<SpaceTradersError.AuthError.AgentNotExists>(exception.error)
        assertEquals(404, exception.httpStatus)
    }

    @Test
    fun spaceTradersClient_unauthenticatedClient_interceptsErrors() = runTest {
        val tokenRepo = FakeTokenRepository(null)
        val errorBody = """{"error":{"code":4111,"message":"Agent symbol already taken."}}"""
        val client = SpaceTradersClient(
            tokenRepository = tokenRepo,
            httpClientFactory = { _ ->
                buildClientWithValidator(HttpStatusCode.Conflict, errorBody)
            }
        )

        val exception = assertFailsWith<SpaceTradersApiException> {
            client.unauthenticated.get("/register")
        }

        assertIs<SpaceTradersError.AuthError.RegisterAgentConflictSymbol>(exception.error)
        assertEquals(409, exception.httpStatus)
    }
}
