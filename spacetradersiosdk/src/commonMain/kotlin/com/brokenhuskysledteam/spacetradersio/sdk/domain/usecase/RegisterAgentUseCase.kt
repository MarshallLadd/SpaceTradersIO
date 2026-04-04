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

// Registers a new agent and persists the returned token.
// Implemented by [RegisterAgentUseCaseImpl]; defined as an interface
// so app-layer tests can substitute a fake without mock engines.
interface RegisterAgentUseCase {
    /**
     * Sends a registration request with the given [symbol] and [faction],
     * saves the returned bearer token, and returns the agent + token pair.
     */
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
