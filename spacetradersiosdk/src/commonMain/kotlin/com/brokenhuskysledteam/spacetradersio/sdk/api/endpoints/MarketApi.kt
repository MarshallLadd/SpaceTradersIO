package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ApiResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MarketDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.TradeCargoRequestDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.TradeCargoResponseDto
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Endpoints for viewing a market and trading cargo (SpaceTraders "Systems" + "Fleet" tags).
 *
 * **Pattern:** Interface + Impl for API clients (see [FleetApi]). The interface lets use-case
 * and repository tests inject a hand-written fake; [MarketApiImpl] performs the real HTTP.
 *
 * **In this project:** Powers the market screen (view goods/prices) and the buy/sell actions.
 * `getMarket` returns live [MarketDto.tradeGoods] pricing only when the agent has a ship at the
 * waypoint; otherwise only the catalogue is present. Buy and sell both return the ship's updated
 * cargo, the agent's new credit balance, and a transaction receipt.
 */
interface MarketApi {

    /**
     * Fetches the market at a waypoint via `GET /systems/{systemSymbol}/waypoints/{waypointSymbol}/market`.
     *
     * @param systemSymbol The system containing the waypoint (e.g. `"X1-DM91"`).
     * @param waypointSymbol The marketplace waypoint (e.g. `"X1-DM91-A1"`).
     * @return [MarketDto]. `tradeGoods`/`transactions` are populated only when a ship of the
     *   agent is present at the waypoint.
     */
    suspend fun getMarket(systemSymbol: String, waypointSymbol: String): MarketDto

    /**
     * Buys cargo from the market via `POST /my/ships/{shipSymbol}/purchase`.
     *
     * The ship must be docked at a waypoint whose market sells [tradeSymbol]. Credits are
     * deducted from the agent's balance.
     *
     * @param shipSymbol The docked ship performing the purchase.
     * @param tradeSymbol The good to buy (e.g. `"FOOD"`).
     * @param units The number of units to buy.
     * @return [TradeCargoResponseDto] with the updated cargo, transaction, and agent.
     */
    suspend fun purchaseCargo(shipSymbol: String, tradeSymbol: String, units: Int): TradeCargoResponseDto

    /**
     * Sells cargo to the market via `POST /my/ships/{shipSymbol}/sell`.
     *
     * The ship must be docked at a waypoint whose market buys [tradeSymbol], and hold at least
     * [units] of it. Credits are added to the agent's balance.
     *
     * @param shipSymbol The docked ship performing the sale.
     * @param tradeSymbol The good to sell (e.g. `"IRON_ORE"`).
     * @param units The number of units to sell.
     * @return [TradeCargoResponseDto] with the updated cargo, transaction, and agent.
     */
    suspend fun sellCargo(shipSymbol: String, tradeSymbol: String, units: Int): TradeCargoResponseDto
}

/**
 * Production implementation of [MarketApi] backed by the real SpaceTraders HTTP API.
 *
 * @param client The shared [SpaceTradersClient]. Trade endpoints use [SpaceTradersClient.authenticated];
 *   the market GET is authenticated too so live pricing is returned when a ship is present.
 */
class MarketApiImpl(private val client: SpaceTradersClient) : MarketApi {

    override suspend fun getMarket(systemSymbol: String, waypointSymbol: String): MarketDto =
        client.authenticated.get("systems/$systemSymbol/waypoints/$waypointSymbol/market")
            .body<ApiResponse<MarketDto>>().data

    override suspend fun purchaseCargo(shipSymbol: String, tradeSymbol: String, units: Int): TradeCargoResponseDto =
        client.authenticated.post("my/ships/$shipSymbol/purchase") {
            setBody(TradeCargoRequestDto(symbol = tradeSymbol, units = units))
        }.body<ApiResponse<TradeCargoResponseDto>>().data

    override suspend fun sellCargo(shipSymbol: String, tradeSymbol: String, units: Int): TradeCargoResponseDto =
        client.authenticated.post("my/ships/$shipSymbol/sell") {
            setBody(TradeCargoRequestDto(symbol = tradeSymbol, units = units))
        }.body<ApiResponse<TradeCargoResponseDto>>().data
}
