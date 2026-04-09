package com.brokenhuskysledteam.spacetradersio.di

import com.brokenhuskysledteam.spacetradersio.sdk.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AccountsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AgentsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AgentsApiImpl
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ContractsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApiImpl
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.SystemsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.SystemsApiImpl
import com.brokenhuskysledteam.spacetradersio.sdk.data.repository.FleetRepositoryImpl
import com.brokenhuskysledteam.spacetradersio.sdk.data.repository.SystemRepositoryImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.SystemRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.WaypointStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.NavigateShipUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.NavigateShipUseCaseImpl
import com.brokenhuskysledteam.spacetradersio.sdk.data.repository.TokenRepositoryImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManager
import com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManagerImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.AgentStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.AcceptContractUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.DockShipUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.DockShipUseCaseImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.FulfillContractUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.GetMyContractsUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.OrbitShipUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.OrbitShipUseCaseImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RefuelShipUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RefuelShipUseCaseImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RegisterAgentUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RegisterAgentUseCaseImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Hilt module that bridges the KMP SDK into the Android DI graph.
// All SDK types (repositories, API clients, use cases, session) are provided here so
// ViewModels can receive them via constructor injection.
//
// Singleton-scoped bindings share one instance across the app's lifetime.
// Session-scoped state providers are unscoped — they delegate to SessionManager.requireSession(),
// which is safe because authenticated screens are always behind navigation guards.
@Module
@InstallIn(SingletonComponent::class)
object SdkModule {

    // --- Infrastructure (Singleton) ---

    @Provides
    @Singleton
    fun provideTokenRepository(): TokenRepository =
        TokenRepositoryImpl(com.russhwolf.settings.Settings())

    @Provides
    @Singleton
    fun provideSpaceTradersClient(tokenRepository: TokenRepository): SpaceTradersClient =
        SpaceTradersClient(tokenRepository)

    @Provides
    @Singleton
    fun provideSessionManager(tokenRepository: TokenRepository): SessionManager =
        SessionManagerImpl(tokenRepository)

    // --- API Clients (Singleton) ---

    @Provides
    @Singleton
    fun provideAccountsApi(client: SpaceTradersClient): AccountsApi =
        AccountsApi(client)

    @Provides
    @Singleton
    fun provideAgentsApi(client: SpaceTradersClient): AgentsApi =
        AgentsApiImpl(client)

    @Provides
    @Singleton
    fun provideContractsApi(client: SpaceTradersClient): ContractsApi =
        ContractsApi(client)

    @Provides
    @Singleton
    fun provideFleetApi(client: SpaceTradersClient): FleetApi =
        FleetApiImpl(client)

    // --- Systems API ---

    @Provides
    @Singleton
    fun provideSystemsApi(client: SpaceTradersClient): SystemsApi =
        SystemsApiImpl(client)

    // --- Session-Scoped State (Unscoped — fetches from current session on each injection) ---

    @Provides
    fun provideFleetStateStore(sm: SessionManager): FleetStateStore =
        sm.requireSession().fleetStateStore

    @Provides
    fun provideAgentStateStore(sm: SessionManager): AgentStateStore =
        sm.requireSession().agentStateStore

    @Provides
    fun provideRefreshScheduler(sm: SessionManager): RefreshScheduler =
        sm.requireSession().refreshScheduler

    @Provides
    fun provideWaypointStateStore(sm: SessionManager): WaypointStateStore =
        sm.requireSession().waypointStateStore

    // --- Repositories ---

    @Provides
    fun provideFleetRepository(
        fleetApi: FleetApi,
        fleetStateStore: FleetStateStore,
        refreshScheduler: RefreshScheduler
    ): FleetRepository = FleetRepositoryImpl(fleetApi, fleetStateStore, refreshScheduler)

    @Provides
    fun provideSystemRepository(
        systemsApi: SystemsApi,
        waypointStateStore: WaypointStateStore
    ): SystemRepository = SystemRepositoryImpl(systemsApi, waypointStateStore)

    // --- Use Cases ---

    @Provides
    fun provideRegisterAgentUseCase(
        accountsApi: AccountsApi,
        sessionManager: SessionManager
    ): RegisterAgentUseCase = RegisterAgentUseCaseImpl(accountsApi, sessionManager)

    @Provides
    fun provideAcceptContractUseCase(contractsApi: ContractsApi): AcceptContractUseCase =
        AcceptContractUseCase(contractsApi)

    @Provides
    fun provideGetMyContractsUseCase(contractsApi: ContractsApi): GetMyContractsUseCase =
        GetMyContractsUseCase(contractsApi)

    @Provides
    fun provideFulfillContractUseCase(contractsApi: ContractsApi): FulfillContractUseCase =
        FulfillContractUseCase(contractsApi)

    @Provides
    fun provideOrbitShipUseCase(
        fleetApi: FleetApi,
        fleetStateStore: FleetStateStore
    ): OrbitShipUseCase = OrbitShipUseCaseImpl(fleetApi, fleetStateStore)

    @Provides
    fun provideDockShipUseCase(
        fleetApi: FleetApi,
        fleetStateStore: FleetStateStore
    ): DockShipUseCase = DockShipUseCaseImpl(fleetApi, fleetStateStore)

    @Provides
    fun provideRefuelShipUseCase(
        fleetApi: FleetApi,
        fleetStateStore: FleetStateStore,
        agentStateStore: AgentStateStore
    ): RefuelShipUseCase = RefuelShipUseCaseImpl(fleetApi, fleetStateStore, agentStateStore)

    @Provides
    fun provideNavigateShipUseCase(
        fleetApi: FleetApi,
        fleetStateStore: FleetStateStore,
        orbitShipUseCase: OrbitShipUseCase
    ): NavigateShipUseCase = NavigateShipUseCaseImpl(fleetApi, fleetStateStore, orbitShipUseCase)
}
