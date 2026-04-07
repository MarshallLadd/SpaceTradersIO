package com.brokenhuskysledteam.spacetradersio.sdk.domain.session

import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SessionManager(private val tokenRepository: TokenRepository) {

    private var _session: SpaceTradersSession? = null

    fun requireSession(): SpaceTradersSession =
        _session ?: error("No active session. User must be authenticated.")

    fun login(token: String) {
        tokenRepository.saveToken(token)
        _session = SpaceTradersSession(
            CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )
    }

    fun logout() {
        _session?.destroy()
        _session = null
        tokenRepository.clearToken()
    }

    fun restoreIfAuthenticated() {
        if (tokenRepository.hasToken() && _session == null) {
            _session = SpaceTradersSession(
                CoroutineScope(SupervisorJob() + Dispatchers.Default)
            )
        }
    }
}
