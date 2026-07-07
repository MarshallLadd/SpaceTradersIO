package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.MiningApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ExtractResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Survey
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository

/**
 * Extracts resources using a [Survey] for a targeted, higher yield. The survey counterpart of
 * [ExtractResourcesUseCase]; updates cargo and cooldown the same way.
 *
 * @param miningApi Live API client.
 * @param fleetRepository Updated with the post-extraction cargo and cooldown.
 */
interface ExtractWithSurveyUseCase {
    /**
     * @param shipSymbol The orbiting, mining-equipped ship.
     * @param survey The survey to target the extraction with (from `CreateSurveyUseCase`).
     * @return the extraction [ExtractResult].
     */
    suspend operator fun invoke(shipSymbol: String, survey: Survey): ExtractResult
}

/** Production implementation of [ExtractWithSurveyUseCase]. */
class ExtractWithSurveyUseCaseImpl(
    private val miningApi: MiningApi,
    private val fleetRepository: FleetRepository
) : ExtractWithSurveyUseCase {
    override suspend operator fun invoke(shipSymbol: String, survey: Survey): ExtractResult {
        val result = miningApi.extractWithSurvey(shipSymbol, survey.toDto()).toDomain()
        fleetRepository.updateShipCargo(shipSymbol, result.cargo)
        fleetRepository.updateShipCooldown(shipSymbol, result.cooldown)
        return result
    }
}
