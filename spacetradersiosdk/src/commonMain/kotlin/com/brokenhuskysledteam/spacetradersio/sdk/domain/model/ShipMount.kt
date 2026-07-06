package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

/**
 * A mount installed on (or installable to) a ship — the hardware that lets a ship mine, survey,
 * scan, or fight.
 *
 * @property symbol The mount identifier (e.g. `"MOUNT_MINING_LASER_II"`). Kept as a raw string
 *   (not an enum) so new mount types added by the API do not crash the client.
 * @property name Human-readable name.
 * @property description Spec/flavour description.
 * @property strength The mount's effectiveness where applicable (extraction power, sensor range…).
 * @property requirements Power/crew/slot cost to run the mount.
 * @property deposits For survey/mining mounts, the deposit types it can find or extract.
 */
data class ShipMount(
    val symbol: String,
    val name: String,
    val description: String,
    val strength: Int?,
    val requirements: MountRequirements,
    val deposits: List<String>
)

/**
 * The power/crew/slot cost of running a [ShipMount].
 *
 * @property power Reactor power units required, or `null` if unspecified.
 * @property crew Crew required, or `null` if unspecified.
 * @property slots Module slots consumed, or `null` if unspecified.
 */
data class MountRequirements(
    val power: Int?,
    val crew: Int?,
    val slots: Int?
)
