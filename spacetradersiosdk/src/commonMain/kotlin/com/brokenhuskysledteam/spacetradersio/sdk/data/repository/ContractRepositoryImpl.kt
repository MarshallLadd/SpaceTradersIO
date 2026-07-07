package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ContractsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.SpaceTradersDatabase
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.upsert
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ContractMeta
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractTab
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.ContractRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ContractRepositoryImpl(
    private val contractsApi: ContractsApi,
    private val database: SpaceTradersDatabase
) : ContractRepository {

    override fun observeContracts(tab: ContractTab, limit: Long, offset: Long): Flow<List<Contract>> {
        val rowsFlow = when (tab) {
            ContractTab.ACTIVE -> database.contractQueries
                .selectActiveTab(limit, offset)
                .asFlow().mapToList(Dispatchers.Default)
            ContractTab.HISTORY -> database.contractQueries
                .selectHistoryTab(limit, offset)
                .asFlow().mapToList(Dispatchers.Default)
        }
        return rowsFlow.map { rows ->
            rows.map { row ->
                val goods = database.contractDeliverGoodQueries
                    .selectByContractId(row.id).executeAsList()
                row.toDomain(goods)
            }
        }
    }

    override suspend fun refreshContracts(page: Int, limit: Int): ContractMeta {
        val response = contractsApi.getMyContracts(page, limit)
        database.transaction {
            response.data.forEach { dto ->
                val contract = dto.toDomain()
                database.contractQueries.upsert(contract)
                database.contractDeliverGoodQueries.deleteByContractId(contract.id)
                contract.terms.deliverGoods.forEach { good ->
                    database.contractDeliverGoodQueries.upsert(contract.id, good)
                }
            }
        }
        return ContractMeta(
            total = response.meta.total,
            page = response.meta.page,
            limit = response.meta.limit
        )
    }

    override suspend fun acceptContract(contractId: String): Contract {
        val dto = contractsApi.acceptContract(contractId).contract
        val contract = dto.toDomain()
        upsertContract(contract)
        return contract
    }

    override suspend fun fulfillContract(contractId: String): Contract {
        val dto = contractsApi.fulfillContract(contractId).contract
        val contract = dto.toDomain()
        upsertContract(contract)
        return contract
    }

    override suspend fun upsertContract(contract: Contract) {
        database.transaction {
            database.contractQueries.upsert(contract)
            database.contractDeliverGoodQueries.deleteByContractId(contract.id)
            contract.terms.deliverGoods.forEach { good ->
                database.contractDeliverGoodQueries.upsert(contract.id, good)
            }
        }
    }
}
