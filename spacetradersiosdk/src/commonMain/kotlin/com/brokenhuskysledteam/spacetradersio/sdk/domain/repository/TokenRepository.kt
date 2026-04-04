package com.brokenhuskysledteam.spacetradersio.sdk.domain.repository

// Abstracts persistent storage of the SpaceTraders bearer token.
// The interface lives in the domain layer so use cases and ViewModels
// can depend on it without knowing about multiplatform-settings or any
// platform storage mechanism.
interface TokenRepository {
    fun getToken(): String?
    fun saveToken(token: String)
    fun clearToken()
    fun hasToken(): Boolean
}
