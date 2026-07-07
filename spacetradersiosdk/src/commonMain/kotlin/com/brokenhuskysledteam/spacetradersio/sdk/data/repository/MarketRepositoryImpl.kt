package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.MarketApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Market
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.MarketRepository

/**
 * Read-through implementation of [MarketRepository]. Fetches fresh market data on every call
 * (market prices are too volatile to cache) and maps it to the domain model.
 *
 * @param marketApi Network layer for the market endpoint.
 */
class MarketRepositoryImpl(private val marketApi: MarketApi) : MarketRepository {

    override suspend fun getMarket(systemSymbol: String, waypointSymbol: String): Market =
        marketApi.getMarket(systemSymbol, waypointSymbol).toDomain()
}
