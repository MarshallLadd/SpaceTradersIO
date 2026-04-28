package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ContractsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.ContractRepository

interface DeliverCargoUseCase {
    suspend operator fun invoke(
        contractId: String,
        shipSymbol: String,
        tradeSymbol: String,
        units: Int
    ): Contract
}

class DeliverCargoUseCaseImpl(
    private val contractsApi: ContractsApi,
    private val contractRepository: ContractRepository
) : DeliverCargoUseCase {
    override suspend fun invoke(contractId: String, shipSymbol: String, tradeSymbol: String, units: Int): Contract {
        val response = contractsApi.deliverCargo(contractId, shipSymbol, tradeSymbol, units)
        val contract = response.contract.toDomain()
        contractRepository.upsertContract(contract)
        return contract
    }
}
