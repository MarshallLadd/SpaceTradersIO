package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository
import com.russhwolf.settings.Settings

// Persists the bearer token using multiplatform-settings.
// On Android this uses SharedPreferences; on iOS it uses NSUserDefaults.
// Both are sufficient for a game token — neither is encrypted.
class TokenRepositoryImpl(
    private val settings: Settings,
) : TokenRepository {
    companion object {
        private const val KEY_TOKEN = "agent_token"
    }

    override fun getToken(): String? = settings.getStringOrNull(KEY_TOKEN)

    override fun saveToken(token: String) {
        settings.putString(KEY_TOKEN, token)
    }

    override fun clearToken() {
        settings.remove(KEY_TOKEN)
    }

    override fun hasToken(): Boolean = settings.hasKey(KEY_TOKEN)
}
