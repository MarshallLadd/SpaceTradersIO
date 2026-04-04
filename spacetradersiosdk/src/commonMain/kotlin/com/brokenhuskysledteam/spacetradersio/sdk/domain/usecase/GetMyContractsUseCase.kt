package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ContractsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract

// Fetches the authenticated agent's contracts and maps them to domain models.
// Supports pagination — callers can pass [page] and [limit] to control which
// slice of contracts to retrieve.
class GetMyContractsUseCase(private val contractsApi: ContractsApi) {
    suspend operator fun invoke(page: Int = 1, limit: Int = 20): List<Contract> =
        contractsApi.getMyContracts(page, limit).data.map { it.toDomain() }
}
