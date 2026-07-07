package com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums

/**
 * The role a good plays at a market, controlling how its price behaves.
 *
 * - [IMPORT] — the market consumes this good; you sell it here (usually at a good price).
 * - [EXPORT] — the market produces this good; you buy it here (usually cheaply).
 * - [EXCHANGE] — freely traded both ways at a stable price (e.g. `FUEL`).
 */
enum class MarketTradeGoodType {
    IMPORT,
    EXPORT,
    EXCHANGE;

    companion object {
        /** Safe parse — unrecognized values fall back to [EXCHANGE] rather than throwing. */
        fun fromString(value: String): MarketTradeGoodType =
            entries.firstOrNull { it.name == value } ?: EXCHANGE
    }
}
