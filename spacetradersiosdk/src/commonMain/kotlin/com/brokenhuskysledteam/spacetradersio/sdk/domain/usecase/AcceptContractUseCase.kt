package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ContractsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract

// Accepts a contract and returns the updated domain model.
// The API response also includes the updated agent (credits change on accept)
// but we discard it here — callers should refresh agent state separately.
class AcceptContractUseCase(private val contractsApi: ContractsApi) {
    suspend operator fun invoke(contractId: String): Contract =
        contractsApi.acceptContract(contractId).contract.toDomain()
}
