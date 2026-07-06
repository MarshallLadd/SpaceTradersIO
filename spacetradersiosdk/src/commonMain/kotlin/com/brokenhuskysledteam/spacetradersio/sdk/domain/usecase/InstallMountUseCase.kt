package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MountModificationResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.MountsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.MountModificationResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.AgentRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository

/**
 * Maps a mount-modification response to a domain result. Shared by install and remove.
 */
internal fun MountModificationResponseDto.toResult(): MountModificationResult = MountModificationResult(
    agent = agent.toDomain(),
    mounts = mounts.map { it.toDomain() },
    cargo = cargo.toDomain(),
    transaction = transaction.toDomain()
)

/**
 * Installs a mount, then updates the ship's cargo (the mount is consumed from the hold) and the
 * agent's credit balance (the shipyard fee) in their local repositories.
 *
 * **Pattern:** Multi-repository use case (like `RefuelShipUseCase`/`BuyCargoUseCase`).
 *
 * @param mountsApi Live API client. Requires the ship docked at a shipyard with the mount in cargo.
 * @param fleetRepository Updated with the ship's post-install cargo.
 * @param agentRepository Updated with the agent's post-fee credits.
 */
interface InstallMountUseCase {
    /**
     * @param shipSymbol The ship to install onto.
     * @param mountSymbol The mount to install.
     * @return The [MountModificationResult] with the updated agent, mounts, cargo, and fee.
     */
    suspend operator fun invoke(shipSymbol: String, mountSymbol: String): MountModificationResult
}

/** Production implementation of [InstallMountUseCase]. */
class InstallMountUseCaseImpl(
    private val mountsApi: MountsApi,
    private val fleetRepository: FleetRepository,
    private val agentRepository: AgentRepository
) : InstallMountUseCase {
    override suspend operator fun invoke(shipSymbol: String, mountSymbol: String): MountModificationResult {
        val result = mountsApi.installMount(shipSymbol, mountSymbol).toResult()
        fleetRepository.updateShipCargo(shipSymbol, result.cargo)
        agentRepository.saveAgent(result.agent)
        return result
    }
}
