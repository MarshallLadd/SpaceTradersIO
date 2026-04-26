package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipyardDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipyardShipDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Shipyard
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipyardShip
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.SupplyLevel

fun ShipyardDto.toDomain(): Shipyard = Shipyard(
    symbol = symbol,
    modificationsFee = modificationsFee,
    ships = ships?.map { it.toDomain() }
)

fun ShipyardShipDto.toDomain(): ShipyardShip = ShipyardShip(
    type = ShipType.fromString(type),
    name = name,
    description = description,
    purchasePrice = purchasePrice,
    supply = SupplyLevel.fromString(supply),
    frameName = frame.name,
    engineSpeed = engine.speed,
    reactorPowerOutput = reactor.powerOutput,
    crewRequired = crew.required,
    crewCapacity = crew.capacity
)
