package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ContractsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract

class AcceptContractUseCase(private val contractsApi: ContractsApi) {
    suspend operator fun invoke(contractId: String): Contract =
        contractsApi.acceptContract(contractId).contract.toDomain()
}
