package com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler

import kotlin.time.Instant

data class ScheduledRefresh(
    val id: String,
    val expiresAt: Instant,
    val action: suspend () -> Unit
)
