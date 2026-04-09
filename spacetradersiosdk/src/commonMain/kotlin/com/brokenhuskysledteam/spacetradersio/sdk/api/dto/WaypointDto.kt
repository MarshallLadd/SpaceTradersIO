package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class WaypointDto(
    val symbol: String,
    val type: String,
    val systemSymbol: String,
    val x: Int,
    val y: Int,
    val orbits: String? = null,
    val orbitals: List<WaypointOrbitalDto> = emptyList(),
    val traits: List<WaypointTraitDto> = emptyList(),
    val isUnderConstruction: Boolean = false
)

@Serializable
data class WaypointOrbitalDto(val symbol: String)

@Serializable
data class WaypointTraitDto(
    val symbol: String,
    val name: String,
    val description: String
)
