package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

/**
 * Generic envelope for a single-object SpaceTraders API response.
 *
 * **Pattern:** Response envelope / wrapper DTO. The SpaceTraders API (and many REST APIs)
 * never return a bare JSON object at the top level — they always nest the payload under a
 * named key. Modelling that wrapper as a generic `data class` avoids hand-writing a wrapper
 * for every endpoint. In a new project, create one envelope type per distinct top-level
 * shape the API uses, then parameterise the payload type with a generic `<T>`. Ktor's
 * `body<ApiResponse<FooDto>>()` deserialization handles the rest automatically.
 *
 * **In this project:** Used for every non-paginated endpoint that returns a single object
 * (e.g. `GET /my/agent` → `ApiResponse<AgentDto>`, `POST /register` → `ApiResponse<RegisterResponseDto>`).
 * The Ktor call site unwraps `.data` immediately after receiving the response and passes
 * the inner DTO to the mapper — callers never see the envelope.
 *
 * @property data The actual payload. The type parameter `T` is inferred from the Ktor
 *   `body<ApiResponse<T>>()` call at the deserialization site.
 */
@Serializable
data class ApiResponse<T>(
    val data: T
)

/**
 * Generic envelope for a paginated SpaceTraders API response.
 *
 * **Pattern:** Paginated response envelope. Many list endpoints return results in pages.
 * By pairing the list payload with a [MetaDto] sibling, the caller can determine how many
 * pages exist without a separate count request. In a new project, extract this into its own
 * generic wrapper rather than repeating `data + meta` in every list DTO.
 *
 * **In this project:** Used for fleet (`GET /my/ships`), contracts (`GET /my/contracts`),
 * waypoints, and other list endpoints. The repository layer consumes [meta] to decide
 * whether to fetch additional pages.
 *
 * @property data The current page of results. The length is at most [MetaDto.limit] items.
 * @property meta Pagination metadata for this response. See [MetaDto] for details on
 *   computing whether more pages are available.
 */
@Serializable
data class PaginatedResponse<T>(
    val data: List<T>,
    val meta: MetaDto
)

/**
 * Pagination metadata returned alongside every list response.
 *
 * **Pattern:** Pagination cursor DTO. Including `total`, `page`, and `limit` in every list
 * response gives the client everything it needs to render a page indicator or decide
 * whether to fetch the next page — with no extra API call. In a new project, always model
 * these three fields together as a single reusable type rather than duplicating them across
 * every list wrapper.
 *
 * **In this project:** Consumed by repository implementations to loop over all pages when
 * the SDK needs the full collection (e.g. loading the complete ship fleet). The formula to
 * determine whether a next page exists is: `page < ceil(total / limit)`. For example,
 * `total = 25`, `limit = 10`, `page = 2` → 3 total pages → page 3 still exists.
 *
 * @property total The total number of matching records across **all** pages, not just this
 *   page. Use this together with [limit] to compute how many pages exist in total.
 * @property page The **1-based** index of the current page. The first page is `1`,
 *   not `0`.
 * @property limit The maximum number of items the server will return per page for this
 *   request. The actual number of items in [PaginatedResponse.data] may be smaller on the
 *   last page.
 */
@Serializable
data class MetaDto(
    val total: Int,
    val page: Int,
    val limit: Int
)
