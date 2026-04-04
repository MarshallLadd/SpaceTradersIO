package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ErrorResponseDto(
    val error: ErrorBodyDto
)

@Serializable
data class ErrorBodyDto(
    val code: Int,
    val message: String,
    val data: JsonObject? = null
)
