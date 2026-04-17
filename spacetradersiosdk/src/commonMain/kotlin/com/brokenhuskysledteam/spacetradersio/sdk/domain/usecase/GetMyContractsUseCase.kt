package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ContractsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract

/**
 * Fetches one page of the authenticated agent's contracts from the API.
 *
 * **Pattern:** Callable use case. `operator fun invoke()` with default parameters lets
 * callers omit pagination arguments when they only need the first page. Like the other
 * contracts use cases, this is a concrete class (no interface split) because it is a
 * thin delegation. In a new project, apply the interface + Impl pattern when you need to
 * substitute a fake in ViewModel tests (see [DockShipUseCase] for that pattern).
 *
 * **Thin delegation — kept for layer consistency and boundary enforcement:** The body is
 * a single mapped API call with no additional business logic. It exists because:
 * 1. **Layer consistency** — ViewModels do not import from `api.*`. Every data-access
 *    operation is routed through the use-case layer.
 * 2. **Future extensibility** — future requirements might add local caching, merging with
 *    offline data, or filtering by contract state. All of that belongs here.
 *
 * **Pagination responsibility:** This use case returns a single page. It does **not**
 * exhaust all pages automatically. The caller (typically a ViewModel) is responsible for
 * pagination logic: tracking the current page number, deciding when to load the next page,
 * and accumulating results. This keeps the use case simple and composable. If you want an
 * "exhaust all pages" helper, implement it as a higher-level use case that calls this one
 * in a loop.
 *
 * **In this project:** Called from the contracts ViewModel on initial load and on
 * "load more" triggers. Default values (`page = 1`, `limit = 20`) handle the common case
 * of fetching the first page without arguments.
 *
 * @param contractsApi The API endpoint for contract operations, including paginated listing.
 */
class GetMyContractsUseCase(private val contractsApi: ContractsApi) {
    /**
     * Fetches a single page of contracts for the authenticated agent.
     *
     * Calls `GET /my/contracts` with the given pagination parameters and maps each
     * response DTO to a [Contract] domain model. The API `meta` object (total count,
     * current page) is not returned; callers that need it for UI pagination indicators
     * should extend this use case or read the raw API response directly.
     *
     * @param page The 1-based page number to retrieve. Defaults to `1`.
     * @param limit The maximum number of contracts per page. Defaults to `20`. The API
     *   caps this at `20` per request.
     * @return A list of [Contract] domain models for the requested page. May be empty if
     *   the agent has no contracts or the requested page is beyond the last page.
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.api.exception.SpaceTradersApiException
     *   If the request is unauthenticated or the API returns an error response.
     */
    suspend operator fun invoke(page: Int = 1, limit: Int = 20): List<Contract> =
        contractsApi.getMyContracts(page, limit).data.map { it.toDomain() }
}
