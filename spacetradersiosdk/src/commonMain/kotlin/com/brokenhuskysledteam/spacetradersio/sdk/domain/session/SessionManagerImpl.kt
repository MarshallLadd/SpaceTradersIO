package com.brokenhuskysledteam.spacetradersio.sdk.domain.session

import com.brokenhuskysledteam.spacetradersio.sdk.data.db.SpaceTradersDatabase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Production implementation of [SessionManager].
 *
 * **Pattern:** `@Volatile` session holder. In `commonMain` the JVM's `synchronized {}`
 * block is unavailable — it is a JVM-only construct. The KMP-safe alternative is to mark
 * the shared field `@Volatile` (from `kotlin.concurrent`) which guarantees that a write
 * on one thread is immediately visible to reads on all other threads. This is sufficient
 * here because [restoreIfAuthenticated] is called on a single thread (the main thread
 * during app start), so there is no concurrent check-then-set race in practice.
 *
 * **In this project:** Injected as a Hilt singleton. ViewModels call [requireSession] to
 * access the current [SpaceTradersSession] without holding a direct reference to it,
 * which keeps them decoupled from session lifecycle concerns.
 *
 * @param tokenRepository Persistent storage for the bearer token; used to save on [login],
 *   clear on [logout], and check on [restoreIfAuthenticated].
 * @param database SQLDelight database shared across all sessions; passed into each new
 *   [SpaceTradersSessionImpl] so it outlives individual sessions.
 */
class SessionManagerImpl(
    private val tokenRepository: TokenRepository,
    private val database: SpaceTradersDatabase
) : SessionManager {

    // @Volatile ensures _session writes are visible across threads. restoreIfAuthenticated()
    // is designed to be called from the main thread (app lifecycle), so its check-then-set
    // is safe in practice despite not being atomic.
    //
    // `import kotlin.concurrent.Volatile` is required — the annotation is not auto-imported
    // in commonMain and the compiler will silently accept the wrong one if the import is wrong.
    @Volatile
    private var _session: SpaceTradersSession? = null

    /**
     * Returns the active session, throwing if none exists.
     *
     * Uses Kotlin's `error()` helper (which throws [IllegalStateException]) so the
     * message is descriptive when a ViewModel is called before authentication.
     */
    override fun requireSession(): SpaceTradersSession =
        _session ?: error("No active session. User must be authenticated.")

    /**
     * Persists [token] and creates a fresh [SpaceTradersSessionImpl].
     *
     * A new [CoroutineScope] with [SupervisorJob] is created for each session so that
     * a failure in one child coroutine does not tear down the entire session, and so
     * [SpaceTradersSessionImpl.destroy] can cancel all timers by cancelling a single scope.
     * [Dispatchers.Default] is used because [Dispatchers.IO] is unavailable in commonMain.
     */
    override fun login(token: String) {
        tokenRepository.saveToken(token)
        _session = SpaceTradersSessionImpl(
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
            database
        )
    }

    /**
     * Tears down the active session and removes the persisted token.
     *
     * The order matters: [SpaceTradersSession.destroy] is called before nulling `_session`
     * so the session can clean up its own state stores and cancel its coroutine scope
     * while the reference is still reachable.
     */
    override fun logout() {
        _session?.destroy()
        _session = null
        tokenRepository.clearToken()
    }

    /**
     * Recreates a session from a persisted token without user interaction.
     *
     * Safe to call unconditionally on app start; the guard `_session == null` prevents
     * a second session from being created if [login] was already called (e.g. in tests
     * that call both). The check-then-act pattern is safe here because this method is
     * only ever called from the main thread during app initialisation.
     */
    override fun restoreIfAuthenticated() {
        if (tokenRepository.hasToken() && _session == null) {
            _session = SpaceTradersSessionImpl(
                CoroutineScope(SupervisorJob() + Dispatchers.Default),
                database
            )
        }
    }
}
