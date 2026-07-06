package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.MountsApi
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.MountModificationResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.AgentRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository

/**
 * Removes a mount, then updates the ship's cargo (the removed mount returns to the hold) and the
 * agent's credit balance (the shipyard fee). The remove counterpart of [InstallMountUseCase].
 *
 * @param mountsApi Live API client. Requires the ship docked at a shipyard.
 * @param fleetRepository Updated with the ship's post-remove cargo.
 * @param agentRepository Updated with the agent's post-fee credits.
 */
interface RemoveMountUseCase {
    /**
     * @param shipSymbol The ship to remove from.
     * @param mountSymbol The mount to remove.
     * @return The [MountModificationResult] with the updated agent, mounts, cargo, and fee.
     */
    suspend operator fun invoke(shipSymbol: String, mountSymbol: String): MountModificationResult
}

/** Production implementation of [RemoveMountUseCase]. */
class RemoveMountUseCaseImpl(
    private val mountsApi: MountsApi,
    private val fleetRepository: FleetRepository,
    private val agentRepository: AgentRepository
) : RemoveMountUseCase {
    override suspend operator fun invoke(shipSymbol: String, mountSymbol: String): MountModificationResult {
        val result = mountsApi.removeMount(shipSymbol, mountSymbol).toResult()
        fleetRepository.updateShipCargo(shipSymbol, result.cargo)
        agentRepository.saveAgent(result.agent)
        return result
    }
}
