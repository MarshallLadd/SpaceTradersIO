package com.brokenhuskysledteam.spacetradersio.sdk.domain.repository

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Market

/**
 * Read access to marketplace data.
 *
 * **Pattern:** Read-through repository (no local cache). Unlike ships/agents/contracts, market
 * pricing is highly volatile — it changes with every trade by any player — so caching it would
 * serve stale prices. [getMarket] therefore fetches fresh from the network on each call and maps
 * straight to the domain model. Buy/sell are not here; they are use cases that update the fleet
 * and agent repositories (see `BuyCargoUseCase` / `SellCargoUseCase`).
 */
interface MarketRepository {

    /**
     * Fetches the current market at a waypoint.
     *
     * @param systemSymbol The system containing the waypoint (e.g. `"X1-DM91"`).
     * @param waypointSymbol The marketplace waypoint (e.g. `"X1-DM91-A1"`).
     * @return The [Market]. Its `tradeGoods` (live pricing) is populated only when the agent
     *   has a ship at the waypoint; otherwise only the catalogue is present.
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersApiException
     *   if the waypoint has no marketplace or the request fails.
     */
    suspend fun getMarket(systemSymbol: String, waypointSymbol: String): Market
}
