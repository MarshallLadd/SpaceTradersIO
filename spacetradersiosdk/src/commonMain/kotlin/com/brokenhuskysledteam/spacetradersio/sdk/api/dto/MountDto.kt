package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

/**
 * Wire-format representation of a ship mount (SpaceTraders `ShipMount` schema).
 *
 * Returned by `GET /my/ships/{shipSymbol}/mounts` and embedded in the install/remove responses.
 *
 * @property symbol The mount identifier (e.g. `"MOUNT_MINING_LASER_II"`).
 * @property name Human-readable name.
 * @property description Flavour/spec description.
 * @property strength The mount's effectiveness (e.g. extraction yield or sensor range), when applicable.
 * @property requirements Power/crew/module-slot requirements to run the mount.
 * @property deposits For survey/mining mounts, the deposit types the mount can find/extract.
 */
@Serializable
data class ShipMountDto(
    val symbol: String,
    val name: String,
    val description: String,
    val strength: Int? = null,
    val requirements: MountRequirementsDto = MountRequirementsDto(),
    val deposits: List<String> = emptyList()
)

/**
 * The power/crew/slot cost of running a mount.
 *
 * @property power Reactor power units required. `null` when unspecified.
 * @property crew Crew required. `null` when unspecified.
 * @property slots Module slots consumed. `null` when unspecified.
 */
@Serializable
data class MountRequirementsDto(
    val power: Int? = null,
    val crew: Int? = null,
    val slots: Int? = null
)

/**
 * Request body for `POST /my/ships/{shipSymbol}/mounts/install` and `.../remove`.
 *
 * @property symbol The mount symbol to install or remove.
 */
@Serializable
data class MountModificationRequestDto(
    val symbol: String
)

/**
 * Response body for both mount install and remove.
 *
 * @property agent The agent after the modification fee is applied.
 * @property mounts The ship's full mount list after the change.
 * @property cargo The ship's cargo after the change (install consumes the mount from cargo;
 *   remove returns it to cargo).
 * @property transaction The modification-fee receipt. See [ShipModificationTransactionDto].
 */
@Serializable
data class MountModificationResponseDto(
    val agent: AgentDto,
    val mounts: List<ShipMountDto>,
    val cargo: ShipCargoDto,
    val transaction: ShipModificationTransactionDto
)

/**
 * Wire-format of a ship-modification transaction (mount/module install or remove fee).
 *
 * Distinct from [MarketTransactionDto]: it has no `type`/`units`/`pricePerUnit` — only the
 * total fee.
 *
 * @property waypointSymbol Where the modification happened (must have a shipyard).
 * @property shipSymbol The ship modified.
 * @property tradeSymbol The mount symbol involved.
 * @property totalPrice The modification fee in credits.
 * @property timestamp ISO-8601 timestamp; the mapper parses it to `Instant`.
 */
@Serializable
data class ShipModificationTransactionDto(
    val waypointSymbol: String,
    val shipSymbol: String,
    val tradeSymbol: String,
    val totalPrice: Int,
    val timestamp: String
)
