package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Top-level wire-format envelope for a SpaceTraders API error response.
 *
 * **Pattern:** Error envelope DTO. The SpaceTraders API returns a consistent JSON shape
 * for every non-2xx response: `{ "error": { "code": …, "message": … } }`. Modelling this
 * as a dedicated DTO lets `HttpCallValidator` in `SpaceTradersClient` deserialize it in one
 * call and throw a typed `SpaceTradersApiException`. In a new project that has a consistent
 * error envelope, create a single error DTO and deserialize it in the Ktor response
 * validator rather than at each individual call site.
 *
 * **In this project:** `SpaceTradersClient` registers an `HttpCallValidator` that intercepts
 * all non-2xx responses, deserializes the body as `ErrorResponseDto`, maps [ErrorBodyDto.code]
 * to the sealed `SpaceTradersError` hierarchy, and throws `SpaceTradersApiException`. App
 * ViewModels catch that exception and `when`-match on the sealed type.
 *
 * @property error The error detail nested under the `"error"` key in the JSON body.
 */
@Serializable
data class ErrorResponseDto(
    val error: ErrorBodyDto
)

/**
 * The error detail payload nested inside an [ErrorResponseDto].
 *
 * **Pattern:** Error body DTO. Separating the outer envelope ([ErrorResponseDto]) from the
 * inner detail ([ErrorBodyDto]) mirrors the JSON nesting and keeps each class focused. The
 * error code is an `Int`, not a `String`, matching the numeric codes defined in the
 * SpaceTraders API error-codes reference (`../OpenAPISpec/space_trader_api_error_codes.json`).
 *
 * **In this project:** `SpaceTradersClient`'s `HttpCallValidator` reads [code] to look up
 * the matching branch in the sealed `SpaceTradersError` hierarchy (e.g. code `4001` →
 * `AuthError.TokenMissing`). [message] is forwarded to `SpaceTradersApiException` for
 * logging and user-facing error display. [data] is optional extra context that some error
 * codes attach (e.g. retry-after seconds for rate-limit errors).
 *
 * @property code Numeric error code defined by the SpaceTraders API. Maps to a branch in
 *   the sealed `SpaceTradersError` hierarchy. The full catalogue of codes is in
 *   `../OpenAPISpec/space_trader_api_error_codes.json`.
 * @property message Human-readable error message from the server. Suitable for display in
 *   error states or for logging, but not for programmatic branching — use [code] for that.
 * @property data Optional structured context the API attaches to some error codes. Modelled
 *   as a raw [JsonObject] because the shape varies per error code and is not exhaustively
 *   typed. `null` when the API does not include additional context.
 */
@Serializable
data class ErrorBodyDto(
    val code: Int,
    val message: String,
    // JsonObject is used rather than a typed class because the extra context fields
    // vary per error code and are not part of a stable, exhaustively-typed schema.
    val data: JsonObject? = null
)
