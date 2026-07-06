package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ApiResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MountModificationRequestDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MountModificationResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipMountDto
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Endpoints for viewing and modifying a ship's mounts (SpaceTraders "Fleet" tag).
 *
 * **Pattern:** Isolated interface + Impl (like [MarketApi]). Kept separate from [FleetApi] so
 * adding mount operations does not force every [FleetApi] fake/stub in the test suite to grow.
 *
 * **In this project:** Powers the mounts screen. Install and remove both require the ship to be
 * **docked at a waypoint with a SHIPYARD**; install additionally requires the mount to be in the
 * ship's cargo hold (buy it at a market first). Both return the updated agent, mount list, cargo,
 * and a modification-fee transaction.
 */
interface MountsApi {

    /**
     * Lists the mounts currently installed on a ship via `GET /my/ships/{shipSymbol}/mounts`.
     *
     * @param shipSymbol The ship whose mounts to list.
     * @return The installed [ShipMountDto]s.
     */
    suspend fun getMounts(shipSymbol: String): List<ShipMountDto>

    /**
     * Installs a mount via `POST /my/ships/{shipSymbol}/mounts/install`.
     *
     * Requires the ship docked at a shipyard with the mount already in its cargo hold.
     *
     * @param shipSymbol The ship to install onto.
     * @param mountSymbol The mount symbol to install (e.g. `"MOUNT_MINING_LASER_I"`).
     * @return [MountModificationResponseDto] with the updated agent, mounts, cargo, and fee.
     */
    suspend fun installMount(shipSymbol: String, mountSymbol: String): MountModificationResponseDto

    /**
     * Removes a mount via `POST /my/ships/{shipSymbol}/mounts/remove`.
     *
     * Requires the ship docked at a shipyard. The removed mount is returned to the cargo hold.
     *
     * @param shipSymbol The ship to remove from.
     * @param mountSymbol The mount symbol to remove.
     * @return [MountModificationResponseDto] with the updated agent, mounts, cargo, and fee.
     */
    suspend fun removeMount(shipSymbol: String, mountSymbol: String): MountModificationResponseDto
}

/** Production implementation of [MountsApi]. */
class MountsApiImpl(private val client: SpaceTradersClient) : MountsApi {

    override suspend fun getMounts(shipSymbol: String): List<ShipMountDto> =
        client.authenticated.get("my/ships/$shipSymbol/mounts")
            .body<ApiResponse<List<ShipMountDto>>>().data

    override suspend fun installMount(shipSymbol: String, mountSymbol: String): MountModificationResponseDto =
        client.authenticated.post("my/ships/$shipSymbol/mounts/install") {
            setBody(MountModificationRequestDto(mountSymbol))
        }.body<ApiResponse<MountModificationResponseDto>>().data

    override suspend fun removeMount(shipSymbol: String, mountSymbol: String): MountModificationResponseDto =
        client.authenticated.post("my/ships/$shipSymbol/mounts/remove") {
            setBody(MountModificationRequestDto(mountSymbol))
        }.body<ApiResponse<MountModificationResponseDto>>().data
}
