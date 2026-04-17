package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractType
import kotlin.time.Instant

/**
 * Represents a mission issued by a faction that the agent can accept, fulfil, and earn
 * credits from.
 *
 * **Pattern:** Immutable domain model with a nested value type ([ContractTerms]). In a new
 * project, break compound API responses into focused sub-objects rather than flattening
 * everything into one large class — it keeps each type cohesive and makes `copy()` calls
 * on individual sub-objects easy when only part of the state changes.
 *
 * **In this project:** Contracts are fetched from `/my/contracts`, mapped from their DTO
 * representation in `api/mapper/`, and surfaced to the UI through the contracts repository.
 * The lifecycle of a contract (`accepted` → goods delivered → `fulfilled`) drives several
 * SDK use cases.
 *
 * @property id The server-assigned unique identifier for this contract. Used as the
 *   primary key in all contract-related API calls (accept, deliver, fulfil).
 * @property factionSymbol The faction that issued the contract (e.g. `"COSMIC"`).
 * @property type Classifies the mission as procurement, transport, or shuttle work.
 *   See [ContractType] for the full enumeration.
 * @property accepted `true` once the agent has explicitly accepted the contract via
 *   `POST /my/contracts/{contractId}/accept`. Unaccepted contracts expire.
 * @property fulfilled `true` when all delivery requirements have been met and the final
 *   payment has been collected via `POST /my/contracts/{contractId}/fulfill`.
 * @property deadlineToAccept The latest point in time the agent may accept this contract.
 *   `null` on older contracts that use the deprecated `expiration` field instead — the
 *   mapper promotes `expiration` into this property when `deadlineToAccept` is absent.
 *   Using [kotlinx.datetime.Instant] (platform-neutral) rather than `java.util.Date`
 *   keeps this type usable in `commonMain` on both Android and iOS.
 * @property terms The payment schedule and delivery deadline. Modelled as a separate
 *   [ContractTerms] object because the API nests them under a `terms` key.
 */
data class Contract(
    val id: String,
    val factionSymbol: String,
    val type: ContractType,
    val accepted: Boolean,
    val fulfilled: Boolean,
    // deadlineToAccept supersedes the deprecated expiration field.
    // Both are kept here to handle contracts that only provide expiration.
    val deadlineToAccept: Instant?,
    val terms: ContractTerms
)

/**
 * The financial and time terms attached to a [Contract].
 *
 * **Pattern:** Nested value object. Extracting a cohesive group of related fields into its
 * own `data class` avoids bloating the parent and makes the sub-object independently
 * copyable — useful if only payment amounts change in a future SDK version.
 *
 * **In this project:** Rendered directly in the contracts list UI to show the agent what
 * they will earn by accepting and fulfilling the contract.
 *
 * @property deadline The point in time by which all deliveries must be completed to
 *   qualify for the fulfilment payment. Stored as [kotlinx.datetime.Instant] so that
 *   time-until-deadline can be computed without platform-specific date APIs.
 * @property paymentOnAccepted Credits awarded immediately when the contract is accepted.
 *   Provides upfront capital for fuel and cargo purchases needed to complete the job.
 * @property paymentOnFulfilled Credits awarded when all goods are delivered and
 *   `fulfill` is called. This is typically the larger portion of the total payout.
 */
data class ContractTerms(
    val deadline: Instant,
    val paymentOnAccepted: Int,
    val paymentOnFulfilled: Int
)
