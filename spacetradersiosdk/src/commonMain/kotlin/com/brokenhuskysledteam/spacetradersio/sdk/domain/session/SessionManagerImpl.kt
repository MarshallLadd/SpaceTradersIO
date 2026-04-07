package com.brokenhuskysledteam.spacetradersio.sdk.domain.session

import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SessionManagerImpl(private val tokenRepository: TokenRepository) : SessionManager {

    // @Volatile ensures _session writes are visible across threads. restoreIfAuthenticated()
    // is designed to be called from the main thread (app lifecycle), so its check-then-set
    // is safe in practice despite not being atomic.
    @Volatile
    private var _session: SpaceTradersSession? = null

    override fun requireSession(): SpaceTradersSession =
        _session ?: error("No active session. User must be authenticated.")

    override fun login(token: String) {
        tokenRepository.saveToken(token)
        _session = SpaceTradersSessionImpl(
            CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )
    }

    override fun logout() {
        _session?.destroy()
        _session = null
        tokenRepository.clearToken()
    }

    override fun restoreIfAuthenticated() {
        if (tokenRepository.hasToken() && _session == null) {
            _session = SpaceTradersSessionImpl(
                CoroutineScope(SupervisorJob() + Dispatchers.Default)
            )
        }
    }
}
