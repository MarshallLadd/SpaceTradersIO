package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import kotlin.time.Instant

/**
 * The outcome of an extraction (with or without a survey).
 *
 * @property shipSymbol The ship that extracted.
 * @property yieldSymbol The good extracted (e.g. `"IRON_ORE"`).
 * @property yieldUnits Units of the good added to the hold.
 * @property cooldown The extraction cooldown (the ship cannot extract again until it clears).
 * @property cargo The ship's cargo after the extraction.
 */
data class ExtractResult(
    val shipSymbol: String,
    val yieldSymbol: String,
    val yieldUnits: Int,
    val cooldown: Cooldown,
    val cargo: ShipCargo
)

/**
 * The outcome of creating surveys.
 *
 * @property cooldown The survey cooldown.
 * @property surveys The surveys created; pass one to extract-with-survey for a targeted yield.
 */
data class SurveyResult(
    val cooldown: Cooldown,
    val surveys: List<Survey>
)

/**
 * A survey of a mineable location, used to target extractions for better yields.
 *
 * @property signature Unique signature verified at extraction time.
 * @property symbol The waypoint the survey is for.
 * @property deposits The deposit good symbols this survey can yield.
 * @property expiration When the survey expires and can no longer be used.
 * @property size Deposit size (`"SMALL"`, `"MODERATE"`, `"LARGE"`).
 */
data class Survey(
    val signature: String,
    val symbol: String,
    val deposits: List<String>,
    val expiration: Instant,
    val size: String
)
