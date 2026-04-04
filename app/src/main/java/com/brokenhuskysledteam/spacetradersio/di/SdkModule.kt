package com.brokenhuskysledteam.spacetradersio.di

import com.brokenhuskysledteam.spacetradersio.sdk.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AccountsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AgentsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AgentsApiImpl
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ContractsApi
import com.brokenhuskysledteam.spacetradersio.sdk.data.repository.TokenRepositoryImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.AcceptContractUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.FulfillContractUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.GetMyContractsUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RegisterAgentUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RegisterAgentUseCaseImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
        AccountsApi(client.unauthenticated)

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
}
