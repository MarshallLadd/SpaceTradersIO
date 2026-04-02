package com.brokenhuskysledteam.spacetraders.di

import com.brokenhuskysledteam.spacetraders.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetraders.api.endpoints.AccountsApi
import com.brokenhuskysledteam.spacetraders.api.endpoints.AgentsApi
import com.brokenhuskysledteam.spacetraders.data.repository.TokenRepositoryImpl
import com.brokenhuskysledteam.spacetraders.domain.repository.TokenRepository
import com.brokenhuskysledteam.spacetraders.domain.usecase.RegisterAgentUseCase
import com.russhwolf.settings.Settings
import org.koin.dsl.module

// Central Koin module. All dependencies are declared here so the
// dependency graph is visible in one place.
val appModule = module {

    // multiplatform-settings no-arg factory — resolves to SharedPreferences
    // on Android and NSUserDefaults on iOS with no platform-specific setup.
    single<Settings> { Settings() }

    // Token repository — single instance so all callers share the same store.
    single<TokenRepository> { TokenRepositoryImpl(get()) }

    // HTTP client manager — single instance so unauthenticated and authenticated
    // clients are reused across the app rather than rebuilt on every call.
    single { SpaceTradersClient(get()) }

    // API endpoint classes — each takes the relevant HttpClient from the manager.
    single { AccountsApi(get<SpaceTradersClient>().unauthenticated) }
    single { AgentsApi(get<SpaceTradersClient>().authenticated) }

    // Use cases
    single { RegisterAgentUseCase(get(), get()) }
}
