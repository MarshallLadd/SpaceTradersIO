package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AccountsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.FactionSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManager

/**
 * The result of a successful agent registration, bundling everything the caller needs
 * to proceed into an authenticated session.
 *
 * Returning a dedicated result type (rather than just the [Agent]) avoids surfacing the
 * raw token string as a loose return value and makes the two pieces of data travel
 * together. In a new project, define a result class whenever a single operation produces
 * multiple distinct values that the caller always needs together.
 *
 * @property agent The newly created agent's domain model, populated from the API response.
 * @property token The AgentToken (JWT) issued by the API. This token authenticates all
 *   subsequent fleet and contract operations for this agent.
 */
data class RegistrationResult(
    val agent: Agent,
    val token: String
)

/**
 * Registers a new agent with the SpaceTraders API and establishes an authenticated session.
 *
 * **Pattern:** Callable use case. Use cases encapsulate a single business operation behind
 * `operator fun invoke()`, allowing callers to write `useCase(args)` naturally. Pairing an
 * interface with an implementation enables ViewModel testing via fakes without a mocking
 * library. In a new project, add a use case when the operation involves multi-step
 * orchestration or cross-repository coordination; skip it for thin single-API delegations
 * if you prefer fewer layers.
 *
 * **Value added (not a thin delegation):** Registration is a two-step operation. After the
 * API call succeeds, [SessionManager.login] is called to store the returned token and make
 * the SDK's HTTP client authenticated for all subsequent requests. Without this use case,
 * every caller would need to know both steps and the dependency on [SessionManager], leaking
 * infrastructure concerns into the ViewModel. The use case owns that cross-cutting concern.
 *
 * **In this project:** Called from `AuthViewModel` when the user submits the registration
 * form. The ViewModel receives a [RegistrationResult] and navigates to the dashboard;
 * it does not touch [SessionManager] directly.
 *
 * @param accountsApi The API endpoint for account-level operations. Registration uses the
 *   `AccountToken` (not the `AgentToken`) — the server validates the JWT `sub` claim.
 * @param sessionManager Stores the agent token and gates authenticated requests. Injected
 *   so the interface can be tested with a fake [SessionManager] that records calls.
 */
interface RegisterAgentUseCase {
    /**
     * Registers a new agent and activates an authenticated session.
     *
     * Calls `POST /register` with the provided symbol, faction, and account token, then
     * calls [SessionManager.login] with the returned agent token so that all subsequent
     * SDK calls are automatically authenticated.
     *
     * @param symbol The desired agent call-sign (2–14 uppercase letters). The API enforces
     *   uniqueness across all active agents.
     * @param faction The starting faction for the new agent. Defaults to [FactionSymbol.COSMIC],
     *   which is the most common choice and always has an available headquarters.
     * @param accountToken A valid AccountToken JWT. Required by the API to create an agent
     *   under the calling account. Distinct from the AgentToken returned in the result.
     * @return A [RegistrationResult] containing the new [Agent] and the AgentToken string.
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.api.exception.SpaceTradersApiException
     *   If the API rejects the request (e.g., symbol already taken, invalid faction, or the
     *   wrong token type was supplied).
     */
    suspend operator fun invoke(
        symbol: String,
        faction: FactionSymbol = FactionSymbol.COSMIC,
        accountToken: String
    ): RegistrationResult
}

/**
 * Production implementation of [RegisterAgentUseCase].
 *
 * @param accountsApi Live Ktor-backed API client for the `/register` endpoint.
 * @param sessionManager Persists the agent token and makes the HTTP client authenticated.
 */
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
        // Activate the session immediately after registration so all subsequent SDK calls
        // carry the new AgentToken. This is the cross-cutting step that justifies the use
        // case — the ViewModel should never need to call sessionManager directly.
        sessionManager.login(response.token)
        return RegistrationResult(
            agent = response.agent.toDomain(),
            token = response.token
        )
    }
}
