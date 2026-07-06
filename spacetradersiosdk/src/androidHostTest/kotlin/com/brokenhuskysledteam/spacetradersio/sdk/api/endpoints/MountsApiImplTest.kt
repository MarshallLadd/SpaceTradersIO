package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

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

private const val MOUNTS_RESPONSE = """
{"data":[
  {"symbol":"MOUNT_MINING_LASER_II","name":"Mining Laser II","description":"x","requirements":{"power":2,"crew":2},"strength":5},
  {"symbol":"MOUNT_SURVEYOR_II","name":"Surveyor II","description":"y","requirements":{"power":3,"crew":4},"strength":2,"deposits":["IRON_ORE"]}
]}
"""

private const val MODIFY_RESPONSE = """
{"data":{
  "agent":{"symbol":"LADD","headquarters":"X1-DM91-A1","credits":168000,"startingFaction":"COSMIC","shipCount":2},
  "mounts":[{"symbol":"MOUNT_MINING_LASER_I","name":"Mining Laser I","description":"z","requirements":{"power":1,"crew":1},"strength":10}],
  "cargo":{"capacity":40,"units":0,"inventory":[]},
  "transaction":{"waypointSymbol":"X1-DM91-A1","shipSymbol":"LADD-1","tradeSymbol":"MOUNT_MINING_LASER_I","totalPrice":3600,"timestamp":"2026-07-06T15:47:49.153Z"}
}}
"""

class MountsApiImplTest {

    private fun apiRespondingWith(body: String, capture: MutableList<HttpRequestData> = mutableListOf()) =
        MountsApiImpl(buildMockSpaceTradersClient { request ->
            capture.add(request)
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) to capture

    @Test
    fun getMounts_parsesMountList() = runTest {
        val (api, requests) = apiRespondingWith(MOUNTS_RESPONSE)
        val mounts = api.getMounts("LADD-1")
        assertEquals(2, mounts.size)
        assertEquals("MOUNT_MINING_LASER_II", mounts[0].symbol)
        assertEquals(5, mounts[0].strength)
        assertEquals(listOf("IRON_ORE"), mounts[1].deposits)
        assertEquals(HttpMethod.Get, requests.single().method)
        assertTrue(requests.single().url.encodedPath.endsWith("my/ships/LADD-1/mounts"))
    }

    @Test
    fun installMount_parsesResponseAndPostsToInstall() = runTest {
        val (api, requests) = apiRespondingWith(MODIFY_RESPONSE)
        val response = api.installMount("LADD-1", "MOUNT_MINING_LASER_I")
        assertEquals(168000L, response.agent.credits)
        assertEquals(1, response.mounts.size)
        assertEquals(3600, response.transaction.totalPrice)
        assertEquals(HttpMethod.Post, requests.single().method)
        assertTrue(requests.single().url.encodedPath.endsWith("my/ships/LADD-1/mounts/install"))
    }

    @Test
    fun removeMount_postsToRemove() = runTest {
        val (api, requests) = apiRespondingWith(MODIFY_RESPONSE)
        api.removeMount("LADD-1", "MOUNT_MINING_LASER_I")
        assertEquals(HttpMethod.Post, requests.single().method)
        assertTrue(requests.single().url.encodedPath.endsWith("my/ships/LADD-1/mounts/remove"))
    }
}
