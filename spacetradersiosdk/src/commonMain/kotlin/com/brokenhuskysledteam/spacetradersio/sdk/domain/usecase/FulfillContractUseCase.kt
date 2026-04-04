package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ContractsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract

// Marks a completed contract as fulfilled and returns the updated domain model.
// Only succeeds if all delivery requirements have been met.
// The API response includes the updated agent (reward credits) but we discard it
// here — callers should refresh agent state separately.
class FulfillContractUseCase(private val contractsApi: ContractsApi) {
    suspend operator fun invoke(contractId: String): Contract =
        contractsApi.fulfillContract(contractId).contract.toDomain()
}
