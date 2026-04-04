package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ContractsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract

class GetMyContractsUseCase(private val contractsApi: ContractsApi) {
    suspend operator fun invoke(page: Int = 1, limit: Int = 20): List<Contract> =
        contractsApi.getMyContracts(page, limit).data.map { it.toDomain() }
}
