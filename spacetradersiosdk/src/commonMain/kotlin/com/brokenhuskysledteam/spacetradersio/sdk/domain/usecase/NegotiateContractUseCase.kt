package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.ContractRepository

interface NegotiateContractUseCase {
    suspend operator fun invoke(shipSymbol: String): Contract
}

class NegotiateContractUseCaseImpl(
    private val fleetApi: FleetApi,
    private val contractRepository: ContractRepository
) : NegotiateContractUseCase {
    override suspend fun invoke(shipSymbol: String): Contract {
        val dto = fleetApi.negotiateContract(shipSymbol)
        val contract = dto.toDomain()
        contractRepository.upsertContract(contract)
        return contract
    }
}
