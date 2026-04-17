package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ContractsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract

/**
 * Marks a completed contract as fulfilled and returns the updated [Contract] domain model.
 *
 * **Pattern:** Callable use case. `operator fun invoke()` lets callers write
 * `fulfillContractUseCase(id)` naturally. Like [AcceptContractUseCase], this class is
 * concrete rather than an interface + Impl pair. In a new project, apply the interface split
 * (see [DockShipUseCase]) when the ViewModel injecting this use case needs to be unit-tested
 * with a fake; keep the concrete class when callers are thin or the class is unlikely to be
 * faked in tests.
 *
 * **Thin delegation — kept for layer consistency:** This use case is a single API call with
 * a DTO-to-domain mapping. It does not add orchestration today, but it exists for two reasons:
 * 1. **Layer consistency** — ViewModels in this project never import from the `api` package.
 *    All business actions go through the use-case layer regardless of complexity.
 * 2. **Future extensibility** — fulfillment could later require verifying all delivery
 *    requirements before calling the API, or triggering a local contract-cache update.
 *    The use case is the correct place for that logic when the need arises.
 *
 * **Fulfillment precondition:** The SpaceTraders API will reject this call if not all
 * delivery requirements on the contract have been met. That precondition is enforced
 * server-side; this use case does not perform a client-side check. If you want to surface
 * a friendlier error before the round trip, add a pre-check here.
 *
 * **Agent credits side-effect:** The API response includes an updated agent object (the
 * fulfillment reward has been credited). That data is discarded here. Callers that need
 * up-to-date credit information should refresh agent state separately after this call.
 *
 * **In this project:** Called from the contracts ViewModel once the player has delivered
 * all required cargo and taps "Fulfill". The returned [Contract] reflects the fulfilled state.
 *
 * @param contractsApi The API endpoint for contract operations.
 */
class FulfillContractUseCase(private val contractsApi: ContractsApi) {
    /**
     * Fulfills the contract identified by [contractId].
     *
     * Calls `POST /my/contracts/{contractId}/fulfill` and maps the response DTO to the
     * [Contract] domain model. The API response's `agent` field (reward credits applied)
     * is discarded — refresh agent state separately if needed.
     *
     * @param contractId The unique identifier of the contract to fulfill.
     * @return The updated [Contract] reflecting its fulfilled state.
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.api.exception.SpaceTradersApiException
     *   If the contract does not exist, has not been accepted, delivery requirements are
     *   unmet, or the contract has expired.
     */
    suspend operator fun invoke(contractId: String): Contract =
        contractsApi.fulfillContract(contractId).contract.toDomain()
}
