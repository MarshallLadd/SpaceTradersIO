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
| State stores, session, scheduler | `:spacetradersiosdk` | `sdk/domain/state/`, `sdk/domain/session/` |

**Data flow (text):**
```
Compose Screen → ViewModel → UseCase → Repository → API → SpaceTradersClient (Ktor) → REST
     ↑ StateFlow<UiState>         ↑ EntityStateStore   ↑ DTO.toDomain()
     ↑ Channel<NavigationTarget>
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
- Repository impl: in `data/repository/`. Orchestrates API calls, writes to `EntityStateStore`, registers `RefreshScheduler` timers.
- Repository is the **only writer** to state stores. ViewModels are read-only observers.
- Use cases: `interface` + `operator fun invoke()` in `domain/usecase/`. Justify existence with orchestration logic (pre-conditions, multi-step, composing other use cases). Single-call delegation with no logic = skip the use case.
- `EntityStateStore` mutations use `MutableStateFlow.update{}` (atomic). Never `.value =` on maps.

**DTO + Mapper + Domain:**
```kotlin
@Serializable data class FooDto(val id: String, val name: String, val optionalField: String? = null)
fun FooDto.toDomain() = Foo(id, name, optionalField)
data class Foo(val id: String, val name: String, val optionalField: String?)
```

**Repository:**
```kotlin
interface FooRepository { suspend fun getFoo(id: String): Foo }

class FooRepositoryImpl(private val api: FooApi, private val store: FooStateStore) : FooRepository {
    override suspend fun getFoo(id: String): Foo {
        val foo = api.getFoo(id).toDomain()
        store.put(id, foo)
        return foo
    }
}
```

**UseCase:**
```kotlin
interface DoFooUseCase { suspend operator fun invoke(id: String): FooResult }

class DoFooUseCaseImpl(private val api: FooApi, private val store: FooStateStore) : DoFooUseCase {
    override suspend operator fun invoke(id: String): FooResult {
        val result = api.doFoo(id).toDomain()
        store.update(id) { it.copy(status = result.newStatus) }
        return result
    }
}
```

**EntityStateStore:**
```kotlin
class EntityStateStore<K, T> {
    private val _entities = MutableStateFlow<Map<K, T>>(emptyMap())
    val entities: StateFlow<Map<K, T>> = _entities.asStateFlow()
    fun observe(key: K): Flow<T?> = _entities.map { it[key] }.distinctUntilChanged()
    fun put(key: K, entity: T) = _entities.update { it + (key to entity) }
    fun putAll(map: Map<K, T>) = _entities.update { it + map }
    fun update(key: K, transform: (T) -> T) = _entities.update { m -> m[key]?.let { m + (key to transform(it)) } ?: m }
    fun remove(key: K) = _entities.update { it - key }
    fun clear() = _entities.update { emptyMap() }
}
class FooStateStore : EntityStateStore<String, Foo>()
```

**Session:**
```kotlin
class SpaceTradersSession(scope: CoroutineScope) {
    val refreshScheduler = RefreshScheduler(scope)
    val fooStateStore = FooStateStore()
    fun onResume() { refreshScheduler.onResume() }
    fun destroy() { scope.cancel() }
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
- `@Singleton`: `TokenRepository`, `SpaceTradersClient`, `SessionManager`, all API impls.
- Unscoped (no annotation): session-dependent stores + scheduler — delegate to `sm.requireSession().xxxStore`. Safe because authenticated screens are behind nav guards.
- Unscoped: repositories and use cases (stateless or session-scoped via injected deps).
- `@HiltViewModel` on every ViewModel. `@AndroidEntryPoint` on `MainActivity`. `@HiltAndroidApp` on `Application`.

**SdkModule skeleton:**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object SdkModule {
    // --- @Singleton: infrastructure ---
    @Provides @Singleton fun provideTokenRepository(): TokenRepository = TokenRepositoryImpl(Settings())
    @Provides @Singleton fun provideClient(repo: TokenRepository): SpaceTradersClient = SpaceTradersClient(repo)
    @Provides @Singleton fun provideSessionManager(repo: TokenRepository): SessionManager = SessionManagerImpl(repo)
    @Provides @Singleton fun provideFooApi(client: SpaceTradersClient): FooApi = FooApiImpl(client)

    // --- Unscoped: session-dependent state ---
    @Provides fun provideFooStateStore(sm: SessionManager): FooStateStore = sm.requireSession().fooStateStore
    @Provides fun provideRefreshScheduler(sm: SessionManager): RefreshScheduler = sm.requireSession().refreshScheduler

    // --- Unscoped: repositories + use cases ---
    @Provides fun provideFooRepository(api: FooApi, store: FooStateStore, sched: RefreshScheduler): FooRepository =
        FooRepositoryImpl(api, store, sched)
    @Provides fun provideDoFooUseCase(api: FooApi, store: FooStateStore): DoFooUseCase =
        DoFooUseCaseImpl(api, store)
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
