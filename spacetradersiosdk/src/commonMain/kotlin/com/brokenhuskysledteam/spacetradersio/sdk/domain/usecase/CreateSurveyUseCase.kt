package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.MiningApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SurveyResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository

/**
 * Creates surveys of the current asteroid and updates the ship's cooldown locally (surveying
 * incurs a cooldown). The returned surveys are held by the caller (ViewModel) and passed to
 * [ExtractWithSurveyUseCase] for targeted extraction.
 *
 * @param miningApi Live API client. Requires the ship in orbit with a surveyor mount.
 * @param fleetRepository Updated with the survey cooldown.
 */
interface CreateSurveyUseCase {
    /** @param shipSymbol The orbiting, surveyor-equipped ship. @return the [SurveyResult]. */
    suspend operator fun invoke(shipSymbol: String): SurveyResult
}

/** Production implementation of [CreateSurveyUseCase]. */
class CreateSurveyUseCaseImpl(
    private val miningApi: MiningApi,
    private val fleetRepository: FleetRepository
) : CreateSurveyUseCase {
    override suspend operator fun invoke(shipSymbol: String): SurveyResult {
        val result = miningApi.createSurvey(shipSymbol).toDomain()
        fleetRepository.updateShipCooldown(shipSymbol, result.cooldown)
        return result
    }
}
