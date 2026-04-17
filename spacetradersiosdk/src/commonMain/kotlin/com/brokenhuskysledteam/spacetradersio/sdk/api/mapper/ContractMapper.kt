package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

// Mapper: ContractDto → Contract (and nested ContractTermsDto → ContractTerms)
// All DTO-to-domain conversions for the Contract entity live here.

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ContractDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ContractTerms
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractType
import kotlin.time.Instant

/**
 * Maps this [ContractDto] to a [Contract] domain model.
 *
 * **Pattern:** Extension function mapper with nested sub-object construction. When the API
 * response contains nested objects (here: a contract with terms and payment), the top-level
 * mapper constructs child domain objects inline rather than delegating to separate mappers.
 * This keeps the full contract conversion readable in a single function. In a new project,
 * use this inline approach when child objects are only ever produced as part of the parent;
 * promote to a dedicated sub-mapper only when the child is also returned by other endpoints.
 *
 * **In this project:** [ContractDto] is returned by `GET /my/contracts` (paginated list),
 * contract accept/fulfill/deliver action responses, and registration. The mapper is the
 * single conversion point, so the domain layer receives correctly typed [Contract] values
 * regardless of which endpoint produced the DTO.
 *
 * **Timestamp handling:** The API sends all timestamps as ISO-8601 strings in the DTO.
 * Conversion to [kotlin.time.Instant] happens here — the DTO stays as raw [String], the
 * domain model sees only [Instant], and no parse logic leaks into repositories or ViewModels.
 *
 * **Deprecation fallback:** The API deprecated the `expiration` field in favour of
 * `deadlineToAccept`, but continues to include both. The mapper prefers `deadlineToAccept`
 * and falls back to `expiration` when it is absent, ensuring correct behaviour for both
 * old and new API responses.
 *
 * @return The domain model built from this DTO's data.
 * @see ContractDto for wire-format field documentation, including the deprecation note on
 *   [ContractDto.expiration] vs [ContractDto.deadlineToAccept].
 */
fun ContractDto.toDomain(): Contract = Contract(
    id = id,
    factionSymbol = factionSymbol,
    // ContractType.fromString() converts the raw API string (e.g. "PROCUREMENT") to the typed
    // enum, preventing magic strings from leaking into the domain layer.
    type = ContractType.fromString(type),
    accepted = accepted,
    fulfilled = fulfilled,
    // deadlineToAccept is preferred; expiration is the older, deprecated fallback.
    // The API guarantees at least one is non-null, so the !! on the result is safe.
    deadlineToAccept = (deadlineToAccept ?: expiration).let { Instant.parse(it) },
    terms = ContractTerms(
        // Timestamp conversion: the DTO holds a raw ISO-8601 string; the domain model holds
        // Instant. Parsing happens once here, not every time the UI reads the deadline.
        deadline = Instant.parse(terms.deadline),
        paymentOnAccepted = terms.payment.onAccepted,
        paymentOnFulfilled = terms.payment.onFulfilled
    )
)
