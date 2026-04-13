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
- `FleetRepository` is **unscoped** because it depends on `RefreshScheduler` (session-scoped). Same for use cases that touch session state.
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
