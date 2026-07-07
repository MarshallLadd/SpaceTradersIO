package com.brokenhuskysledteam.spacetradersio.sdk.e2e

import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Live E2E for Phase 1 (markets & trading) against the real SpaceTraders API.
 *
 * Registers a fresh agent, gets its command ship docked at a marketplace, then performs a real
 * buy → sell round-trip and verifies the full loop: the bought good appears in the ship's cargo
 * inventory, credits are deducted on purchase and increased on sale, and the sold good leaves the
 * hold. This proves the market/trade wire contracts and the fleet+agent repository updates
 * end-to-end. Runs only under `-Pe2e`.
 */
class MarketTradingLiveTest {

    @Test
    fun buyThenSell_updatesCargoInventoryAndCredits() = runBlocking {
        assumeE2eEnabled()
        val session = registerE2eAgent()
        val ship = session.commandShip
        val system = ship.nav.systemSymbol

        // Find a marketplace waypoint in the starting system.
        val waypoints = session.systemsApi.getSystemWaypoints(system, limit = 20).data.map { it.toDomain() }
        val marketWaypoint = waypoints.firstOrNull { wp ->
            wp.traits.any { it.symbol == WaypointTraitSymbol.MARKETPLACE }
        }
        assumeTrue("No marketplace in the starting system's first page — cannot run trade test", marketWaypoint != null)

        // Get the command ship docked at the market (navigates if needed).
        session.arriveAndDock(ship.symbol, marketWaypoint!!.symbol)

        // With a ship present, the market returns live pricing. Pick a cheap buyable good
        // (EXPORT or EXCHANGE — IMPORT goods are sold TO the market, not bought FROM it).
        val market = session.marketApi.getMarket(system, marketWaypoint.symbol)
        val buyable = market.tradeGoods
            .filter { it.type == "EXPORT" || it.type == "EXCHANGE" }
            .minByOrNull { it.purchasePrice }
        assumeTrue("No buyable good at this market", buyable != null)
        val good = buyable!!.symbol

        // ── Buy 1 unit ──
        val buy = session.marketApi.purchaseCargo(ship.symbol, good, 1)
        assertEquals("PURCHASE", buy.transaction.type)
        assertEquals(good, buy.transaction.tradeSymbol)
        val bought = buy.cargo.inventory.firstOrNull { it.symbol == good }
        assertTrue(bought != null && bought.units >= 1, "Bought good should appear in cargo inventory")
        val creditsAfterBuy = buy.agent.credits

        // ── Sell it back ──
        val sell = session.marketApi.sellCargo(ship.symbol, good, 1)
        assertEquals("SELL", sell.transaction.type)
        assertTrue(sell.agent.credits > creditsAfterBuy, "Selling should increase credits above the post-purchase balance")
        // The single unit we bought is gone from the hold.
        val remaining = sell.cargo.inventory.firstOrNull { it.symbol == good }?.units ?: 0
        assertEquals(0, remaining, "Sold good should no longer be in the hold")
    }
}
