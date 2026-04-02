package com.brokenhuskysledteam.spacetraders.domain.usecase

import com.brokenhuskysledteam.spacetraders.api.endpoints.ContractsApi
import com.brokenhuskysledteam.spacetraders.api.mapper.toDomain
import com.brokenhuskysledteam.spacetraders.domain.model.Contract

class GetMyContractsUseCase(private val contractsApi: ContractsApi) {
    suspend operator fun invoke(page: Int = 1, limit: Int = 20): List<Contract> =
        contractsApi.getMyContracts(page, limit).data.map { it.toDomain() }
}
