package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import kotlin.time.Instant

data class Cooldown(
    val shipSymbol: String,
    val totalSeconds: Int,
    val remainingSeconds: Int,
    val expiration: Instant?
)
