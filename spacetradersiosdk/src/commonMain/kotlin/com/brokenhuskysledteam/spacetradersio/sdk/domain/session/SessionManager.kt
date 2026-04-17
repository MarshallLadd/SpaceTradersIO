package com.brokenhuskysledteam.spacetradersio.sdk.domain.session

/**
 * Manages the lifecycle of a single authenticated SpaceTraders session.
 *
 * **Pattern:** Session Manager (interface). Define this as an interface so the
 * implementation can be swapped in tests with a hand-written fake — no Mockk or
 * reflection needed. In a new project, declare the contract here and inject it
 * via DI everywhere it is consumed.
 *
 * **In this project:** The single concrete implementation is [SessionManagerImpl],
 * wired as a singleton in the Hilt module. Every ViewModel that needs authenticated
 * API access calls [requireSession] to obtain the current [SpaceTradersSession].
 *
 * A "session" represents the full authenticated context for one logged-in agent:
 * the coroutine scope, in-memory state stores, and scheduled refresh timers. There
 * is at most one active session at a time. Creating a new session via [login]
 * implicitly replaces (and destroys) any previously active one.
 */
interface SessionManager {

    /**
     * Returns the active [SpaceTradersSession], or throws if the user is not authenticated.
     *
     * @return The current session.
     * @throws IllegalStateException if no session has been established via [login] or
     *   [restoreIfAuthenticated].
     */
    fun requireSession(): SpaceTradersSession

    /**
     * Creates and stores a new [SpaceTradersSession] for the given bearer token.
     *
     * Persists [token] to [com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository]
     * so the session survives process death. Any previously active session is replaced —
     * callers should call [logout] first if an explicit teardown is needed.
     *
     * @param token The SpaceTraders agent bearer token (JWT) returned by registration
     *   or imported by the user.
     */
    fun login(token: String)

    /**
     * Destroys the active session and clears the persisted token.
     *
     * Cancels all session-scoped coroutines, clears in-memory state stores, and
     * removes the token from persistent storage. Safe to call when no session is
     * active (no-op in that case).
     */
    fun logout()

    /**
     * Restores a previous session on app start if a persisted token exists.
     *
     * Called once during application initialisation (e.g. from `Application.onCreate`
     * or a lifecycle observer). Checks whether a token was saved in a prior session; if
     * so, recreates the [SpaceTradersSession] without requiring the user to log in again.
     * Does nothing if no token is stored or a session is already active.
     */
    fun restoreIfAuthenticated()
}
