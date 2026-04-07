package com.brokenhuskysledteam.spacetradersio.sdk.domain.session

interface SessionManager {
    fun requireSession(): SpaceTradersSession
    fun login(token: String)
    fun logout()
    fun restoreIfAuthenticated()
}
