package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ApiResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.CreateSurveyResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ExtractResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.JettisonRequestDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.JettisonResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.SurveyDto
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Endpoints for mining: extracting resources, surveying, and jettisoning cargo
 * (SpaceTraders "Fleet" tag). Isolated interface + Impl (like [MarketApi]/[MountsApi]).
 *
 * **In this project:** Extraction requires the ship to be **in orbit** at an asteroid-type
 * waypoint with a mining mount. Extract and survey both incur a cooldown; the fleet repository
 * stores it and (via the RefreshScheduler) auto-refreshes the ship when it clears.
 */
interface MiningApi {

    /**
     * Extracts resources via `POST /my/ships/{shipSymbol}/extract`.
     *
     * @param shipSymbol The orbiting, mining-equipped ship.
     * @return [ExtractResponseDto] with the yield, cooldown, and updated cargo.
     */
    suspend fun extract(shipSymbol: String): ExtractResponseDto

    /**
     * Extracts using a survey via `POST /my/ships/{shipSymbol}/extract/survey`. The survey is
     * sent as the request body and must match one previously returned by [createSurvey].
     *
     * @param shipSymbol The orbiting, mining-equipped ship.
     * @param survey The survey to target the extraction with.
     * @return [ExtractResponseDto] with the yield, cooldown, and updated cargo.
     */
    suspend fun extractWithSurvey(shipSymbol: String, survey: SurveyDto): ExtractResponseDto

    /**
     * Creates surveys via `POST /my/ships/{shipSymbol}/survey`. Requires a surveyor mount.
     *
     * @param shipSymbol The orbiting, surveyor-equipped ship.
     * @return [CreateSurveyResponseDto] with the surveys and a cooldown.
     */
    suspend fun createSurvey(shipSymbol: String): CreateSurveyResponseDto

    /**
     * Jettisons cargo via `POST /my/ships/{shipSymbol}/jettison`.
     *
     * @param shipSymbol The ship to dump cargo from.
     * @param tradeSymbol The good to jettison.
     * @param units The number of units to jettison.
     * @return [JettisonResponseDto] with the updated cargo.
     */
    suspend fun jettison(shipSymbol: String, tradeSymbol: String, units: Int): JettisonResponseDto
}

/** Production implementation of [MiningApi]. */
class MiningApiImpl(private val client: SpaceTradersClient) : MiningApi {

    override suspend fun extract(shipSymbol: String): ExtractResponseDto =
        client.authenticated.post("my/ships/$shipSymbol/extract") { setBody("{}") }
            .body<ApiResponse<ExtractResponseDto>>().data

    override suspend fun extractWithSurvey(shipSymbol: String, survey: SurveyDto): ExtractResponseDto =
        client.authenticated.post("my/ships/$shipSymbol/extract/survey") { setBody(survey) }
            .body<ApiResponse<ExtractResponseDto>>().data

    override suspend fun createSurvey(shipSymbol: String): CreateSurveyResponseDto =
        client.authenticated.post("my/ships/$shipSymbol/survey") { setBody("{}") }
            .body<ApiResponse<CreateSurveyResponseDto>>().data

    override suspend fun jettison(shipSymbol: String, tradeSymbol: String, units: Int): JettisonResponseDto =
        client.authenticated.post("my/ships/$shipSymbol/jettison") {
            setBody(JettisonRequestDto(tradeSymbol, units))
        }.body<ApiResponse<JettisonResponseDto>>().data
}
