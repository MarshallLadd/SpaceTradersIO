package com.brokenhuskysledteam.spacetradersio.sdk.domain.repository

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ContractMeta
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractTab
import kotlinx.coroutines.flow.Flow

/**
 * Defines all data operations for the player's contracts.
 *
 * **Pattern:** Repository interface in the domain layer. The interface lives in `domain/` so
 * that ViewModels can depend on it without any knowledge of how data is fetched or stored.
 * The actual storage and network logic is in `ContractRepositoryImpl` in the `data/` layer.
 * To apply this pattern in a new project: define every data operation your feature needs here,
 * then implement them in an `Impl` class that ViewModel tests replace with a hand-written fake.
 *
 * **Benefits of the interface boundary:**
 * - `ContractsViewModel` depends on `ContractRepository`, not the `Impl` → tests supply a
 *   `FakeContractRepository` with zero mocking libraries.
 * - The domain layer stays free of `data/` imports; the dependency arrow points inward.
 *
 * **In this project:** `ContractRepositoryImpl` persists contracts in two SQLDelight tables
 * (`contract` + `contract_deliver_good`) and fetches from the SpaceTraders contracts
 * endpoints. Callers only see this interface and are unaware of those details.
 */
interface ContractRepository {

    /**
     * Returns a hot [Flow] that emits the locally-cached list of [Contract] objects
     * matching [tab] and re-emits whenever those rows change in the database.
     *
     * **Pattern:** Reactive offline-first observation with pagination. Back this with a
     * SQLDelight `asFlow().mapToList()` query that filters by `status` (derived from [tab])
     * and applies `LIMIT`/`OFFSET` for the current page. Any write to the `contract` table
     * triggers a new emission automatically.
     *
     * **In this project:** `ContractsViewModel` subscribes to this flow via `flatMapLatest`
     * keyed on a `QueryKey(tab, page, limit)`. When the user changes tab or page, a new
     * subscription replaces the old one without needing to cancel it explicitly.
     *
     * @param tab Determines which contracts to surface: [ContractTab.ACTIVE] maps to
     *   `status = UNACCEPTED or ACTIVE`; [ContractTab.HISTORY] maps to fulfilled/expired.
     * @param limit Maximum number of contracts to return (page size).
     * @param offset Number of rows to skip (= `(page - 1) * limit`).
     * @return A [Flow] that emits the matching contracts from the local cache. Emits an
     *   empty list when no contracts exist for [tab] on the current page.
     */
    fun observeContracts(tab: ContractTab, limit: Long, offset: Long): Flow<List<Contract>>

    /**
     * Fetches a page of contracts from the network and writes them to the local database.
     *
     * **Pattern:** Network-then-cache write. Callers do not receive the contracts directly;
     * instead [observeContracts] automatically emits the new values to all active collectors
     * once the DB writes complete. This keeps the data flow strictly unidirectional:
     * network → DB → Flow. The only value returned is [ContractMeta] (pagination totals),
     * which the ViewModel cannot derive from the DB alone.
     *
     * @param page 1-based page number to fetch.
     * @param limit Page size (contracts per page).
     * @return [ContractMeta] containing the server-side `total` count, which the ViewModel
     *   uses to compute `totalPages` for the pagination controls.
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.domain.error.SpaceTradersApiException
     *   if the API call fails.
     */
    suspend fun refreshContracts(page: Int, limit: Int): ContractMeta

    /**
     * Accepts the contract identified by [contractId] and persists the updated record.
     *
     * Calls `POST /my/contracts/{contractId}/accept`. On success the server deposits the
     * upfront payment and transitions the contract to `ACTIVE` status.
     *
     * @param contractId The [Contract.id] to accept.
     * @return The updated [Contract] with `accepted = true` and `status = ACTIVE`, which
     *   the ViewModel surfaces as a [com.brokenhuskysledteam.spacetradersio.ui.contracts.ContractActionResult].
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.domain.error.SpaceTradersApiException
     *   if the contract is already accepted, expired, or the API call fails.
     */
    suspend fun acceptContract(contractId: String): Contract

    /**
     * Fulfills the contract identified by [contractId] and persists the updated record.
     *
     * Calls `POST /my/contracts/{contractId}/fulfill`. On success the server deposits the
     * fulfillment reward and transitions the contract to `FULFILLED` status.
     *
     * @param contractId The [Contract.id] to fulfill. All [Contract.terms] deliver-goods
     *   must already have `unitsFulfilled >= unitsRequired` at the time of this call.
     * @return The updated [Contract] with `fulfilled = true` and `status = FULFILLED`.
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.domain.error.SpaceTradersApiException
     *   if delivery requirements are not met or the API call fails.
     */
    suspend fun fulfillContract(contractId: String): Contract

    /**
     * Writes a [Contract] directly to the local database without a network call.
     *
     * Used by `ContractRepositoryImpl` after [acceptContract] and [fulfillContract] to
     * immediately persist the updated contract returned by those API calls. The
     * [observeContracts] flow emits the update to all active collectors automatically.
     *
     * @param contract The [Contract] to write.
     */
    suspend fun upsertContract(contract: Contract)
}
