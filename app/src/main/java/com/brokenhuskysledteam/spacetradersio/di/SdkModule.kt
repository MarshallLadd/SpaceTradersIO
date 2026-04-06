package com.brokenhuskysledteam.spacetradersio.di

import com.brokenhuskysledteam.spacetradersio.sdk.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AccountsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AgentsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AgentsApiImpl
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ContractsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApiImpl
import com.brokenhuskysledteam.spacetradersio.sdk.data.repository.FleetRepositoryImpl
import com.brokenhuskysledteam.spacetradersio.sdk.data.repository.TokenRepositoryImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository
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
// All SDK types (repositories, API clients, use cases) are provided here so
// ViewModels can receive them via constructor injection.
//
// Singleton-scoped bindings share one instance across the app's lifetime.
// Use-case bindings are unscoped — each injection site gets a fresh instance,
// which is fine since they're stateless.
@Module
@InstallIn(SingletonComponent::class)
object SdkModule {

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
    fun provideRegisterAgentUseCase(
        accountsApi: AccountsApi,
        tokenRepository: TokenRepository
    ): RegisterAgentUseCase = RegisterAgentUseCaseImpl(accountsApi, tokenRepository)

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
    @Singleton
    fun provideFleetApi(client: SpaceTradersClient): FleetApi =
        FleetApiImpl(client)

    @Provides
    @Singleton
    fun provideFleetRepository(fleetApi: FleetApi): FleetRepository =
        FleetRepositoryImpl(fleetApi)

    @Provides
    fun provideOrbitShipUseCase(fleetApi: FleetApi): OrbitShipUseCase =
        OrbitShipUseCaseImpl(fleetApi)

    @Provides
    fun provideDockShipUseCase(fleetApi: FleetApi): DockShipUseCase =
        DockShipUseCaseImpl(fleetApi)

    @Provides
    fun provideRefuelShipUseCase(fleetApi: FleetApi): RefuelShipUseCase =
        RefuelShipUseCaseImpl(fleetApi)
}
