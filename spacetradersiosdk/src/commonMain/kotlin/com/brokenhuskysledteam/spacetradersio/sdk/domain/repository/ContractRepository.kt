package com.brokenhuskysledteam.spacetradersio.sdk.domain.repository

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ContractMeta
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractTab
import kotlinx.coroutines.flow.Flow

interface ContractRepository {
    fun observeContracts(tab: ContractTab, limit: Long, offset: Long): Flow<List<Contract>>
    suspend fun refreshContracts(page: Int, limit: Int): ContractMeta
    suspend fun acceptContract(contractId: String): Contract
    suspend fun fulfillContract(contractId: String): Contract
    suspend fun upsertContract(contract: Contract)
}
