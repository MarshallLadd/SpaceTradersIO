package com.brokenhuskysledteam.spacetraders.domain.usecase

import com.brokenhuskysledteam.spacetraders.api.endpoints.AccountsApi
import com.brokenhuskysledteam.spacetraders.api.mapper.toDomain
import com.brokenhuskysledteam.spacetraders.domain.model.Agent
import com.brokenhuskysledteam.spacetraders.domain.model.enums.FactionSymbol
import com.brokenhuskysledteam.spacetraders.domain.repository.TokenRepository

data class RegistrationResult(
    val agent: Agent,
    val token: String
)

// Registers a new agent with the SpaceTraders API, then immediately
// persists the returned bearer token so subsequent authenticated
// requests can be made without re-registering.
class RegisterAgentUseCase(
    private val accountsApi: AccountsApi,
    private val tokenRepository: TokenRepository
) {
    suspend operator fun invoke(
        symbol: String,
        faction: FactionSymbol = FactionSymbol.COSMIC
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
