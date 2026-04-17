package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository
import com.russhwolf.settings.Settings

/**
 * Concrete implementation of [TokenRepository] that persists the bearer token using
 * the `multiplatform-settings` library.
 *
 * **Pattern:** Platform-agnostic key-value persistence via `multiplatform-settings`.
 * The [Settings] interface abstracts the underlying storage mechanism: on Android it
 * wraps `SharedPreferences`, on iOS it wraps `NSUserDefaults`. The same Kotlin code
 * runs unchanged on both platforms — no `expect/actual` is required. To apply this
 * pattern in a new project, depend on `com.russhwolf:multiplatform-settings` and
 * inject a `Settings` instance from the platform-specific DI graph.
 *
 * **In this project:** The token is a JWT bearer token issued by the SpaceTraders API.
 * It is read at app startup to determine whether a session can be restored automatically
 * (see `SpaceTradersSession`). It is written during registration or token-import and
 * cleared on logout. The storage is not encrypted — this is acceptable for a game token
 * that carries no financial or personal data, but a production app handling sensitive
 * credentials should use an encrypted store (e.g. Android Keystore / iOS Keychain).
 *
 * @param settings Platform-appropriate `Settings` implementation; injected by the DI
 *                 graph so the concrete `SharedPreferences`/`NSUserDefaults` constructor
 *                 call is kept in platform-specific code.
 */
class TokenRepositoryImpl(
    private val settings: Settings,
) : TokenRepository {

    companion object {
        /**
         * The key used to store the token in [Settings].
         *
         * Hardcoded (rather than configurable) because this app supports exactly one
         * logged-in agent at a time. If multi-account support were added, the key
         * should include the account identifier (e.g. `"agent_token_$accountId"`) to
         * avoid collisions between sessions.
         */
        private const val KEY_TOKEN = "agent_token"
    }

    /**
     * Returns the stored bearer token, or `null` if no token has been saved.
     *
     * Uses `getStringOrNull` rather than `getString` — the latter throws
     * `IllegalStateException` when the key is absent, which would crash the app on
     * first launch before any token has been written.
     *
     * @return The stored token string, or `null` if the user has not logged in.
     */
    override fun getToken(): String? = settings.getStringOrNull(KEY_TOKEN)

    /**
     * Persists [token] to the underlying platform key-value store under [KEY_TOKEN].
     *
     * Any previously stored token is overwritten. This method is synchronous — the
     * `multiplatform-settings` library does not expose a suspend variant because
     * `SharedPreferences` and `NSUserDefaults` writes are fast in-process operations
     * that do not require off-thread dispatch.
     *
     * @param token The JWT bearer token to persist.
     */
    override fun saveToken(token: String) {
        settings.putString(KEY_TOKEN, token)
    }

    /**
     * Removes the stored token from the underlying key-value store.
     *
     * Called during logout. After this returns, [hasToken] will return `false` and
     * [getToken] will return `null`. On Android, `Settings.remove` calls
     * `SharedPreferences.Editor.remove().apply()`.
     */
    override fun clearToken() {
        settings.remove(KEY_TOKEN)
    }

    /**
     * Returns `true` if a token has previously been saved, `false` otherwise.
     *
     * Checking key presence via `hasKey` is preferred over `getToken() != null`
     * because it avoids deserializing the stored string when the caller only needs
     * to know whether a value exists (e.g. the session restore gate at startup).
     *
     * @return `true` if [KEY_TOKEN] exists in [Settings], `false` if absent.
     */
    override fun hasToken(): Boolean = settings.hasKey(KEY_TOKEN)
}
