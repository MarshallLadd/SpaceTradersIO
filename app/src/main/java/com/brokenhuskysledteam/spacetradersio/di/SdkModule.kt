package com.brokenhuskysledteam.spacetradersio.di

import android.content.Context
import com.brokenhuskysledteam.spacetradersio.sdk.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AccountsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AgentsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AgentsApiImpl
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ContractsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApiImpl
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ShipyardApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ShipyardApiImpl
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.SystemsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.SystemsApiImpl
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.SpaceTradersDatabase
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.SqlDriverFactory
import com.brokenhuskysledteam.spacetradersio.sdk.data.repository.AgentRepositoryImpl
import com.brokenhuskysledteam.spacetradersio.sdk.data.repository.ContractRepositoryImpl
import com.brokenhuskysledteam.spacetradersio.sdk.data.repository.FleetRepositoryImpl
import com.brokenhuskysledteam.spacetradersio.sdk.data.repository.ShipyardRepositoryImpl
import com.brokenhuskysledteam.spacetradersio.sdk.data.repository.SystemRepositoryImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.AgentRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.ContractRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.ShipyardRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.SystemRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManager
import com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManagerImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.WaypointStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.AcceptContractUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.DeliverCargoUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.DeliverCargoUseCaseImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.DockShipUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.DockShipUseCaseImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.FulfillContractUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.GetMyContractsUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.NavigateShipUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.NavigateShipUseCaseImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.NegotiateContractUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.NegotiateContractUseCaseImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.OrbitShipUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.OrbitShipUseCaseImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RefuelShipUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RefuelShipUseCaseImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RegisterAgentUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RegisterAgentUseCaseImpl
import com.brokenhuskysledteam.spacetradersio.sdk.data.repository.TokenRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt `@Module` that bridges the KMP SDK into the Android DI graph.
 *
 * **Pattern:** SDK adapter module. The SDK itself has zero Hilt (or any DI framework)
 * dependency, which keeps it consumable from iOS without pulling in Android-only libraries.
 * `SdkModule` is the Android-only adapter layer: it constructs SDK objects using their
 * public constructors and registers them as Hilt bindings so that ViewModels can receive
 * them via constructor injection without knowing how they are built. To apply in a new
 * project: keep your shared library free of DI annotations; create a single platform-specific
 * `@Module` that wires the library into the host DI graph.
 *
 * **Scoping strategy — two tiers:**
 *
 * *App-scoped (`@Singleton`)* — objects that are safe and cheap to share for the entire app
 * lifetime. These are created once when the Hilt component is first used and live until the
 * process dies: [SpaceTradersClient], [SpaceTradersDatabase], [SessionManager], and all API
 * classes. There is exactly one instance of each across all screens.
 *
 * *Session-scoped (unscoped `@Provides`)* — objects whose identity is tied to a login
 * session rather than the app lifetime. [RefreshScheduler] and [WaypointStateStore] are
 * retrieved by calling `SessionManager.requireSession()` at the Hilt injection call site.
 * Because they are unscoped, Hilt calls the provider function on every injection, but the
 * provider simply returns the object already held by the current session — no new instance
 * is created per injection. This is safe because authenticated screens are always behind a
 * navigation guard (the nav host redirects to Auth when there is no session). If an
 * unauthenticated screen somehow reached an unscoped provider, `requireSession()` would
 * throw, surfacing the bug immediately.
 *
 * All bindings are installed in [SingletonComponent] so the graph lives as long as the
 * application process.
 */
@Module
@InstallIn(SingletonComponent::class)
object SdkModule {

    // -----------------------------------------------------------------------------------------
    // Infrastructure (Singleton)
    // -----------------------------------------------------------------------------------------

    /**
     * Provides the [TokenRepository] backed by multiplatform-settings persistent storage.
     *
     * Uses `com.russhwolf.settings.Settings()` — the default Settings implementation on
     * Android maps to `SharedPreferences`. The token is persisted across app restarts so
     * the user does not need to re-authenticate after a process kill.
     *
     * @return A [TokenRepositoryImpl] wrapping the platform default settings store.
     */
    @Provides
    @Singleton
    fun provideTokenRepository(): TokenRepository =
        TokenRepositoryImpl(com.russhwolf.settings.Settings())

    /**
     * Provides the singleton [SpaceTradersClient] (Ktor HTTP client wrapper).
     *
     * [SpaceTradersClient] holds the configured Ktor `HttpClient`, installs the content
     * negotiation and auth plugins, and is the single point of entry for all API calls.
     * Making it a singleton ensures the underlying OkHttp connection pool is shared across
     * all API classes, avoiding unnecessary connection overhead.
     *
     * @param tokenRepository Used by the client to attach `Authorization: Bearer` headers to
     *   authenticated requests.
     * @return A singleton [SpaceTradersClient].
     */
    @Provides
    @Singleton
    fun provideSpaceTradersClient(tokenRepository: TokenRepository): SpaceTradersClient =
        SpaceTradersClient(tokenRepository)

    /**
     * Provides the singleton [SpaceTradersDatabase] (SQLDelight database).
     *
     * The database is created by [SqlDriverFactory], which on Android returns a
     * `AndroidSqliteDriver` backed by a file in the app's private data directory. A singleton
     * ensures all repositories share one connection and SQLDelight's in-memory query
     * notification machinery works correctly across threads.
     *
     * @param context Application context used by [SqlDriverFactory] to locate the DB file.
     * @return A singleton [SpaceTradersDatabase].
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SpaceTradersDatabase =
        SpaceTradersDatabase(SqlDriverFactory(context).create())

    /**
     * Provides the singleton [SessionManager].
     *
     * [SessionManager] owns the currently active `Session` object (or null if logged out).
     * It reads the persisted token on startup, creates a session if a valid token is found,
     * and tears the session down on logout. Because session state affects every screen, a
     * singleton is required so all observers share the same instance.
     *
     * @param tokenRepository Provides the persisted auth token on startup.
     * @param database Passed through so [SessionManagerImpl] can construct session-scoped
     *   state stores that are backed by the same database instance.
     * @return A singleton [SessionManagerImpl].
     */
    @Provides
    @Singleton
    fun provideSessionManager(
        tokenRepository: TokenRepository,
        database: SpaceTradersDatabase
    ): SessionManager = SessionManagerImpl(tokenRepository, database)

    // -----------------------------------------------------------------------------------------
    // API Clients (Singleton)
    // -----------------------------------------------------------------------------------------

    /**
     * Provides the [AccountsApi] used for agent registration.
     *
     * `AccountsApi` is a concrete class (not an interface) because registration is a
     * one-off bootstrap operation with no testability requirement that demands an interface.
     *
     * @param client The shared Ktor client wrapper.
     * @return A singleton [AccountsApi].
     */
    @Provides
    @Singleton
    fun provideAccountsApi(client: SpaceTradersClient): AccountsApi =
        AccountsApi(client)

    /**
     * Provides the [AgentsApi] implementation for fetching agent (player profile) data.
     *
     * @param client The shared Ktor client wrapper.
     * @return A singleton [AgentsApiImpl] bound to the [AgentsApi] interface.
     */
    @Provides
    @Singleton
    fun provideAgentsApi(client: SpaceTradersClient): AgentsApi =
        AgentsApiImpl(client)

    /**
     * Provides the [ContractsApi] for contract lifecycle operations (accept, fulfill, list).
     *
     * @param client The shared Ktor client wrapper.
     * @return A singleton [ContractsApi].
     */
    @Provides
    @Singleton
    fun provideContractsApi(client: SpaceTradersClient): ContractsApi =
        ContractsApi(client)

    /**
     * Provides the [FleetApi] implementation for ship navigation, docking, and refueling.
     *
     * @param client The shared Ktor client wrapper.
     * @return A singleton [FleetApiImpl] bound to the [FleetApi] interface.
     */
    @Provides
    @Singleton
    fun provideFleetApi(client: SpaceTradersClient): FleetApi =
        FleetApiImpl(client)

    /**
     * Provides the [SystemsApi] implementation for fetching system and waypoint data.
     *
     * @param client The shared Ktor client wrapper.
     * @return A singleton [SystemsApiImpl] bound to the [SystemsApi] interface.
     */
    @Provides
    @Singleton
    fun provideSystemsApi(client: SpaceTradersClient): SystemsApi =
        SystemsApiImpl(client)

    @Provides
    @Singleton
    fun provideShipyardApi(client: SpaceTradersClient): ShipyardApi =
        ShipyardApiImpl(client)

    // -----------------------------------------------------------------------------------------
    // Session-Scoped State (Unscoped — delegates to current session on each injection)
    // -----------------------------------------------------------------------------------------

    /**
     * Provides the [RefreshScheduler] for the current login session.
     *
     * This binding is intentionally **unscoped**: Hilt calls this provider on each injection
     * point rather than caching the result. The provider delegates to
     * `SessionManager.requireSession().refreshScheduler`, which always returns the scheduler
     * belonging to the currently active session. When the user logs in, a new session (and a
     * new scheduler coroutine scope) is created; when they log out, the old session is
     * discarded. If this were `@Singleton`, the stale scheduler from a previous session would
     * be injected after re-login.
     *
     * @param sm The singleton [SessionManager]; `requireSession()` throws if called outside
     *   an authenticated flow.
     * @return The [RefreshScheduler] for the current session.
     */
    @Provides
    fun provideRefreshScheduler(sm: SessionManager): RefreshScheduler =
        sm.requireSession().refreshScheduler

    /**
     * Provides the [WaypointStateStore] for the current login session.
     *
     * Unscoped for the same reason as [provideRefreshScheduler]: the waypoint store is
     * session-scoped because its in-memory cache should be cleared on logout and rebuilt on
     * the next login. The store is backed by the SQLDelight database but holds a reactive
     * in-memory `Map` that is populated lazily per system visit.
     *
     * @param sm The singleton [SessionManager].
     * @return The [WaypointStateStore] for the current session.
     */
    @Provides
    fun provideWaypointStateStore(sm: SessionManager): WaypointStateStore =
        sm.requireSession().waypointStateStore

    // -----------------------------------------------------------------------------------------
    // Repositories
    // -----------------------------------------------------------------------------------------

    /**
     * Provides the [AgentRepository] for reading and caching player agent data.
     *
     * `@Singleton` because the agent profile is global to the app and multiple screens
     * (dashboard, ship list) observe the same `StateFlow`; a singleton ensures they all
     * share one reactive source.
     *
     * @param agentsApi API class for fetching agent data from the SpaceTraders REST API.
     * @param database SQLDelight database used to cache the agent record locally.
     * @return A singleton [AgentRepositoryImpl] bound to [AgentRepository].
     */
    @Provides
    @Singleton
    fun provideAgentRepository(
        agentsApi: AgentsApi,
        database: SpaceTradersDatabase
    ): AgentRepository = AgentRepositoryImpl(agentsApi, database)

    /**
     * Provides the [FleetRepository] for observing and managing the player's ship fleet.
     *
     * Unscoped because [FleetRepositoryImpl] depends on [RefreshScheduler], which is itself
     * session-scoped. Scoping this as `@Singleton` would cause Hilt to cache a
     * `FleetRepository` that holds a reference to a stale session's scheduler after re-login.
     *
     * @param fleetApi API class for ship navigation, docking, and status queries.
     * @param database SQLDelight database used to persist ship records for offline-first access.
     * @param refreshScheduler Session-scoped scheduler that triggers transit completion callbacks.
     * @return A new [FleetRepositoryImpl] wired to the current session's scheduler.
     */
    @Provides
    fun provideFleetRepository(
        fleetApi: FleetApi,
        database: SpaceTradersDatabase,
        refreshScheduler: RefreshScheduler
    ): FleetRepository = FleetRepositoryImpl(fleetApi, database, refreshScheduler)

    /**
     * Provides the [SystemRepository] for fetching and caching waypoint data.
     *
     * Unscoped because [SystemRepositoryImpl] depends on [WaypointStateStore], which is
     * session-scoped. The system repository writes fetched waypoints into the state store,
     * which then notifies all active observers automatically.
     *
     * @param systemsApi API class for paginated waypoint queries.
     * @param waypointStateStore Session-scoped store that holds and emits waypoint data.
     * @return A new [SystemRepositoryImpl] wired to the current session's state store.
     */
    @Provides
    fun provideSystemRepository(
        systemsApi: SystemsApi,
        waypointStateStore: WaypointStateStore
    ): SystemRepository = SystemRepositoryImpl(systemsApi, waypointStateStore)

    @Provides
    fun provideShipyardRepository(
        shipyardApi: ShipyardApi,
        fleetRepository: FleetRepository,
        agentRepository: AgentRepository,
        database: SpaceTradersDatabase
    ): ShipyardRepository = ShipyardRepositoryImpl(shipyardApi, fleetRepository, agentRepository, database)

    @Provides
    @Singleton
    fun provideContractRepository(
        contractsApi: ContractsApi,
        database: SpaceTradersDatabase
    ): ContractRepository = ContractRepositoryImpl(contractsApi, database)

    // -----------------------------------------------------------------------------------------
    // Use Cases
    // -----------------------------------------------------------------------------------------

    /**
     * Provides the [RegisterAgentUseCase] for new agent registration.
     *
     * Registration calls `POST /register` with an `AccountToken`, then calls
     * [SessionManager] to create a new session with the returned `AgentToken`. Because it
     * orchestrates both an API call and session creation, it warrants a use case rather than
     * a direct repository call.
     *
     * @param accountsApi Issues the registration request.
     * @param sessionManager Creates the session from the returned agent token.
     * @return A [RegisterAgentUseCaseImpl].
     */
    @Provides
    fun provideRegisterAgentUseCase(
        accountsApi: AccountsApi,
        sessionManager: SessionManager
    ): RegisterAgentUseCase = RegisterAgentUseCaseImpl(accountsApi, sessionManager)

    /**
     * Provides the [AcceptContractUseCase] for accepting an offered contract.
     *
     * A thin use case that calls `POST /my/contracts/{id}/accept`. Exposed as a use case
     * (rather than called directly on the repository) for consistency with other contract
     * operations and to keep ViewModels decoupled from API classes.
     *
     * @param contractsApi Issues the accept request.
     * @return An [AcceptContractUseCase].
     */
    @Provides
    fun provideAcceptContractUseCase(contractsApi: ContractsApi): AcceptContractUseCase =
        AcceptContractUseCase(contractsApi)

    /**
     * Provides the [GetMyContractsUseCase] for fetching the player's contract list.
     *
     * @param contractsApi Issues the list request.
     * @return A [GetMyContractsUseCase].
     */
    @Provides
    fun provideGetMyContractsUseCase(contractsApi: ContractsApi): GetMyContractsUseCase =
        GetMyContractsUseCase(contractsApi)

    /**
     * Provides the [FulfillContractUseCase] for delivering contract goods and claiming rewards.
     *
     * @param contractsApi Issues the fulfill request.
     * @return A [FulfillContractUseCase].
     */
    @Provides
    fun provideFulfillContractUseCase(contractsApi: ContractsApi): FulfillContractUseCase =
        FulfillContractUseCase(contractsApi)

    /**
     * Provides the [OrbitShipUseCase] for transitioning a ship from DOCKED to IN_ORBIT.
     *
     * Depends on [FleetRepository] so the updated nav state is written to the local DB after
     * the API confirms the orbit command.
     *
     * @param fleetApi Issues the orbit command (`POST /my/ships/{symbol}/orbit`).
     * @param fleetRepository Persists the updated ship state after a successful orbit.
     * @return An [OrbitShipUseCaseImpl].
     */
    @Provides
    fun provideOrbitShipUseCase(
        fleetApi: FleetApi,
        fleetRepository: FleetRepository
    ): OrbitShipUseCase = OrbitShipUseCaseImpl(fleetApi, fleetRepository)

    /**
     * Provides the [DockShipUseCase] for transitioning a ship from IN_ORBIT to DOCKED.
     *
     * @param fleetApi Issues the dock command (`POST /my/ships/{symbol}/dock`).
     * @param fleetRepository Persists the updated ship state after a successful dock.
     * @return A [DockShipUseCaseImpl].
     */
    @Provides
    fun provideDockShipUseCase(
        fleetApi: FleetApi,
        fleetRepository: FleetRepository
    ): DockShipUseCase = DockShipUseCaseImpl(fleetApi, fleetRepository)

    /**
     * Provides the [RefuelShipUseCase] for purchasing fuel at a marketplace.
     *
     * Also depends on [AgentRepository] to update the agent's credit balance after the fuel
     * purchase deducts credits.
     *
     * @param fleetApi Issues the refuel command (`POST /my/ships/{symbol}/refuel`).
     * @param fleetRepository Persists the updated fuel level after refueling.
     * @param agentRepository Updates the agent's credits after the fuel cost is deducted.
     * @return A [RefuelShipUseCaseImpl].
     */
    @Provides
    fun provideRefuelShipUseCase(
        fleetApi: FleetApi,
        fleetRepository: FleetRepository,
        agentRepository: AgentRepository
    ): RefuelShipUseCase = RefuelShipUseCaseImpl(fleetApi, fleetRepository, agentRepository)

    /**
     * Provides the [NavigateShipUseCase] for sending a ship to a new waypoint.
     *
     * Navigation requires the ship to be IN_ORBIT first. The use case handles this
     * automatically by calling [OrbitShipUseCase] if the ship is currently DOCKED before
     * issuing the navigate command. This pre-orbit step is the reason [OrbitShipUseCase]
     * is injected rather than [FleetApi] being called directly.
     *
     * @param fleetApi Issues the navigate command (`POST /my/ships/{symbol}/navigate`).
     * @param fleetRepository Persists the updated nav state (destination, arrival time).
     * @param orbitShipUseCase Automatically puts the ship into orbit if it is docked before
     *   the navigate command is sent.
     * @return A [NavigateShipUseCaseImpl].
     */
    @Provides
    fun provideNavigateShipUseCase(
        fleetApi: FleetApi,
        fleetRepository: FleetRepository,
        orbitShipUseCase: OrbitShipUseCase
    ): NavigateShipUseCase = NavigateShipUseCaseImpl(fleetApi, fleetRepository, orbitShipUseCase)

    @Provides
    fun provideNegotiateContractUseCase(
        fleetApi: FleetApi,
        contractRepository: ContractRepository
    ): NegotiateContractUseCase = NegotiateContractUseCaseImpl(fleetApi, contractRepository)

    @Provides
    fun provideDeliverCargoUseCase(
        contractsApi: ContractsApi,
        contractRepository: ContractRepository
    ): DeliverCargoUseCase = DeliverCargoUseCaseImpl(contractsApi, contractRepository)
}
