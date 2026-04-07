package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AccountsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.FactionSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManager

data class RegistrationResult(
    val agent: Agent,
    val token: String
)

// Registers a new agent and creates the auth-scoped session via SessionManager.login().
// Defined as an interface so app-layer tests can substitute a fake without mock engines.
interface RegisterAgentUseCase {
    suspend operator fun invoke(
        symbol: String,
        faction: FactionSymbol = FactionSymbol.COSMIC,
        accountToken: String
    ): RegistrationResult
}

class RegisterAgentUseCaseImpl(
    private val accountsApi: AccountsApi,
    private val sessionManager: SessionManager
) : RegisterAgentUseCase {
    override suspend operator fun invoke(
        symbol: String,
        faction: FactionSymbol,
        accountToken: String
    ): RegistrationResult {
        val response = accountsApi.register(
            symbol = symbol,
            faction = faction.name,
            accountToken = accountToken
        )
        sessionManager.login(response.token)
        return RegistrationResult(
            agent = response.agent.toDomain(),
            token = response.token
        )
    }
}
