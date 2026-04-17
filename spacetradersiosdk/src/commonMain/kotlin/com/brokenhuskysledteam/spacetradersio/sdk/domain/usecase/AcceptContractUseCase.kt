package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ContractsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract

/**
 * Accepts an available contract and returns the updated [Contract] domain model.
 *
 * **Pattern:** Callable use case. `operator fun invoke()` lets callers write
 * `acceptContractUseCase(id)` rather than `acceptContractUseCase.invoke(id)`. Because this
 * class is concrete (no interface), tests that need to substitute it should use a hand-written
 * fake subclass or restructure the caller to accept the interface instead. In a new project,
 * prefer the interface + Impl split (see [DockShipUseCase] for an example) when the use case
 * is injected into a class that needs to be unit-tested without a real network.
 *
 * **Thin delegation — kept for layer consistency:** This use case does not add orchestration
 * beyond the API call and DTO mapping. It exists because:
 * 1. **Layer consistency** — all business operations in this project flow through the use-case
 *    layer, so ViewModels never call API or repository interfaces directly. A missing use case
 *    here would force the ViewModel to import from the `api` layer, breaking the dependency rule.
 * 2. **Future extensibility** — if accepting a contract later requires a pre-condition check
 *    (e.g., verifying the contract is in `OFFERED` state before calling the API) or a
 *    post-action side-effect (e.g., updating a local contract cache), the logic has a natural
 *    home here without touching the ViewModel.
 *
 * **Agent credits side-effect:** The API response contains an updated agent object (credits
 * change when a contract is accepted). That data is intentionally discarded here. Callers
 * that need up-to-date credit information should refresh agent state separately after this
 * call returns. This keeps the use case focused on a single return type.
 *
 * **In this project:** Called from the contracts ViewModel after the user taps "Accept". The
 * returned [Contract] is used to refresh the displayed contract list.
 *
 * @param contractsApi The API endpoint for contract operations.
 */
class AcceptContractUseCase(private val contractsApi: ContractsApi) {
    /**
     * Accepts the contract identified by [contractId].
     *
     * Calls `POST /my/contracts/{contractId}/accept` and maps the response DTO to the
     * [Contract] domain model. The API response's `agent` field (with updated credits)
     * is discarded — refresh agent state separately if needed.
     *
     * @param contractId The unique identifier of the contract to accept.
     * @return The updated [Contract] reflecting its new accepted state.
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.api.exception.SpaceTradersApiException
     *   If the contract does not exist, has already been accepted, or has expired.
     */
    suspend operator fun invoke(contractId: String): Contract =
        contractsApi.acceptContract(contractId).contract.toDomain()
}
