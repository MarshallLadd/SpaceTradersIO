package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

// Mappers for inter-system travel: jump gate, system, and jump response → domain.
// Warp reuses NavigateResponseDto.toDomain() (NavigateMapper.kt), since warp returns the same
// nav+fuel shape as navigate.

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.JumpGateDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.JumpResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.SystemDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.JumpGate
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.JumpResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.StarSystem

/** Maps a [JumpGateDto] to a [JumpGate] domain model. */
fun JumpGateDto.toDomain(): JumpGate = JumpGate(
    symbol = symbol,
    connections = connections
)

/** Maps a [SystemDto] to a [StarSystem] domain model. */
fun SystemDto.toDomain(): StarSystem = StarSystem(
    symbol = symbol,
    sectorSymbol = sectorSymbol,
    type = type,
    x = x,
    y = y
)

/** Maps a [JumpResponseDto] to a [JumpResult] domain model. */
fun JumpResponseDto.toDomain(): JumpResult = JumpResult(
    nav = nav.toDomain(),
    cooldown = cooldown.toDomain()
)
