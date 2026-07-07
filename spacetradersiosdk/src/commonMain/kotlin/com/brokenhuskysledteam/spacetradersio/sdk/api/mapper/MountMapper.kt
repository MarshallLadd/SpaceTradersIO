package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

// Mapper: ShipMountDto/ShipModificationTransactionDto → domain models.

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MountRequirementsDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipModificationTransactionDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipMountDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.MountRequirements
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipModificationTransaction
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipMount
import kotlin.time.Instant

/** Maps a [ShipMountDto] to a [ShipMount] domain model. */
fun ShipMountDto.toDomain(): ShipMount = ShipMount(
    symbol = symbol,
    name = name,
    description = description,
    strength = strength,
    requirements = requirements.toDomain(),
    deposits = deposits
)

/** Maps a [MountRequirementsDto] to a [MountRequirements] domain model. */
fun MountRequirementsDto.toDomain(): MountRequirements = MountRequirements(
    power = power,
    crew = crew,
    slots = slots
)

/**
 * Maps a [ShipModificationTransactionDto] to a [ShipModificationTransaction], parsing the
 * ISO-8601 timestamp to an [Instant] at the boundary (as all mappers do).
 */
fun ShipModificationTransactionDto.toDomain(): ShipModificationTransaction = ShipModificationTransaction(
    waypointSymbol = waypointSymbol,
    shipSymbol = shipSymbol,
    tradeSymbol = tradeSymbol,
    totalPrice = totalPrice,
    timestamp = Instant.parse(timestamp)
)
