package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

/**
 * Wire-format representation of a single market trade record from the SpaceTraders API.
 *
 * **Pattern:** Data Transfer Object (DTO). DTOs are `@Serializable` data classes that carry
 * the API wire format. A mapper converts Dto → Domain at the API boundary, keeping
 * serialization concerns out of business logic. In a new project, create one DTO per
 * API response/request schema and a corresponding mapper.
 *
 * **In this project:** `MarketTransactionDto` is embedded in [RefuelResponseDto] (the fuel
 * purchase record) and in market buy/sell responses. It represents a completed trade — a
 * snapshot of what was traded, when, at what price, and by which ship. Two fields require
 * mapper conversion:
 * - [type] is a raw `String` (`"PURCHASE"` or `"SELL"`); the mapper converts it to the
 *   typed `TransactionType` enum.
 * - [timestamp] is an ISO-8601 `String`; the mapper parses it to
 *   `kotlinx.datetime.Instant` for the domain model.
 *
 * @property waypointSymbol The waypoint where the transaction occurred, in
 *   `<system>-<waypoint>` format (e.g. `"X1-OE-PM"`). The market must exist at this
 *   waypoint for the transaction to be valid.
 * @property shipSymbol The symbol of the ship that performed the trade. The ship must have
 *   been docked at [waypointSymbol] at the time of the transaction.
 * @property tradeSymbol The commodity identifier that was bought or sold
 *   (e.g. `"IRON_ORE"`, `"FUEL"`). Corresponds to a good listed in the market's trade
 *   catalogue.
 * @property type The direction of the trade as a raw string: `"PURCHASE"` when the agent
 *   bought goods, `"SELL"` when the agent sold. The mapper converts this to the typed
 *   `TransactionType` enum so the domain layer never works with magic strings.
 * @property units The number of units traded in this transaction.
 * @property pricePerUnit The credit cost (or credit revenue) per individual unit.
 * @property totalPrice The total credits spent or received. Always equals
 *   `pricePerUnit * units` but is included by the API as a convenience so the client
 *   does not have to multiply.
 * @property timestamp The moment the transaction was recorded by the server, as an
 *   ISO-8601 string (e.g. `"2025-04-16T12:34:56.000Z"`). The DTO keeps this as a raw
 *   `String`; the mapper parses it to `kotlinx.datetime.Instant` for the domain model.
 */
@Serializable
data class MarketTransactionDto(
    val waypointSymbol: String,
    val shipSymbol: String,
    val tradeSymbol: String,
    // Raw string enum: "PURCHASE" or "SELL". Mapper converts to TransactionType enum.
    val type: String,
    val units: Int,
    val pricePerUnit: Int,
    val totalPrice: Int,
    // ISO-8601 string. Mapper converts to kotlinx.datetime.Instant.
    val timestamp: String
)
