package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

// Mappers for mining: extract/survey responses → domain, and Survey → DTO for extract-with-survey.

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.CreateSurveyResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ExtractResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.SurveyDepositDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.SurveyDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ExtractResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Survey
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SurveyResult
import kotlin.time.Instant

/** Maps an [ExtractResponseDto] to an [ExtractResult] domain model. */
fun ExtractResponseDto.toDomain(): ExtractResult = ExtractResult(
    shipSymbol = extraction.shipSymbol,
    yieldSymbol = extraction.yieldResult.symbol,
    yieldUnits = extraction.yieldResult.units,
    cooldown = cooldown.toDomain(),
    cargo = cargo.toDomain()
)

/** Maps a [CreateSurveyResponseDto] to a [SurveyResult]. */
fun CreateSurveyResponseDto.toDomain(): SurveyResult = SurveyResult(
    cooldown = cooldown.toDomain(),
    surveys = surveys.map { it.toDomain() }
)

/** Maps a [SurveyDto] to a [Survey] domain model. */
fun SurveyDto.toDomain(): Survey = Survey(
    signature = signature,
    symbol = symbol,
    deposits = deposits.map { it.symbol },
    expiration = Instant.parse(expiration),
    size = size
)

/**
 * Maps a [Survey] back to a [SurveyDto] for the extract-with-survey request body. The API
 * verifies the [Survey.signature], so the survey must be sent back exactly as received.
 */
fun Survey.toDto(): SurveyDto = SurveyDto(
    signature = signature,
    symbol = symbol,
    deposits = deposits.map { SurveyDepositDto(it) },
    expiration = expiration.toString(),
    size = size
)
