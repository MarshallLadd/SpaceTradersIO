package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

/**
 * Response for `POST /my/ships/{shipSymbol}/jump`.
 *
 * A jump moves the ship to a connected jump gate and incurs a cooldown. Any transaction/agent
 * fields the API includes are ignored (via `ignoreUnknownKeys`) — the client re-reads agent
 * state on the next refresh.
 *
 * @property nav The ship's nav state after the jump.
 * @property cooldown The jump cooldown.
 */
@Serializable
data class JumpResponseDto(
    val nav: ShipNavDto,
    val cooldown: CooldownDto
)

/**
 * A jump gate and the waypoints it connects to (SpaceTraders `JumpGate` schema). Returned by
 * `GET /systems/{systemSymbol}/waypoints/{waypointSymbol}/jump-gate`.
 *
 * @property symbol This jump gate's waypoint symbol.
 * @property connections The waypoint symbols of jump gates reachable from here (each may be in a
 *   different system).
 */
@Serializable
data class JumpGateDto(
    val symbol: String,
    val connections: List<String>
)

/**
 * A star system summary (SpaceTraders `System` schema). Returned by `GET /systems` (paginated)
 * and `GET /systems/{systemSymbol}`.
 *
 * @property symbol The system identifier (e.g. `"X1-DM91"`).
 * @property sectorSymbol The sector the system belongs to.
 * @property type The system/star type as a raw string (mapper keeps it as-is).
 * @property x Galaxy X coordinate.
 * @property y Galaxy Y coordinate.
 */
@Serializable
data class SystemDto(
    val symbol: String,
    val sectorSymbol: String,
    val type: String,
    val x: Int,
    val y: Int
)
