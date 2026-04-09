package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType

data class Waypoint(
    val symbol: String,
    val type: WaypointType,
    val systemSymbol: String,
    val x: Int,
    val y: Int,
    val orbits: String?,
    val orbitals: List<String>,
    val traits: List<WaypointTrait>,
    val isUnderConstruction: Boolean
)
