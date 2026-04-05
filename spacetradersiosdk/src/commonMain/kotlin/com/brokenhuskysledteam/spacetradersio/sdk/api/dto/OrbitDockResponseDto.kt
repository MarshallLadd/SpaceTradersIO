package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

// Shared response shape for both POST /my/ships/{symbol}/orbit
// and POST /my/ships/{symbol}/dock — both return { "data": { "nav": ShipNav } }.
@Serializable
data class OrbitDockResponseDto(
    val nav: ShipNavDto
)
