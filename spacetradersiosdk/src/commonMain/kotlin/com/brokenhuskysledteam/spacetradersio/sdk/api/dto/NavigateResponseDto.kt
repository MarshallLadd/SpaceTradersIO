package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class NavigateResponseDto(
    val nav: ShipNavDto,
    val fuel: ShipFuelDto
)

@Serializable
data class NavigateRequestDto(val waypointSymbol: String)
