package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

/**
 * Pagination metadata returned by
 * [com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.ContractRepository.refreshContracts].
 *
 * **Pattern:** Pagination envelope. SpaceTraders list endpoints return a `meta` object
 * alongside the data array. Extracting it into this dedicated type lets the repository
 * return both the data (written to the DB) and the pagination state (returned to the caller)
 * in a single suspend call. The ViewModel stores `total` in `LocalState` so it can compute
 * `totalPages = ceil(total / limit)` without re-fetching.
 *
 * **In this project:** `ContractsViewModel.loadPage()` calls `refreshContracts()`, stores
 * the returned `total` in `_localState`, and the `uiState` `combine` block derives
 * `totalPages`, `canGoNextPage`, and `canGoPrevPage` from it.
 *
 * @property total The total number of contracts on the server for this agent (across all
 *   pages). Used to compute the last page number.
 * @property page The page number that was fetched (1-based).
 * @property limit The page size used for this fetch.
 */
data class ContractMeta(val total: Int, val page: Int, val limit: Int)
