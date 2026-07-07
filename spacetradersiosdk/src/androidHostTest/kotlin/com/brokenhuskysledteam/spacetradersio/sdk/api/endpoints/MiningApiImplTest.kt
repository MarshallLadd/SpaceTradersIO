package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.SurveyDepositDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.SurveyDto
import com.brokenhuskysledteam.spacetradersio.sdk.testing.buildMockSpaceTradersClient
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val EXTRACT_RESPONSE = """
{"data":{
  "extraction":{"shipSymbol":"LADD-1","yield":{"symbol":"IRON_ORE","units":5}},
  "cooldown":{"shipSymbol":"LADD-1","totalSeconds":70,"remainingSeconds":70,"expiration":"2026-07-06T00:01:10Z"},
  "cargo":{"capacity":40,"units":5,"inventory":[{"symbol":"IRON_ORE","name":"Iron Ore","description":"ore","units":5}]}
}}
"""

private const val SURVEY_RESPONSE = """
{"data":{
  "cooldown":{"shipSymbol":"LADD-1","totalSeconds":60,"remainingSeconds":60,"expiration":"2026-07-06T00:01:00Z"},
  "surveys":[{"signature":"sig-1","symbol":"X1-DM91-B7","deposits":[{"symbol":"IRON_ORE"}],"expiration":"2026-07-06T01:00:00Z","size":"MODERATE"}]
}}
"""

private const val JETTISON_RESPONSE = """{"data":{"cargo":{"capacity":40,"units":0,"inventory":[]}}}"""

class MiningApiImplTest {

    private fun api(body: String, capture: MutableList<HttpRequestData> = mutableListOf()) =
        MiningApiImpl(buildMockSpaceTradersClient { request ->
            capture.add(request)
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) to capture

    @Test
    fun extract_parsesYieldCooldownCargo_andPosts() = runTest {
        val (a, requests) = api(EXTRACT_RESPONSE)
        val r = a.extract("LADD-1")
        assertEquals("IRON_ORE", r.extraction.yieldResult.symbol)
        assertEquals(5, r.extraction.yieldResult.units)
        assertEquals(70, r.cooldown.totalSeconds)
        assertEquals(5, r.cargo.units)
        assertEquals(HttpMethod.Post, requests.single().method)
        assertTrue(requests.single().url.encodedPath.endsWith("my/ships/LADD-1/extract"))
    }

    @Test
    fun extractWithSurvey_postsToExtractSurvey() = runTest {
        val (a, requests) = api(EXTRACT_RESPONSE)
        val survey = SurveyDto("sig-1", "X1-DM91-B7", listOf(SurveyDepositDto("IRON_ORE")), "2026-07-06T01:00:00Z", "MODERATE")
        a.extractWithSurvey("LADD-1", survey)
        assertTrue(requests.single().url.encodedPath.endsWith("my/ships/LADD-1/extract/survey"))
    }

    @Test
    fun createSurvey_parsesSurveys_andPosts() = runTest {
        val (a, requests) = api(SURVEY_RESPONSE)
        val r = a.createSurvey("LADD-1")
        assertEquals(1, r.surveys.size)
        assertEquals("sig-1", r.surveys[0].signature)
        assertTrue(requests.single().url.encodedPath.endsWith("my/ships/LADD-1/survey"))
    }

    @Test
    fun jettison_parsesCargo_andPosts() = runTest {
        val (a, requests) = api(JETTISON_RESPONSE)
        val r = a.jettison("LADD-1", "IRON_ORE", 5)
        assertEquals(0, r.cargo.units)
        assertTrue(requests.single().url.encodedPath.endsWith("my/ships/LADD-1/jettison"))
    }
}
