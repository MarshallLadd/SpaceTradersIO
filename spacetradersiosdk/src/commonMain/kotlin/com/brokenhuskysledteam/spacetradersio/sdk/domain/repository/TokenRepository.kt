package com.brokenhuskysledteam.spacetradersio.sdk.domain.repository

/**
 * Defines persistence operations for the player's SpaceTraders bearer token.
 *
 * **Pattern:** Repository interface in the domain layer for simple key-value persistence.
 * Even when there is only one likely implementation, keeping this as an interface provides
 * two concrete benefits:
 * 1. **Testability** — tests inject a fake (`FakeTokenRepository`) that stores the token in
 *    a plain `var` field. No platform storage APIs, no `SharedPreferences`, no file system.
 * 2. **Abstraction boundary** — the domain and UI layers are decoupled from the storage
 *    mechanism. Switching from `multiplatform-settings` to an encrypted store, or adapting
 *    to a new platform, requires changing only the `data/` implementation.
 *
 * **In this project:** `TokenRepositoryImpl` wraps the `multiplatform-settings` library,
 * which maps to `SharedPreferences` on Android and `NSUserDefaults` on iOS. The domain and
 * UI layers see only this interface and are unaware of those platform details.
 *
 * The token persisted here is the SpaceTraders **Agent Token** (a JWT). It is passed in the
 * `Authorization: Bearer` header for all authenticated API calls and survives app restarts.
 */
interface TokenRepository {

    /**
     * Returns the currently persisted bearer token, or `null` if no token has been saved.
     *
     * Called during app startup to decide whether to show the auth screen or navigate
     * directly to the dashboard. Also called by `SpaceTradersClient` before every API
     * request to attach the correct `Authorization` header.
     *
     * @return The stored token string, or `null` if [saveToken] has never been called or
     *   [clearToken] was called since the last save.
     */
    fun getToken(): String?

    /**
     * Persists [token] to durable storage so it survives process death and app restarts.
     *
     * Called once after a successful registration or manual token import. Subsequent calls
     * overwrite the previously stored value.
     *
     * @param token The SpaceTraders Agent Token JWT to persist.
     */
    fun saveToken(token: String)

    /**
     * Removes the persisted token from storage.
     *
     * Called during logout. After this call, [getToken] returns `null` and [hasToken] returns
     * `false` until a new token is saved.
     */
    fun clearToken()

    /**
     * Returns `true` if a token is currently persisted, `false` otherwise.
     *
     * A convenience predicate — semantically equivalent to `getToken() != null`, but avoids
     * allocating the token string when the caller only needs to know whether one exists (e.g.
     * during startup routing to decide the initial navigation destination).
     *
     * @return `true` if [saveToken] has been called and [clearToken] has not been called since.
     */
    fun hasToken(): Boolean
}
