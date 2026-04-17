package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

/**
 * Typed exception thrown whenever the SpaceTraders API returns an error response.
 *
 * **Pattern:** Typed exception wrapping. Instead of throwing a generic `Exception` with a plain
 * string message, this exception carries two structured pieces of data: a [SpaceTradersError]
 * (a sealed type the caller can pattern-match exhaustively) and the raw [httpStatus] code for
 * logging and retry decisions. In a new project, create one project-specific exception class that
 * wraps your domain error hierarchy and the HTTP status — then callers use `when` on the error
 * type rather than parsing strings.
 *
 * **In this project:** `SpaceTradersApiException` is the single exception type produced by
 * `HttpCallValidator` inside `SpaceTradersClient`. Every SDK use case and every ViewModel that
 * calls the SDK catches exactly this type. Because [error] is sealed, the compiler enforces
 * exhaustive handling when new error categories are added — string-based error dispatch has no
 * such compile-time safety.
 *
 * Typical calling pattern in a ViewModel:
 * ```kotlin
 * try {
 *     repository.navigate(shipSymbol, waypointSymbol)
 * } catch (e: SpaceTradersApiException) {
 *     when (e.error) {
 *         is SpaceTradersError.AuthError -> handleLogout()
 *         is SpaceTradersError.NavigationError.InsufficientFuel -> promptRefuel()
 *         is SpaceTradersError.NavigationError -> showGenericNavError(e.error.message)
 *         is SpaceTradersError.Unknown -> showUnexpectedError(e.httpStatus)
 *         else -> showGenericError(e.error.message)
 *     }
 * }
 * ```
 *
 * The `Exception(error.message)` super-constructor call forwards the human-readable message so
 * that standard logging (e.g., `Napier.e(e)`) surfaces useful text without extra unwrapping.
 *
 * @param error The structured domain error describing what went wrong. Use `when (e.error)` on
 *   this property to route handling — category-level branches (`is AuthError`) catch all subtypes,
 *   while specific branches (`is AuthError.TokenEmpty`) override when fine-grained handling is
 *   needed.
 * @param httpStatus The raw HTTP status code returned by the server (e.g., 401, 422, 429). Useful
 *   for retry logic (e.g., back off on 429) and for diagnostic logging alongside [error].
 */
class SpaceTradersApiException(
    val error: SpaceTradersError,
    val httpStatus: Int
) : Exception(error.message)
