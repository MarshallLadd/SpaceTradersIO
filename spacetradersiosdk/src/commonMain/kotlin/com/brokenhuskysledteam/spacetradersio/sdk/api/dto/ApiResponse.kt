package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

// Every SpaceTraders API response wraps its payload in a "data" field.
// These envelope types model that top-level wrapper so we can deserialize
// both single-object and paginated list responses uniformly.

@Serializable
data class ApiResponse<T>(
    val data: T
)

@Serializable
data class PaginatedResponse<T>(
    val data: List<T>,
    val meta: MetaDto
)

@Serializable
data class MetaDto(
    val total: Int,
    val page: Int,
    val limit: Int
)
