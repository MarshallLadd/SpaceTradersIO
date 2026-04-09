package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.NavigateResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.NavigateResult

fun NavigateResponseDto.toDomain(): NavigateResult = NavigateResult(
    nav = nav.toDomain(),
    fuel = fuel.toDomain()
)
