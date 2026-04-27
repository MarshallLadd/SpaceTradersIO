package com.brokenhuskysledteam.spacetradersio.sdk.data.db

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Shipyard
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipyardShip
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.SupplyLevel

// Import aliases resolve the same-name clash between SQLDelight-generated DB
// classes and the domain model classes of the same name.
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.Shipyard as DbShipyard
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.Shipyard_ship as DbShipyardShip

fun DbShipyard.toDomain(ships: List<ShipyardShip>?): Shipyard = Shipyard(
    symbol = symbol,
    modificationsFee = modifications_fee.toInt(),
    ships = ships
)

fun DbShipyardShip.toDomain(): ShipyardShip = ShipyardShip(
    type = ShipType.fromString(ship_type),
    name = name,
    description = description,
    purchasePrice = purchase_price.toInt(),
    supply = SupplyLevel.fromString(supply),
    frameName = frame_name,
    engineSpeed = engine_speed.toInt(),
    reactorPowerOutput = reactor_power_output.toInt(),
    crewRequired = crew_required.toInt(),
    crewCapacity = crew_capacity.toInt()
)

fun ShipyardShipQueries.upsert(waypoint_symbol: String, ship: ShipyardShip) {
    upsert(
        waypoint_symbol = waypoint_symbol,
        ship_type = ship.type.name,
        name = ship.name,
        description = ship.description,
        purchase_price = ship.purchasePrice.toLong(),
        supply = ship.supply.name,
        frame_name = ship.frameName,
        engine_speed = ship.engineSpeed.toLong(),
        reactor_power_output = ship.reactorPowerOutput.toLong(),
        crew_required = ship.crewRequired.toLong(),
        crew_capacity = ship.crewCapacity.toLong()
    )
}
