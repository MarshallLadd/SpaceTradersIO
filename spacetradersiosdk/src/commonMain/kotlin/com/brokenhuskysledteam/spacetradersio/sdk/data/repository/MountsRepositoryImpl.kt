package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.MountsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipMount
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.MountsRepository

/** Read-through implementation of [MountsRepository]. */
class MountsRepositoryImpl(private val mountsApi: MountsApi) : MountsRepository {
    override suspend fun getMounts(shipSymbol: String): List<ShipMount> =
        mountsApi.getMounts(shipSymbol).map { it.toDomain() }
}
