package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetraders.api.endpoints.ContractsApi
import com.brokenhuskysledteam.spacetraders.api.mapper.toDomain
import com.brokenhuskysledteam.spacetraders.domain.model.Contract

class AcceptContractUseCase(private val contractsApi: ContractsApi) {
    suspend operator fun invoke(contractId: String): Contract =
        contractsApi.acceptContract(contractId).contract.toDomain()
}
