package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ErrorResponseDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun deserialize_errorWithoutData() {
        val body = """{"error":{"code":4100,"message":"Token is empty."}}"""
        val dto = json.decodeFromString<ErrorResponseDto>(body)

        assertEquals(4100, dto.error.code)
        assertEquals("Token is empty.", dto.error.message)
        assertNull(dto.error.data)
    }

    @Test
    fun deserialize_errorWithData() {
        val body = """{"error":{"code":4000,"message":"Cooldown active.","data":{"remainingSeconds":30}}}"""
        val dto = json.decodeFromString<ErrorResponseDto>(body)

        assertEquals(4000, dto.error.code)
        assertEquals("Cooldown active.", dto.error.message)
        val data = assertNotNull(dto.error.data)
        assertEquals(JsonPrimitive(30), data["remainingSeconds"])
    }

    @Test
    fun deserialize_errorWithEmptyDataObject() {
        val body = """{"error":{"code":3000,"message":"Serialization error.","data":{}}}"""
        val dto = json.decodeFromString<ErrorResponseDto>(body)

        assertEquals(3000, dto.error.code)
        val data = assertNotNull(dto.error.data)
        assertEquals(0, data.size)
    }

    @Test
    fun deserialize_ignoresUnknownFields() {
        val body = """{"error":{"code":4100,"message":"Token is empty.","unknownField":"value"}}"""
        val dto = json.decodeFromString<ErrorResponseDto>(body)

        assertEquals(4100, dto.error.code)
        assertEquals("Token is empty.", dto.error.message)
    }
}
