package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import kotlin.time.Instant

/**
 * Records a single completed buy or sell event at a marketplace waypoint.
 *
 * **Pattern:** Immutable audit-log domain model. In any project where an API action produces
 * a financial record, capture the receipt as an immutable `data class`. The properties mirror
 * exactly what the server returned at the moment of the trade — nothing is computed or
 * inferred. The [timestamp] anchors the record in time so a UI can sort or display a
 * transaction history without further API calls.
 *
 * **In this project:** [MarketTransaction] is embedded in [RefuelResult] and also returned
 * by buy/sell cargo endpoints. It gives the ViewModel a full audit trail of what was
 * traded, at what price, and when — useful for displaying a "last transaction" summary on
 * the Dashboard without storing a separate history.
 *
 * @property waypointSymbol The waypoint where the trade occurred, in
 *   `<system>-<waypoint>` format (e.g. `"X1-OE-PM"`). Identifies the market location.
 * @property shipSymbol The unique identifier of the ship that conducted the trade
 *   (e.g. `"MYAGENT-1"`). Needed when multiple ships can trade in the same session.
 * @property tradeSymbol The identifier of the commodity traded (e.g. `"IRON_ORE"`).
 *   Corresponds to a trade good defined in the SpaceTraders market catalogue.
 * @property type Either `"PURCHASE"` (agent bought goods) or `"SELL"` (agent sold goods).
 *   Modelled as a raw `String` here rather than an enum because the SpaceTraders API
 *   sometimes extends this list, and an unknown value should not crash the app.
 * @property units The number of units involved in this single transaction.
 * @property pricePerUnit The per-unit credit price at the moment of the trade. Market
 *   prices fluctuate based on supply/demand, so storing this per-transaction preserves
 *   accurate historical data.
 * @property totalPrice The total cost or revenue for the transaction.
 *   Always equal to [units] × [pricePerUnit], but provided by the server so the client
 *   does not need to recompute it (and risk integer overflow edge cases).
 * @property timestamp The exact moment the transaction was recorded by the server.
 *   Stored as [kotlinx.datetime.Instant] for platform-neutral time arithmetic in
 *   `commonMain`.
 */
data class MarketTransaction(
    val waypointSymbol: String,
    val shipSymbol: String,
    val tradeSymbol: String,
    // Raw String intentionally: new transaction types should not crash the app.
    val type: String,
    val units: Int,
    val pricePerUnit: Int,
    // Provided by the server; avoids client-side multiplication for display and audit.
    val totalPrice: Int,
    val timestamp: Instant
)
