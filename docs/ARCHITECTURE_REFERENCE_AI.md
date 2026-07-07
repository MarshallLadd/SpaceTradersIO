# Architecture Reference: AI Context Version

> Compressed pattern rules for Android/KMP apps. Same 7 sections as ARCHITECTURE_REFERENCE_HUMAN.md — this version is optimized for AI context injection, not narrative reading.

Package namespace: `com.brokenhuskysledteam.spacetradersio` (app), `com.brokenhuskysledteam.spacetradersio.sdk` (SDK)

---

## 1. High-Level Architecture

**Rules:**
- Two modules only: `:spacetradersiosdk` (business logic) and `:app` (Android UI/DI).
- `:spacetradersiosdk` `commonMain` must have zero Android/Compose imports — compiler enforces this.
- `:app` must have zero Ktor/DTO imports — all network access goes through SDK interfaces.
- Dependency direction: `:app` → `:spacetradersiosdk`. Never the reverse.
- Platform-specific behaviour uses `expect/actual`. Never conditional logic in `commonMain`.

**Layer ownership table:**

| Layer | Module | Package path |
|---|---|---|
| Compose screens, ViewModels, theme | `:app` | `ui/` |
| Hilt DI module | `:app` | `di/SdkModule.kt` |
| Navigation routes + NavHost | `:app` | `navigation/` |
| API impls, DTOs, mappers | `:spacetradersiosdk` | `sdk/api/` |
| Domain models, use cases, repository interfaces | `:spacetradersiosdk` | `sdk/domain/` |
| Repository impls, data sources | `:spacetradersiosdk` | `sdk/data/` |
| Session, scheduler, waypoint store | `:spacetradersiosdk` | `sdk/domain/state/`, `sdk/domain/session/` |
| SQLDelight DB (ships, agents) | `:spacetradersiosdk` | `sdk/data/db/` |

**Data flow (text):**
```
Compose Screen → ViewModel → UseCase → Repository → API → SpaceTradersClient (Ktor) → REST
     ↑ StateFlow<UiState>         ↑ SQLDelight DB   ↑ DTO.toDomain()
     ↑ Channel<NavigationTarget>  (ships/agents: Flow from DB; waypoints: WaypointStateStore)
```

---

## 2. UI & State Management (UDF)

**Rules:**
- One `StateFlow<XxxUiState>` per screen. All fields have safe defaults. Never null-check state.
- One `sealed interface XxxEvent` per screen. All user actions go through `onEvent(event)`.
- Internal: `MutableStateFlow`. Exposed: `StateFlow` via `.asStateFlow()`.
- For derived state from multiple flows: use `combine(...).stateIn(viewModelScope, SharingStarted.Eagerly, initial)`.
- Use `SharingStarted.Eagerly` (not `WhileSubscribed`) when tests read `.value` directly.
- Navigation: `Channel<NavigationTarget>(Channel.BUFFERED)`, exposed via `.receiveAsFlow()`. Never `SharedFlow` (re-delivers on re-subscription).
- Screens: two-layer pattern. Stateful wrapper = hiltViewModel + LaunchedEffect for nav events. Stateless content = pure (state, events) → UI.
- **LocalState pattern**: When a ViewModel has multiple transient local signals (loading, error, pending dialogs), group them into a single private `data class LocalState(...)` backed by one `MutableStateFlow<LocalState>`. Combine this with repository flows. Fewer flows = simpler `combine()` call; `_localState.update { it.copy(...) }` atomically updates multiple fields.
- **Non-fatal secondary enrichment**: Use `runCatching { }` when fetching supplementary data that should not block the primary screen. The derived flag (e.g., `hasShipyard`) defaults to `false` if the secondary call fails — the screen remains usable.

**ViewModel skeleton:**
```kotlin
@HiltViewModel
class FooViewModel @Inject constructor(
    private val fooUseCase: FooUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(FooUiState())
    val uiState: StateFlow<FooUiState> = _uiState.asStateFlow()

    private val _navEvent = Channel<NavigationTarget>(Channel.BUFFERED)
    val navEvent = _navEvent.receiveAsFlow()

    fun onEvent(event: FooEvent) {
        when (event) {
            is FooEvent.ItemSelected -> viewModelScope.launch { handle(event.id) }
            is FooEvent.ErrorDismissed -> _uiState.update { it.copy(error = null) }
        }
    }

    private suspend fun handle(id: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        try {
            fooUseCase(id)
            _navEvent.send(NavigationTarget.FooDetail(id))
        } catch (e: SpaceTradersApiException) {
            _uiState.update { it.copy(isLoading = false, error = e.message) }
        }
    }
}

data class FooUiState(val items: List<Foo> = emptyList(), val isLoading: Boolean = false, val error: String? = null)
sealed interface FooEvent {
    data class ItemSelected(val id: String) : FooEvent
    data object ErrorDismissed : FooEvent
}
```

**LocalState ViewModel variant (multi-field local state + repository Flow):**
```kotlin
@HiltViewModel
class ShipyardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val shipyardRepository: ShipyardRepository
) : ViewModel() {

    private val waypointSymbol: String = checkNotNull(savedStateHandle["waypointSymbol"])
    private val _localState = MutableStateFlow(LocalState())

    val uiState: StateFlow<ShipyardUiState> = combine(
        shipyardRepository.observeShipyard(waypointSymbol),
        _localState
    ) { shipyard, local ->
        ShipyardUiState(shipyard = shipyard, isRefreshing = local.isRefreshing, error = local.error, ...)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ShipyardUiState())

    init { refresh() }

    private fun refresh() {
        _localState.update { it.copy(isRefreshing = true, error = null) }
        viewModelScope.launch {
            try { shipyardRepository.refreshShipyard(...) }
            catch (e: Exception) { _localState.update { it.copy(error = e.message) } }
            finally { _localState.update { it.copy(isRefreshing = false) } }
        }
    }

    // Non-fatal secondary enrichment: failure leaves hasShipyard = false, screen still usable.
    private fun loadWithEnrichment() {
        viewModelScope.launch {
            fleetRepository.refreshMyShip(shipSymbol)
            val ship = fleetRepository.observeShip(shipSymbol).first()
            if (ship != null) {
                runCatching {
                    val waypoint = systemRepository.getWaypoint(ship.nav.systemSymbol, ship.nav.waypointSymbol)
                    _localState.update { it.copy(hasShipyard = waypoint.traits.any { t -> t.symbol == WaypointTraitSymbol.SHIPYARD }) }
                }
            }
        }
    }

    private data class LocalState(
        val isRefreshing: Boolean = true,
        val error: String? = null,
        val pendingAction: SomeType? = null   // any number of local-only fields
    )
}
```

**Screen skeleton:**
```kotlin
@Composable
fun FooScreen(onNavigateToDetail: (String) -> Unit, viewModel: FooViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.navEvent.collect { when (it) {
            is NavigationTarget.FooDetail -> onNavigateToDetail(it.id)
            else -> Unit
        }}
    }
    FooScreenContent(uiState, viewModel::onEvent)
}

@Composable
fun FooScreenContent(uiState: FooUiState, onEvent: (FooEvent) -> Unit) { /* pure UI */ }
```

---

## 3. Routing & Navigation

**Rules:**
- Route objects: `@Serializable object XxxRoute` (no params) or `@Serializable data class XxxRoute(val id: String)` (with params).
- `NavigationTarget`: ViewModel-facing sealed interface. ViewModels emit these; screens translate to `navController.navigate(XxxRoute(...))`.
- NavHost uses `composable<T>` DSL — never string-based routes.
- Screens receive nav callbacks as lambdas — never a `NavController` reference.
- Auth gate: `popUpTo<AuthRoute> { inclusive = true }` after login. `popUpTo(0) { inclusive = true }` on logout.
- `startDestination` is evaluated once at NavHost composition from `tokenRepository.hasToken()`.
- Adding a new nav event to a ViewModel's sealed event interface requires a branch in the VM's `when`, even if the VM passes it through: `is MyEvent.Nav -> Unit`.

**Route skeleton:**
```kotlin
@Serializable object AuthRoute
@Serializable object DashboardRoute
@Serializable data class FooDetailRoute(val id: String)

sealed interface NavigationTarget {
    data object Auth : NavigationTarget
    data object Dashboard : NavigationTarget
    data class FooDetail(val id: String) : NavigationTarget
}
```

**NavHost skeleton:**
```kotlin
@Composable
fun AppNavHost(tokenRepository: TokenRepository, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val startDestination: Any = if (tokenRepository.hasToken()) DashboardRoute else AuthRoute
    NavHost(navController, startDestination, modifier) {
        composable<AuthRoute> {
            AuthScreen(onNavigateToDashboard = {
                navController.navigate(DashboardRoute) { popUpTo<AuthRoute> { inclusive = true } }
            })
        }
        composable<DashboardRoute> {
            DashboardScreen(
                onNavigateToFoo = { id -> navController.navigate(FooDetailRoute(id)) },
                onLogout = { navController.navigate(AuthRoute) { popUpTo(0) { inclusive = true } } }
            )
        }
        composable<FooDetailRoute> { FooDetailScreen(onNavigateBack = { navController.popBackStack() }) }
    }
}
```

---

## 4. Data & Domain Layer

**Rules:**
- DTOs: `@Serializable data class` in `api/dto/`. Mirror API schema exactly. Nullable + default for optional fields.
- Mappers: `fun DtoType.toDomain(): DomainType` extension functions in `api/mapper/`. Never serialize in domain layer.
- Domain models: plain `data class` in `domain/model/`. Zero annotations.
- Repository interface: in `domain/repository/`. Application layer depends on interface only.
- Repository impl: in `data/repository/`. Calls API, persists to SQLDelight DB (ships/agents) or `EntityStateStore` (waypoints), registers `RefreshScheduler` timers.
- Repository is the **only writer** to the DB and state stores. ViewModels are read-only observers.
- Use cases: `interface` + `operator fun invoke()` in `domain/usecase/`. Justify existence with orchestration logic (pre-conditions, multi-step, composing other use cases). Single-call delegation with no logic = skip the use case.
- Ships and agents use SQLDelight as persistence layer — Flow observation via `asFlow().mapToList/mapToOneOrNull(Dispatchers.Default)`. Never `Dispatchers.IO` in `commonMain` (iOS incompatible).
- Waypoints still use `WaypointStateStore` (in-memory `EntityStateStore<String, Waypoint>`) — no DB backing needed.
- `EntityStateStore` mutations use `MutableStateFlow.update{}` (atomic). Never `.value =` on maps.

**DTO + Mapper + Domain:**
```kotlin
@Serializable data class FooDto(val id: String, val name: String, val optionalField: String? = null)
fun FooDto.toDomain() = Foo(id, name, optionalField)
data class Foo(val id: String, val name: String, val optionalField: String?)
```

**Repository (SQLDelight-backed, e.g. ships/agents):**
```kotlin
interface ShipRepository {
    fun observeShips(): Flow<List<Ship>>
    fun observeShip(symbol: String): Flow<Ship?>
    suspend fun refreshMyShips(page: Int = 1, limit: Int = 20)
    suspend fun saveShip(ship: Ship)
    suspend fun updateShipNav(symbol: String, nav: ShipNav)
    suspend fun clearAll()
}

class ShipRepositoryImpl(
    private val api: FleetApi,
    private val database: SpaceTradersDatabase,
    private val refreshScheduler: RefreshScheduler
) : ShipRepository {
    override fun observeShips(): Flow<List<Ship>> =
        database.shipQueries.selectAll().asFlow().mapToList(Dispatchers.Default).map { rows -> rows.map { it.toDomain() } }
    override fun observeShip(symbol: String): Flow<Ship?> =
        database.shipQueries.selectBySymbol(symbol).asFlow().mapToOneOrNull(Dispatchers.Default).map { it?.toDomain() }
    override suspend fun refreshMyShips(page: Int, limit: Int) {
        val ships = api.getMyShips(page, limit).data.map { it.toDomain() }
        ships.forEach { database.shipQueries.upsertShip(it.toEntity()) }
        refreshScheduler.scheduleRefresh(/* transit ships */)
    }
    override suspend fun saveShip(ship: Ship) { database.shipQueries.upsertShip(ship.toEntity()) }
    override suspend fun updateShipNav(symbol: String, nav: ShipNav) { database.shipQueries.updateNav(/* fields */) }
    override suspend fun clearAll() { database.shipQueries.deleteAllShips() }
}
```

**Fog-of-war sentinel (nullable List<T> in SQLDelight):**

When a domain model has `val ships: List<T>?` where `null` = fog-of-war and `emptyList()` = no listings, a relational table alone cannot distinguish them — both states produce zero child rows. Add a sentinel column to the parent table:

```sql
CREATE TABLE shipyard (
    symbol TEXT NOT NULL PRIMARY KEY,
    modifications_fee INTEGER NOT NULL,
    -- 1 = API returned a list (even if empty). 0 = fog-of-war (ships field was absent/null).
    ships_cached INTEGER NOT NULL DEFAULT 0
);
```

In `refreshXxx()`, persist the intent:
```kotlin
database.shipyardQueries.upsert(
    symbol = dto.symbol,
    modifications_fee = dto.modificationsFee.toLong(),
    ships_cached = if (dto.ships != null) 1L else 0L
)
```

In `observeXxx()`, restore it:
```kotlin
combine(
    database.shipyardQueries.selectBySymbol(waypointSymbol).asFlow().mapToOneOrNull(Dispatchers.Default),
    database.shipyardShipQueries.selectByWaypoint(waypointSymbol).asFlow().mapToList(Dispatchers.Default)
) { meta, ships ->
    meta?.toDomain(if (meta.ships_cached == 1L) ships.map { it.toDomain() } else null)
}
```

**Contract repository (two-table SQLDelight design):**

`ContractRepository` follows the same cache-then-network pattern as `ShipRepository` with two differences:

- **Two tables:** `contract` + `contract_deliver_good`. On every upsert the repository runs a single SQLDelight `transaction { }` that upserts the parent row then does `deleteByContractId` + re-inserts for each deliver good. This keeps deliver-good rows consistent without a merge strategy.
- **Status at mapper boundary:** `ContractStatus` is computed by `computeContractStatus()` inside `ContractDto.toDomain()` using `Clock.System.now()`, then stored as a TEXT column (`'ACTIVE'`, `'UNACCEPTED'`, etc.). The SQL queries filter directly by status string (`WHERE status = 'UNACCEPTED' OR status = 'ACTIVE'`), avoiding date arithmetic in SQL. Status in the DB can grow stale between API calls — this is acceptable because `refreshContracts()` recomputes and re-stores status on every network fetch.
- **`@Singleton` is safe** because `ContractRepositoryImpl` only depends on `ContractsApi` and `SpaceTradersDatabase` (both `@Singleton`). Unlike `FleetRepository`, it has no `RefreshScheduler` dependency.
- **`observeContracts` returns rows for one tab at a time** (active or history), paged by `limit`/`offset`.

**`ContractsViewModel` — QueryKey + flatMapLatest pattern:**

`LocalState` alone is not enough when a ViewModel must re-subscribe to a *different* SQL query (different tab or page), not just re-combine the same query with new local state. Use:

```kotlin
private val _localState = MutableStateFlow(LocalState())

// Re-subscribes to DB only when tab/page/limit change — not on isLoading/pendingAccept mutations.
@OptIn(ExperimentalCoroutinesApi::class)
private val contractsFlow = _localState
    .map { QueryKey(it.selectedTab, it.currentPage, it.limit) }
    .distinctUntilChanged()
    .flatMapLatest { key ->
        val offset = ((key.page - 1) * key.limit).toLong()
        contractRepository.observeContracts(key.tab, key.limit.toLong(), offset)
    }

val uiState = combine(contractsFlow, _localState) { contracts, local ->
    ContractsUiState(contracts = contracts, selectedTab = local.selectedTab, ...)
}.stateIn(viewModelScope, SharingStarted.Eagerly, ContractsUiState())

private data class QueryKey(val tab: ContractTab, val page: Int, val limit: Int)
```

`distinctUntilChanged()` prevents `flatMapLatest` from restarting the DB subscription every time any `_localState` field changes. The `QueryKey` contains only the fields that actually change the SQL query.

**Repository (StateStore-backed, e.g. waypoints):**
```kotlin
interface WaypointRepository { suspend fun getWaypoints(systemSymbol: String): List<Waypoint> }

class WaypointRepositoryImpl(private val api: SystemsApi, private val store: WaypointStateStore) : WaypointRepository {
    override suspend fun getWaypoints(systemSymbol: String): List<Waypoint> {
        val waypoints = api.getSystemWaypoints(systemSymbol).data.map { it.toDomain() }
        store.putAll(waypoints.associateBy { it.symbol })
        return waypoints
    }
}
```

**UseCase:**
```kotlin
interface DoFooUseCase { suspend operator fun invoke(id: String): FooResult }

class DoFooUseCaseImpl(private val api: FooApi, private val repository: FooRepository) : DoFooUseCase {
    override suspend operator fun invoke(id: String): FooResult {
        val result = api.doFoo(id).toDomain()
        repository.updateFooState(id, result.newState)
        return result
    }
}
```

**Session (interface + impl):**
```kotlin
interface SpaceTradersSession {
    val refreshScheduler: RefreshScheduler
    val contractStateStore: ContractStateStore
    val waypointStateStore: WaypointStateStore
    val database: SpaceTradersDatabase
    val isActive: Boolean
    fun onResume()
    fun destroy()
}

class SpaceTradersSessionImpl(
    private val scope: CoroutineScope,
    override val database: SpaceTradersDatabase
) : SpaceTradersSession {
    override val refreshScheduler = RefreshScheduler(scope)
    override val contractStateStore = ContractStateStore()
    override val waypointStateStore = WaypointStateStore()
    override val isActive: Boolean get() = scope.isActive
    override fun onResume() { refreshScheduler.onResume() }
    override fun destroy() {
        scope.cancel()
        database.shipQueries.deleteAllShips()
        database.agentQueries.deleteAll()
    }
}
```

---

## 5. Networking (Ktor)

**Rules:**
- `authenticated` and `unauthenticated` are `get` properties (NOT `val`) — rebuilds on access to pick up freshly saved tokens.
- `httpClientFactory` lambda is the test seam for `MockEngine`. Production code never sets it.
- `defaultRequest { contentType(ContentType.Application.Json) }` is mandatory — omitting it causes `setBody()` failures.
- `HttpCallValidator` intercepts non-2xx before ContentNegotiation — parses error body, throws `SpaceTradersApiException(error, httpStatus)`.
- POST endpoints with no body: always use `setBody("{}")`. Empty body + `Content-Type: application/json` = 422.
- API class: interface in `api/endpoints/`, impl takes `SpaceTradersClient` (not `HttpClient`).
- Single-entity responses unwrap: `.body<ApiResponse<FooDto>>().data`. Paginated: `.body<PaginatedResponse<FooDto>>()`.

**SpaceTradersClient skeleton:**
```kotlin
class SpaceTradersClient(
    private val tokenRepository: TokenRepository,
    internal val httpClientFactory: ((token: String?) -> HttpClient)? = null
) {
    val unauthenticated: HttpClient get() = httpClientFactory?.invoke(null) ?: buildHttpClient(null)
    val authenticated: HttpClient get() = httpClientFactory?.invoke(tokenRepository.getToken()) ?: buildHttpClient(tokenRepository.getToken())

    private fun buildHttpClient(token: String?): HttpClient = HttpClient(/* platform engine */) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        install(HttpCallValidator) {
            handleResponseExceptionWithRequest { cause, _ ->
                val e = cause as? ClientRequestException ?: return@handleResponseExceptionWithRequest
                throw SpaceTradersApiException(e.response.body<ErrorResponseDto>().error.toDomain(), e.response.status.value)
            }
        }
        defaultRequest {
            url("https://api.spacetraders.io/v2/")
            contentType(ContentType.Application.Json)
            if (token != null) headers.append("Authorization", "Bearer $token")
        }
    }
}
```

**API impl skeleton:**
```kotlin
interface FooApi {
    suspend fun getFoos(page: Int = 1, limit: Int = 20): PaginatedResponse<FooDto>
    suspend fun getFoo(id: String): FooDto
    suspend fun doAction(id: String): ActionResultDto
}

class FooApiImpl(private val client: SpaceTradersClient) : FooApi {
    override suspend fun getFoos(page: Int, limit: Int) =
        client.authenticated.get("foos") { parameter("page", page); parameter("limit", limit) }.body<PaginatedResponse<FooDto>>()
    override suspend fun getFoo(id: String) =
        client.authenticated.get("foos/$id").body<ApiResponse<FooDto>>().data
    override suspend fun doAction(id: String) =                          // empty POST — must use setBody("{}")
        client.authenticated.post("foos/$id/action") { setBody("{}") }.body<ApiResponse<ActionResultDto>>().data
}
```

---

## 6. Dependency Injection (Hilt)

**Rules:**
- Single module: `@Module @InstallIn(SingletonComponent::class) object SdkModule`.
- `@Singleton`: `TokenRepository`, `SpaceTradersClient`, `SessionManager`, `SpaceTradersDatabase`, `AgentRepository`, all API impls.
- `SpaceTradersDatabase` is a singleton: `SqlDriverFactory(@ApplicationContext context).create()` — one DB for the app lifetime.
- `AgentRepository` is singleton because it only depends on `AgentsApi` + `SpaceTradersDatabase` (both singletons).
- `ContractRepository` is `@Singleton` for the same reason — depends only on `ContractsApi` + `SpaceTradersDatabase`, no session-scoped deps.
- `FleetRepository` is **unscoped** because it depends on `RefreshScheduler` (session-scoped). Same for use cases that touch session state.
- A repository that depends on another **unscoped** (session-dependent) repository must itself be unscoped — even if it has no direct session dep. Example: `ShipyardRepository` depends on `FleetRepository` and `AgentRepository` (both unscoped), so it too is unscoped. Making it `@Singleton` would hold stale session references across login/logout.
- Unscoped: session-dependent scheduler + state stores — delegate to `sm.requireSession().xxxStore`.
- `@HiltViewModel` on every ViewModel. `@AndroidEntryPoint` on `MainActivity`. `@HiltAndroidApp` on `Application`.

**SdkModule skeleton (three tiers):**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object SdkModule {
    // --- Tier 1: @Singleton infrastructure ---
    @Provides @Singleton fun provideTokenRepository(): TokenRepository = TokenRepositoryImpl(Settings())
    @Provides @Singleton fun provideClient(repo: TokenRepository): SpaceTradersClient = SpaceTradersClient(repo)
    @Provides @Singleton fun provideDatabase(@ApplicationContext ctx: Context): SpaceTradersDatabase =
        SpaceTradersDatabase(SqlDriverFactory(ctx).create())
    @Provides @Singleton fun provideSessionManager(repo: TokenRepository, db: SpaceTradersDatabase): SessionManager =
        SessionManagerImpl(repo, db)
    @Provides @Singleton fun provideFooApi(client: SpaceTradersClient): FooApi = FooApiImpl(client)
    @Provides @Singleton fun provideAgentRepository(api: AgentsApi, db: SpaceTradersDatabase): AgentRepository =
        AgentRepositoryImpl(api, db)

    // --- Tier 2: Unscoped session-dependent state ---
    @Provides fun provideWaypointStateStore(sm: SessionManager): WaypointStateStore = sm.requireSession().waypointStateStore
    @Provides fun provideRefreshScheduler(sm: SessionManager): RefreshScheduler = sm.requireSession().refreshScheduler

    // --- Tier 3: Unscoped repositories + use cases ---
    @Provides fun provideFleetRepository(api: FleetApi, db: SpaceTradersDatabase, sched: RefreshScheduler): FleetRepository =
        FleetRepositoryImpl(api, db, sched)
    @Provides fun provideDoFooUseCase(api: FooApi, repo: FooRepository): DoFooUseCase =
        DoFooUseCaseImpl(api, repo)
}
```

---

## 7. Testing Philosophy

**Rules:**
- 100% class, method, line, and branch coverage for all new code.
- No mocking frameworks. Hand-write fakes implementing the same interface.
- SDK tests in `androidHostTest/` — use `MockEngine` for HTTP layer, fakes for repository/usecase interfaces.
- App ViewModel tests in `app/src/test/` — use `kotlin-test-junit` (not plain `kotlin-test`), `StandardTestDispatcher`, Turbine for Channel/Flow.
- `MockEngine` handler must be a `MockRequestHandleScope` extension — `respond()` is not available standalone.
- `buildMockSpaceTradersClient` must pass token through (`{ token -> ... }` not `{ _ -> ... }`).
- `buildMockSpaceTradersClient` must include `defaultRequest { contentType(...) }`.
- `RefreshScheduler` in tests: use `backgroundScope`, not `this`. Prevents `UncompletedCoroutinesError`.
- Transit test fixtures: always use far-future `arrivalTime = "2099-01-01T01:00:00.000Z"`. Past dates → infinite refresh loop → OOM.
- `SharingStarted.Eagerly` in ViewModels when tests read `.value` directly.
- Fake repositories for ViewModel tests: back with `MutableStateFlow<Map<String, T>>(emptyMap())` — start **empty**. Populate only inside `refreshXxx()`. Pre-populating the flow bypasses the loading path and breaks error-state tests.
- Use case fakes that mutate entity state should call `repo.updateXxx()` (not `store.update()`), matching the production contract.
- When a test needs state before ViewModel init (e.g. a ship already in cache), add a synchronous `preloadXxx(entity)` helper to the fake repository that calls `_state.update { ... }` directly.

**Test helper skeleton:**
```kotlin
class FakeTokenRepository(var storedToken: String? = "test-token") : TokenRepository {
    override fun getToken() = storedToken
    override fun saveToken(token: String) { storedToken = token }
    override fun clearToken() { storedToken = null }
    override fun hasToken() = storedToken != null
}

fun buildMockSpaceTradersClient(
    tokenRepository: TokenRepository = FakeTokenRepository(),
    handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
) = SpaceTradersClient(tokenRepository, httpClientFactory = { token ->
    HttpClient(MockEngine { handler(it) }) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        defaultRequest {
            contentType(ContentType.Application.Json)
            if (token != null) headers.append("Authorization", "Bearer $token")
        }
    }
})

private fun MockRequestHandleScope.okJson(content: String) = respond(
    content, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
)
```

**Fake API skeleton:**
```kotlin
class FakeFooApi(private val result: FooDto = testFooDto()) : FooApi {
    var lastGetId: String? = null
    var getCallCount = 0
    override suspend fun getFoos(page: Int, limit: Int) = PaginatedResponse(listOf(result), testMeta())
    override suspend fun getFoo(id: String): FooDto { lastGetId = id; getCallCount++; return result }
    override suspend fun doAction(id: String) = testActionResultDto()
}
```

**ViewModel test skeleton:**
```kotlin
class FooViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    @BeforeTest fun setUp() { Dispatchers.setMain(testDispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `nav event emitted on success`() = runTest {
        val vm = FooViewModel(FakeDoFooUseCase())
        vm.navEvent.test {
            vm.onEvent(FooEvent.ItemSelected("id-1"))
            advanceUntilIdle()
            assertIs<NavigationTarget.FooDetail>(awaitItem())
        }
    }

    @Test
    fun `error state on failure`() = runTest {
        val vm = FooViewModel(FakeDoFooUseCase(throws = testApiException()))
        vm.onEvent(FooEvent.ItemSelected("id-1"))
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.error)
    }
}
```

**Live E2E tests (opt-in, real API):**
- `*LiveTest` classes in `androidHostTest/.../e2e/` hit the **real** SpaceTraders API for true end-to-end verification of each feature slice.
- Gated by `-Pe2e` (see SDK `build.gradle.kts`), which forwards the account token from `secrets.properties` (gitignored) as a JVM system property. Without `-Pe2e` the tests self-skip via `assumeE2eEnabled()`, so normal builds/CI never touch the network.
- Run: `./gradlew :spacetradersiosdk:testAndroidHostTest -Pe2e --tests "*LiveTest"`.
- Build a real client with `SpaceTradersClient(tokenRepository, httpClientFactory = null)` (null factory → OkHttp engine on the JVM). `registerE2eAgent()` registers a fresh randomized-callsign agent per run and stores its token in a `FakeTokenRepository` → reproducible, reset-proof.
- Use `runBlocking` (not `runTest`) — real network calls need real time.

---

## Critical Gotchas (quick reference)

| Gotcha | Rule |
|---|---|
| Empty POST body | Always `setBody("{}")`. Empty body + `Content-Type: application/json` = 422 |
| `authenticated` property | It's a `get` property, not `val`. Never cache the result. |
| `MockRequestHandleScope` | Response helpers MUST be extensions on the scope. `respond()` is not standalone. |
| `buildMockSpaceTradersClient` token | Pass token through: `{ token -> ... }`. Never `{ _ -> ... }`. |
| `RefreshScheduler` in tests | Use `backgroundScope`, not `this`. Prevents `UncompletedCoroutinesError`. |
| Transit test fixtures | `arrivalTime = "2099-01-01T01:00:00.000Z"`. Past dates → infinite loop → OOM. |
| `SharingStarted` in tests | Use `Eagerly` if tests read `.value` without a subscriber. |
| Material3 sharp corners | `RoundedCornerShape(0.dp)`, not `RectangleShape`. M3 requires `CornerBasedShape`. |
| `ProcessLifecycleOwner` | Requires `androidx-lifecycle-process` dep (not bundled with lifecycle-runtime-ktx). |
| `kotlin-test` in app tests | Use `kotlin-test-junit` bridge. Plain `kotlin-test` missing JVM annotations. |
| `@Volatile` in `commonMain` | `import kotlin.concurrent.Volatile`. `synchronized {}` is JVM-only. |
| `Dispatchers.IO` in `commonMain` | Not available — iOS has no IO dispatcher. Use `Dispatchers.Default` for SQLDelight `asFlow().mapToList/mapToOneOrNull(Dispatchers.Default)`. |
| Fake repository init in tests | Start `MutableStateFlow` **empty** (`emptyMap()`). Populate only inside `refreshXxx()`. Pre-populating skips the load path and breaks error-state assertions. |
| Cross-module smart cast | Kotlin cannot smart-cast a public property from another module. Capture in a `val` first: `val ships = shipyard.ships; if (ships != null) { items(ships) { ... } }`. |
| `combine` + single `update` timing in tests | A single `_localState.update {}` schedules a combine re-emission on the test dispatcher — it does NOT execute synchronously. Call `testDispatcher.scheduler.advanceUntilIdle()` after each state-changing `onEvent()` before reading `uiState.value`. Two consecutive updates before an advance coalesce (only the final state emits). |
| Fog-of-war nullable list in SQLDelight | A table cannot distinguish "no rows = null" from "no rows = empty list". Add a sentinel column (`ships_cached INTEGER`) and set it to `1` when the API returned a list, `0` for fog-of-war. Read it in the observe flow to decide `null` vs mapped list. |
| `ContractRepository @Singleton` is safe | `ContractRepository` depends only on `ContractsApi` and `SpaceTradersDatabase` (both singletons). Unlike `FleetRepository` (which holds `RefreshScheduler`), it has no session-scoped dep → `@Singleton` is correct. |
| `ContractStatus` stored as TEXT in DB | Status is computed from timestamps in the mapper via `computeContractStatus()` and stored as TEXT. SQL tab queries filter by status string directly. Status can grow stale if the clock advances between refreshes — acceptable because `refreshContracts()` recomputes status on every network call. |
| `QueryKey + flatMapLatest` in `ContractsViewModel` | Use `_localState.map { QueryKey(...) }.distinctUntilChanged().flatMapLatest { ... }` when the ViewModel must re-subscribe to a *different* SQL query (different tab/page), not just re-combine the same one. `distinctUntilChanged()` prevents `flatMapLatest` from restarting on every ephemeral `LocalState` mutation (e.g., `isLoading`, `pendingAccept`). |
| Cargo inventory as JSON column | A ship's `cargo.inventory` list is persisted as a single JSON `cargo_inventory TEXT` column on the `ship` row (not a child table). It's a value list bound 1:1 to a ship, always read/written with the aggregate cargo counts, never queried independently — the case where a serialized column beats a child table. `ShipDbMapper` owns the `Json` encode/decode; `CargoItem` is `@Serializable` solely for this. (Contrast the fog-of-war nullable-list case, which *does* need relational rows.) |
| Live E2E gating | `*LiveTest` classes call `assumeE2eEnabled()` first and only run under `-Pe2e`. Never rely on a shell env var to enable them — Gradle does not reliably forward env to the test worker JVM; the `-Pe2e` project property is wired to a system property in `build.gradle.kts`. |
| Read-through repository for volatile data | `MarketRepository` has **no** DB cache — `getMarket` fetches fresh every call and maps straight to domain, because market prices change with every trade. Only cache data that is stable between fetches (agent, ships, contracts, waypoints). Its ViewModel holds the fetched value in `LocalState`, but combines live `AgentRepository.observeAgent()` for the credit balance so it updates reactively after a trade. |
| Buy/sell = multi-repo use cases | `BuyCargoUseCase`/`SellCargoUseCase` mirror `RefuelShipUseCase`: one API call returns `{cargo, agent, transaction}`; the use case maps all three first, then writes `cargo`→`FleetRepository.updateShipCargo` and `agent`→`AgentRepository.saveAgent`. They must be **unscoped** in Hilt (they depend on the session-scoped `FleetRepository`). |
| Cooldown auto-refresh is active | `FleetRepositoryImpl.updateShipCooldown` schedules a `cooldown:$ship` RefreshScheduler timer when `expiration != null` (mirrors the transit timer in `updateShipNav`). So after an extract/survey the ship auto-refreshes ~1s after the cooldown clears, and any ViewModel observing the ship (e.g. mining) re-enables its actions automatically. Test fixtures MUST use far-future cooldown expirations (`2099-…`) — a past expiry fires `delay(0)` immediately. |
