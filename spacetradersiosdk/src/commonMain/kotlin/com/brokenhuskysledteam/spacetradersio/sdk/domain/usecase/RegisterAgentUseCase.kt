package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AccountsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.FactionSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository

data class RegistrationResult(
    val agent: Agent,
    val token: String
)

interface RegisterAgentUseCase {
    suspend operator fun invoke(
        symbol: String,
        faction: FactionSymbol = FactionSymbol.COSMIC
    ): RegistrationResult
}

// Registers a new agent with the SpaceTraders API, then immediately
// persists the returned bearer token so subsequent authenticated
// requests can be made without re-registering.
class RegisterAgentUseCaseImpl(
    private val accountsApi: AccountsApi,
    private val tokenRepository: TokenRepository
) : RegisterAgentUseCase {
    override suspend operator fun invoke(
        symbol: String,
        faction: FactionSymbol
    ): RegistrationResult {
        val response = accountsApi.register(
            symbol = symbol,
            faction = faction.name
        )
        tokenRepository.saveToken(response.token)
        return RegistrationResult(
            agent = response.agent.toDomain(),
            token = response.token
        )
    }
}
