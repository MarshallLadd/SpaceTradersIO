package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ContractDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.NavigateResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.PaginatedResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.RefuelResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi

// Shared stub for FleetApi in use case tests — never calls API methods (all throw).
// Used as the FleetRepositoryImpl constructor arg when the repo's network path is not
// exercised by the use case under test.
internal object StubFleetApi : FleetApi {
    override suspend fun getMyShips(page: Int, limit: Int): PaginatedResponse<ShipDto> = throw UnsupportedOperationException()
    override suspend fun getMyShip(shipSymbol: String): ShipDto = throw UnsupportedOperationException()
    override suspend fun orbitShip(shipSymbol: String): ShipNavDto = throw UnsupportedOperationException()
    override suspend fun dockShip(shipSymbol: String): ShipNavDto = throw UnsupportedOperationException()
    override suspend fun refuelShip(shipSymbol: String): RefuelResponseDto = throw UnsupportedOperationException()
    override suspend fun navigateShip(shipSymbol: String, waypointSymbol: String): NavigateResponseDto = throw UnsupportedOperationException()
    override suspend fun negotiateContract(shipSymbol: String): ContractDto = throw UnsupportedOperationException()
}
