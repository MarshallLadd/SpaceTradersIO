package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response for `POST /my/ships/{shipSymbol}/extract` and `.../extract/survey`.
 *
 * @property extraction What was extracted (ship + yield).
 * @property cooldown The action cooldown incurred; drives the auto-refresh + UI countdown.
 * @property cargo The ship's cargo after the extraction (with the extracted units added).
 */
@Serializable
data class ExtractResponseDto(
    val extraction: ExtractionDto,
    val cooldown: CooldownDto,
    val cargo: ShipCargoDto
)

/**
 * Details of a single extraction.
 *
 * @property shipSymbol The ship that extracted.
 * @property yieldResult The good and quantity extracted (JSON key `yield`).
 */
@Serializable
data class ExtractionDto(
    val shipSymbol: String,
    @SerialName("yield") val yieldResult: ExtractionYieldDto
)

/**
 * A single extraction yield.
 *
 * @property symbol The good extracted (e.g. `"IRON_ORE"`).
 * @property units Units of the good placed into the cargo hold.
 */
@Serializable
data class ExtractionYieldDto(
    val symbol: String,
    val units: Int
)

/**
 * Response for `POST /my/ships/{shipSymbol}/survey`.
 *
 * @property cooldown The survey cooldown.
 * @property surveys The surveys created; each can be passed to extract/survey for better yields.
 */
@Serializable
data class CreateSurveyResponseDto(
    val cooldown: CooldownDto,
    val surveys: List<SurveyDto>
)

/**
 * A survey of a mineable location. Round-trips: obtained from `create-survey` and passed back
 * as the request body of `extract/survey`.
 *
 * @property signature Unique signature verified when extracting with this survey.
 * @property symbol The waypoint symbol the survey is for.
 * @property deposits The deposits that may be extracted with this survey.
 * @property expiration ISO-8601 expiry after which the survey is unusable.
 * @property size Deposit size (`"SMALL"`, `"MODERATE"`, `"LARGE"`).
 */
@Serializable
data class SurveyDto(
    val signature: String,
    val symbol: String,
    val deposits: List<SurveyDepositDto>,
    val expiration: String,
    val size: String
)

/**
 * A surveyed deposit.
 *
 * @property symbol The deposit good symbol (e.g. `"IRON_ORE"`).
 */
@Serializable
data class SurveyDepositDto(
    val symbol: String
)

/**
 * Request body for `POST /my/ships/{shipSymbol}/jettison`.
 *
 * @property symbol The good to jettison.
 * @property units The number of units to jettison.
 */
@Serializable
data class JettisonRequestDto(
    val symbol: String,
    val units: Int
)

/**
 * Response for `POST /my/ships/{shipSymbol}/jettison`.
 *
 * @property cargo The ship's cargo after jettisoning.
 */
@Serializable
data class JettisonResponseDto(
    val cargo: ShipCargoDto
)
