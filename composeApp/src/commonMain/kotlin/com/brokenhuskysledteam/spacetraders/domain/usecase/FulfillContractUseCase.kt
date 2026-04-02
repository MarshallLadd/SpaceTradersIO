package com.brokenhuskysledteam.spacetraders.domain.usecase

import com.brokenhuskysledteam.spacetraders.api.endpoints.ContractsApi
import com.brokenhuskysledteam.spacetraders.api.mapper.toDomain
import com.brokenhuskysledteam.spacetraders.domain.model.Contract

class FulfillContractUseCase(private val contractsApi: ContractsApi) {
    suspend operator fun invoke(contractId: String): Contract =
        contractsApi.fulfillContract(contractId).contract.toDomain()
}
