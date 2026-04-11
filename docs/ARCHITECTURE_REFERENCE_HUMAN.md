# Architecture Reference: Android/KMP Gold Standard Playbook

> **Purpose:** Personal reference for building modern, scalable Android/KMP apps. Grounded in real decisions made in the SpaceTraders IO project. When starting a new app, read this first.

---

## Table of Contents

1. [High-Level Architecture](#1-high-level-architecture)
2. [UI & State Management (UDF)](#2-ui--state-management-udf)
3. [Routing & Navigation](#3-routing--navigation)
4. [Data & Domain Layer](#4-data--domain-layer)
5. [Networking (Ktor)](#5-networking-ktor)
6. [Dependency Injection (Hilt)](#6-dependency-injection-hilt)
7. [Testing Philosophy](#7-testing-philosophy)

---

## 1. High-Level Architecture

### The Two-Module Split

The project is divided into exactly two Gradle modules, and the boundary between them is a hard architectural contract:

| Module | What it owns | What it must NOT contain |
|---|---|---|
| `:spacetradersiosdk` | Network client, API layer, DTOs, domain models, repository interfaces + impls, use cases, state stores, session management | Any Android/Compose import, any UI concern |
| `:app` | Jetpack Compose screens, ViewModels, Hilt DI module, navigation graph, theme, reusable UI components | Direct Ktor calls, DTO types, any business logic |

**Why this matters:** The SDK compiles for Android *and* iOS (`commonMain`). If any Android-only import leaks into `commonMain`, the iOS build breaks immediately. This hard failure is the enforcement mechanism — the module boundary is not a convention, it is a compiler constraint.

The `:app` module depends on `:spacetradersiosdk`. The SDK has zero knowledge of `:app`. This one-directional dependency means the entire business logic layer can be tested on the JVM without an Android device.

### Data Flow

```mermaid
flowchart TD
    subgraph APP[":app  ─  Android Only"]
        Wrapper["Screen Wrapper\n(stateful, hiltViewModel)"]
        Screen["Screen Content\n(stateless, pure)"]
        VM["ViewModel\n(@HiltViewModel)"]
        NavHost["SpaceTradersNavHost"]
    end

    subgraph SDK[":spacetradersiosdk  ─  commonMain"]
        UC["Use Case\n(interface + operator invoke)"]
        Repo["Repository Impl\n(data/)"]
        Store["EntityStateStore\n(StateFlow&lt;Map&lt;K,T&gt;&gt;)"]
        API["API Impl\n(Ktor suspend fns)"]
        Client["SpaceTradersClient\n(Ktor HttpClient)"]
    end

    REST["SpaceTraders REST API"]

    Wrapper -->|hiltViewModel| VM
    VM -->|"StateFlow&lt;UiState&gt;"| Wrapper
    VM -->|"Channel&lt;NavigationTarget&gt;"| Wrapper
    Wrapper -->|collectAsStateWithLifecycle| Screen
    Screen -->|"onEvent(XxxEvent)"| Wrapper
    VM -->|"calls suspend fn"| UC
    UC -->|"calls"| Repo
    Repo -->|"write-through"| Store
    Store -->|"StateFlow observed by"| VM
    Repo -->|"calls"| API
    API -->|"Ktor request"| Client
    Client -->|"HTTPS"| REST
    REST -->|"JSON DTO"| API
    API -->|"dto.toDomain()"| Repo
```

### The `expect/actual` Mechanism

`Platform.kt` in `commonMain` is the current example. Platform-specific behaviour (e.g., the Ktor HTTP engine — OkHttp on Android, Darwin on iOS) is declared with `expect` in `commonMain` and fulfilled with `actual` in `androidMain`/`iosMain`. Every new platform-specific concern should follow this same pattern rather than reaching for conditional logic in `commonMain`.

---

## 2. UI & State Management (UDF)

### Philosophy

All screens follow **Unidirectional Data Flow (UDF)**:

- **State flows down:** A single `StateFlow<XxxUiState>` is the only source of truth for the UI. The `data class` uses default-value constructor parameters so an "empty" state is always valid.
- **Events flow up:** The user's interactions are expressed as a `sealed interface XxxEvent`. The ViewModel's single `onEvent(event: XxxEvent)` function handles all of them.
- **Navigation is fire-and-forget:** One-shot navigation signals use `Channel<NavigationTarget>(Channel.BUFFERED)`, not `SharedFlow`. SharedFlow re-delivers the last event to new collectors — a subscriber arriving after a config change would immediately navigate again. A `Channel` element is consumed exactly once.

### UiState + Event Pair

```kotlin
// Every field has a safe default. The ViewModel can always construct one without arguments.
data class FooUiState(
    val items: List<Foo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface FooEvent {
    data object RefreshClicked : FooEvent
    data class ItemSelected(val id: String) : FooEvent
    data object ErrorDismissed : FooEvent
    // Navigation events live here too, even if the VM just passes them through:
    // data class ViewDetailClicked(val id: String) : FooEvent
}
```

### ViewModel — Simple Pattern (MutableStateFlow)

Use this when the screen's state is self-contained.

```kotlin
@HiltViewModel
class FooViewModel @Inject constructor(
    private val doFooUseCase: DoFooUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(FooUiState())
    val uiState: StateFlow<FooUiState> = _uiState.asStateFlow()

    private val _navEvent = Channel<NavigationTarget>(Channel.BUFFERED)
    val navEvent = _navEvent.receiveAsFlow()

    fun onEvent(event: FooEvent) {
        when (event) {
            is FooEvent.ItemSelected -> viewModelScope.launch { handleItemSelected(event.id) }
            is FooEvent.RefreshClicked -> viewModelScope.launch { refresh() }
            is FooEvent.ErrorDismissed -> _uiState.update { it.copy(error = null) }
        }
    }

    private suspend fun handleItemSelected(id: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        try {
            doFooUseCase(id)
            _navEvent.send(NavigationTarget.FooDetail(id))
        } catch (e: SpaceTradersApiException) {
            _uiState.update { it.copy(isLoading = false, error = e.message) }
        }
    }

    private suspend fun refresh() { /* ... */ }
}
```

### ViewModel — Derived State Pattern (combine)

Use this when state is composed from multiple reactive sources (e.g., an `EntityStateStore` plus local loading/error signals).

```kotlin
@HiltViewModel
class BarViewModel @Inject constructor(
    private val fooStateStore: FooStateStore,
    private val fooRepository: FooRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    // combine merges multiple flows into one derived StateFlow
    val uiState: StateFlow<BarUiState> = combine(
        fooStateStore.entities,
        _isLoading,
        _error
    ) { entities, isLoading, error ->
        BarUiState(items = entities.values.toList(), isLoading = isLoading, error = error)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,  // see Gotcha below
        initialValue = BarUiState()
    )

    private val _navEvent = Channel<NavigationTarget>(Channel.BUFFERED)
    val navEvent = _navEvent.receiveAsFlow()

    init {
        // Only fetch if the store is empty — avoids redundant API calls on re-entry
        if (fooStateStore.entities.value.isEmpty()) {
            viewModelScope.launch { fooRepository.refreshFoos() }
        }
    }

    fun onEvent(event: BarEvent) {
        when (event) {
            is BarEvent.RefreshClicked -> viewModelScope.launch { fooRepository.refreshFoos() }
        }
    }
}
```

> ⚠️ **Gotcha — `SharingStarted.WhileSubscribed` + `StandardTestDispatcher`:**
> `WhileSubscribed(5000)` keeps the flow alive 5 seconds after the last collector disappears, which is great for production (avoids recomputing on quick back-navigations). But in unit tests with `StandardTestDispatcher`, there is no active subscriber when you read `.value` directly — the upstream `combine` never fires and `.value` stays at its initial value. Use `SharingStarted.Eagerly` in ViewModels when tests need to read `.value` directly.

### Two-Layer Screen Pattern

Every screen is two composables:

```kotlin
// ── Layer 1: Stateful Wrapper ──────────────────────────────────────────────
// Responsible for: injecting ViewModel, collecting navigation events.
// Never contains UI layout code.
@Composable
fun FooScreen(
    onNavigateToDetail: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: FooViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navEvent.collect { target ->
            when (target) {
                is NavigationTarget.FooDetail -> onNavigateToDetail(target.id)
                NavigationTarget.Back -> onNavigateBack()
                else -> Unit  // exhaustiveness: sealed when requires all branches
            }
        }
    }

    FooScreenContent(uiState = uiState, onEvent = viewModel::onEvent)
}

// ── Layer 2: Stateless Content ─────────────────────────────────────────────
// Responsible for: layout. Pure function of (state, event emitter).
// Can be used in @Preview and tested independently.
@Composable
fun FooScreenContent(
    uiState: FooUiState,
    onEvent: (FooEvent) -> Unit
) {
    Column {
        if (uiState.isLoading) LinearProgressIndicator()
        uiState.error?.let { error ->
            Text(error)
            Button(onClick = { onEvent(FooEvent.ErrorDismissed) }) { Text("Dismiss") }
        }
        LazyColumn {
            items(uiState.items) { item ->
                Text(item.name, modifier = Modifier.clickable {
                    onEvent(FooEvent.ItemSelected(item.id))
                })
            }
        }
    }
}
```

**Why two layers?** The stateless content composable can be tested with pure function calls and used in `@Preview` without a ViewModel. The stateful wrapper's single responsibility is bridging the ViewModel lifecycle to the composition lifecycle.

---

## 3. Routing & Navigation

### Two Navigation Concepts

This architecture uses two distinct navigation types that serve different masters:

| Type | Lives in | Purpose |
|---|---|---|
| `@Serializable` Route objects | `navigation/Routes.kt` | NavController-facing — represents a destination in the back stack |
| `NavigationTarget` sealed interface | `navigation/NavigationTarget.kt` | ViewModel-facing — a one-shot signal saying "I want to go somewhere" |

**Why the split?** A ViewModel should not know what `NavController` looks like. It emits a `NavigationTarget.FooDetail(id)`. The screen's `LaunchedEffect` translates that into `navController.navigate(FooDetailRoute(id))`. This keeps the ViewModel testable without a NavController dependency.

### Route Objects

```kotlin
// navigation/Routes.kt
// Objects for screens with no parameters; data classes for parameterized screens.
@Serializable object AuthRoute
@Serializable object DashboardRoute
@Serializable data class FooDetailRoute(val id: String)
@Serializable data class SystemMapRoute(
    val systemSymbol: String,
    val focusWaypointSymbol: String? = null  // optional params use nullable defaults
)
```

### NavigationTarget

```kotlin
// navigation/NavigationTarget.kt
sealed interface NavigationTarget {
    data object Auth : NavigationTarget
    data object Dashboard : NavigationTarget
    data class FooDetail(val id: String) : NavigationTarget
    data object Back : NavigationTarget
}
```

### NavHost (with Auth Gate Pattern)

```kotlin
@Composable
fun AppNavHost(
    tokenRepository: TokenRepository,  // injected by MainActivity, not hiltViewModel
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    // Auth gate: determined once at composition, not recomputed on every recomposition
    val startDestination: Any = if (tokenRepository.hasToken()) DashboardRoute else AuthRoute

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable<AuthRoute> {
            AuthScreen(
                onNavigateToDashboard = {
                    navController.navigate(DashboardRoute) {
                        // Clear AuthRoute from back stack so Back doesn't return to login
                        popUpTo<AuthRoute> { inclusive = true }
                    }
                }
            )
        }

        composable<DashboardRoute> {
            DashboardScreen(
                onNavigateToFooDetail = { id -> navController.navigate(FooDetailRoute(id)) },
                onLogout = {
                    navController.navigate(AuthRoute) {
                        // Clear the entire back stack on logout
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable<FooDetailRoute> {
            FooDetailScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
```

**Rules:**
- Screens receive navigation callbacks as lambdas — they never hold a `NavController` reference.
- `popUpTo { inclusive = true }` prevents the back button from returning to a screen that should no longer be reachable (e.g., the auth screen after login, or authenticated screens after logout).
- `startDestination` is evaluated once, at `NavHost` composition. It is not reactive — to handle mid-session auth state changes, emit a `NavigationTarget` from the ViewModel instead.

---

## 4. Data & Domain Layer

### Clean Architecture Flow

```
REST API → DTO (api/dto/) → Mapper (api/mapper/) → Domain Model (domain/model/)
                                                           ↓
                                              Repository Interface (domain/repository/)
                                                           ↓
                                              Repository Impl (data/repository/)
                                              [writes to EntityStateStore]
                                                           ↓
                                              Use Case (domain/usecase/)
                                                           ↓
                                              ViewModel (:app)
```

**The invariant:** Domain models have zero `@Serializable` or Ktor imports. DTOs have zero domain model imports. The mapper is the only place these two worlds touch.

### DTO + Mapper + Domain Model

```kotlin
// api/dto/FooDto.kt  — mirrors the API schema exactly
@Serializable
data class FooDto(
    val id: String,
    val name: String,
    val count: Int,
    val optionalField: String? = null   // nullable + default for fields the API may omit
)

// api/mapper/FooMapper.kt  — extension function, never a method on the DTO or domain model
fun FooDto.toDomain(): Foo = Foo(
    id = id,
    name = name,
    count = count,
    optionalField = optionalField
)

// domain/model/Foo.kt  — clean data class, no annotations
data class Foo(
    val id: String,
    val name: String,
    val count: Int,
    val optionalField: String?
)
```

### Repository Interface + Implementation

```kotlin
// domain/repository/FooRepository.kt
interface FooRepository {
    suspend fun getFoos(page: Int = 1, limit: Int = 20): List<Foo>
    suspend fun getFoo(id: String): Foo
    suspend fun refreshFoos()
}

// data/repository/FooRepositoryImpl.kt
class FooRepositoryImpl(
    private val fooApi: FooApi,
    private val fooStateStore: FooStateStore,
    private val refreshScheduler: RefreshScheduler
) : FooRepository {

    override suspend fun getFoos(page: Int, limit: Int): List<Foo> {
        val foos = fooApi.getFoos(page, limit).data.map { it.toDomain() }
        fooStateStore.putAll(foos.associateBy { it.id })  // write-through to store
        foos.forEach { scheduleTimerIfNeeded(it) }
        return foos
    }

    override suspend fun getFoo(id: String): Foo {
        val foo = fooApi.getFoo(id).toDomain()
        fooStateStore.put(id, foo)                        // single-entity write-through
        scheduleTimerIfNeeded(foo)
        return foo
    }

    override suspend fun refreshFoos() { getFoos() }

    private fun scheduleTimerIfNeeded(foo: Foo) {
        foo.expiresAt?.let { expiry ->
            refreshScheduler.schedule(
                id = "foo:${foo.id}",       // idempotent — re-scheduling replaces the entry
                expiresAt = expiry,
                action = { getFoo(foo.id) }
            )
        }
    }
}
```

**Key rule:** The repository is the **only writer** to `EntityStateStore`. ViewModels are read-only observers. This ensures that any update — user action, timer, or initial fetch — flows through the same write path and notifies all observers simultaneously.

### Use Case Interface + Implementation

Use cases justify their existence by encapsulating **business orchestration** that spans more than one call, enforces pre-conditions, or composes other use cases. A use case that simply delegates a single call to a repository with no logic should be skipped — call the repository directly.

```kotlin
// domain/usecase/NavigateFooUseCase.kt
interface NavigateFooUseCase {
    // operator fun invoke makes the use case callable as: useCase(id, destination)
    suspend operator fun invoke(id: String, destination: String): NavigationResult
}

class NavigateFooUseCaseImpl(
    private val fooApi: FooApi,
    private val fooStateStore: FooStateStore,
    private val prepareUseCase: PrepareFooUseCase   // use cases can compose other use cases
) : NavigateFooUseCase {

    override suspend operator fun invoke(id: String, destination: String): NavigationResult {
        // Pre-condition: ensure foo is in the correct state before navigating
        val current = fooStateStore.entities.value[id]
        if (current?.status == FooStatus.DOCKED) {
            prepareUseCase(id)  // operator invoke — called like a function
        }

        // Execute the primary action
        val result = fooApi.navigate(id, destination).toDomain()

        // Merge the response back into the store (avoid an extra GET call)
        fooStateStore.update(id) { it.copy(status = result.newStatus, route = result.route) }

        return result
    }
}
```

### EntityStateStore — Reactive State Base

```kotlin
// domain/state/EntityStateStore.kt
class EntityStateStore<K, T> {
    private val _entities = MutableStateFlow<Map<K, T>>(emptyMap())
    val entities: StateFlow<Map<K, T>> = _entities.asStateFlow()

    // observe() returns a flow for a single key — use in ViewModels watching one entity
    fun observe(key: K): Flow<T?> = _entities.map { it[key] }.distinctUntilChanged()

    // All mutations use MutableStateFlow.update() for atomic compare-and-set
    fun put(key: K, entity: T) = _entities.update { it + (key to entity) }
    fun putAll(map: Map<K, T>) = _entities.update { it + map }
    fun update(key: K, transform: (T) -> T) = _entities.update { m ->
        m[key]?.let { m + (key to transform(it)) } ?: m
    }
    fun remove(key: K) = _entities.update { it - key }
    fun clear() = _entities.update { emptyMap() }
}

// Domain-specific stores are zero-boilerplate type aliases
class FooStateStore : EntityStateStore<String, Foo>()
class BarStateStore : EntityStateStore<String, Bar>()
```

### Session Lifecycle

All state stores and the `RefreshScheduler` are grouped into a `SpaceTradersSession` that is created on login and destroyed on logout. This ensures stale state from a previous session never leaks into a new one.

```kotlin
// domain/session/SpaceTradersSession.kt
class SpaceTradersSession(private val scope: CoroutineScope) {
    val refreshScheduler = RefreshScheduler(scope)
    val fooStateStore = FooStateStore()
    val agentStateStore = AgentStateStore()

    fun onResume() { refreshScheduler.onResume() }    // fire expired timers on foreground
    fun destroy() { scope.cancel() }                  // cancels all pending timers
}

// domain/session/SessionManager.kt
class SessionManager(private val tokenRepository: TokenRepository) {
    private var _session: SpaceTradersSession? = null

    fun requireSession(): SpaceTradersSession =
        _session ?: error("No active session. User must be authenticated.")

    fun login(token: String) {
        tokenRepository.saveToken(token)
        _session = SpaceTradersSession(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }

    fun logout() {
        _session?.destroy()
        _session = null
        tokenRepository.clearToken()
    }

    fun restoreIfAuthenticated() {
        if (tokenRepository.hasToken() && _session == null) {
            _session = SpaceTradersSession(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        }
    }
}
```

**`SupervisorJob()`** is critical here — it ensures that if one timer's refresh action throws an exception, it does not cancel the entire session's coroutine scope and bring down all other timers.

---

## 5. Networking (Ktor)

### SpaceTradersClient

The client is the single point of Ktor configuration. Every API impl receives an instance and calls `client.authenticated` or `client.unauthenticated`.

```kotlin
class SpaceTradersClient(
    private val tokenRepository: TokenRepository,
    // This lambda is the test seam — production code never sets it;
    // tests inject a MockEngine-backed factory here.
    internal val httpClientFactory: ((token: String?) -> HttpClient)? = null
) {
    // ⚠️ These are get properties, NOT val.
    // A val would capture the token at construction time. If a user registers
    // and the token is saved *after* this class is instantiated, val would
    // miss it. A get property rebuilds the client on every access, picking
    // up the freshly saved token without restarting the app.
    val unauthenticated: HttpClient
        get() = httpClientFactory?.invoke(null) ?: buildHttpClient(null)

    val authenticated: HttpClient
        get() = httpClientFactory?.invoke(tokenRepository.getToken())
            ?: buildHttpClient(tokenRepository.getToken())

    fun authenticatedWith(token: String): HttpClient =
        httpClientFactory?.invoke(token) ?: buildHttpClient(token)

    private fun buildHttpClient(token: String?): HttpClient = HttpClient(/* OkHttp on Android, Darwin on iOS */) {
        install(ContentNegotiation) {
            // isLenient + ignoreUnknownKeys: handles API evolution gracefully
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }

        // HttpCallValidator intercepts BEFORE ContentNegotiation tries to deserialize.
        // This is how we convert HTTP 4xx/5xx into typed domain exceptions.
        install(HttpCallValidator) {
            handleResponseExceptionWithRequest { cause, _ ->
                val httpException = cause as? ClientRequestException ?: return@handleResponseExceptionWithRequest
                val errorBody = httpException.response.body<ErrorResponseDto>()
                throw SpaceTradersApiException(
                    error = errorBody.error.toDomain(),
                    httpStatus = httpException.response.status.value
                )
            }
        }

        defaultRequest {
            url("https://api.spacetraders.io/v2/")
            // ⚠️ This sets Content-Type globally. Any POST endpoint that sends
            // an empty body MUST use setBody("{}") — an empty body with
            // Content-Type: application/json causes a 422 from the API.
            contentType(ContentType.Application.Json)
            if (token != null) headers.append("Authorization", "Bearer $token")
        }

        install(Logging) {
            logger = NapierLogger()
            level = LogLevel.INFO
        }
    }
}
```

### API Interface + Implementation

```kotlin
// api/endpoints/FooApi.kt
interface FooApi {
    suspend fun getFoos(page: Int = 1, limit: Int = 20): PaginatedResponse<FooDto>
    suspend fun getFoo(id: String): FooDto
    suspend fun performAction(id: String): ActionResultDto   // POST, no request body
    suspend fun performActionWithBody(id: String, body: ActionRequestDto): ActionResultDto
}

class FooApiImpl(private val client: SpaceTradersClient) : FooApi {

    override suspend fun getFoos(page: Int, limit: Int): PaginatedResponse<FooDto> =
        client.authenticated.get("foos") {
            parameter("page", page)
            parameter("limit", limit)
        }.body()

    override suspend fun getFoo(id: String): FooDto =
        // Many endpoints wrap the response in { "data": { ... } }
        client.authenticated.get("foos/$id").body<ApiResponse<FooDto>>().data

    // ⚠️ setBody("{}") is mandatory for POST endpoints with no body.
    // The defaultRequest sets Content-Type globally; sending truly empty body
    // with that header causes 422 Unprocessable Entity from the SpaceTraders API.
    override suspend fun performAction(id: String): ActionResultDto =
        client.authenticated.post("foos/$id/action") { setBody("{}") }
            .body<ApiResponse<ActionResultDto>>().data

    override suspend fun performActionWithBody(id: String, body: ActionRequestDto): ActionResultDto =
        client.authenticated.post("foos/$id/action") { setBody(body) }
            .body<ApiResponse<ActionResultDto>>().data
}
```

### Error Handling in ViewModels

```kotlin
viewModelScope.launch {
    _uiState.update { it.copy(isLoading = true, error = null) }
    try {
        val result = fooUseCase(id, param)
        _navEvent.send(NavigationTarget.Success)
    } catch (e: SpaceTradersApiException) {
        val message = when (val err = e.error) {
            is SpaceTradersError.AuthError.InvalidToken -> "Token expired. Please log in again."
            is SpaceTradersError.NavigationError.ShipInTransit -> "Ship is already in transit."
            is SpaceTradersError.ShipOperationError.ShipNotInOrbit -> "Ship must be in orbit first."
            is SpaceTradersError.GeneralError -> err.message ?: "An error occurred."
            else -> "Error ${e.httpStatus}: ${e.error}"
        }
        _uiState.update { it.copy(isLoading = false, error = message) }
    }
}
```

---

## 6. Dependency Injection (Hilt)

### Three Scoping Tiers

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object SdkModule {

    // ─────────────────────────────────────────────────────────────────────────
    // Tier 1: @Singleton — infrastructure that lives for the entire app process
    // ─────────────────────────────────────────────────────────────────────────

    @Provides @Singleton
    fun provideTokenRepository(): TokenRepository =
        TokenRepositoryImpl(Settings())   // Settings() uses SharedPreferences on Android

    @Provides @Singleton
    fun provideSpaceTradersClient(repo: TokenRepository): SpaceTradersClient =
        SpaceTradersClient(repo)

    @Provides @Singleton
    fun provideSessionManager(repo: TokenRepository): SessionManager =
        SessionManagerImpl(repo)

    @Provides @Singleton
    fun provideFooApi(client: SpaceTradersClient): FooApi = FooApiImpl(client)

    // ─────────────────────────────────────────────────────────────────────────
    // Tier 2: Unscoped — session-dependent state (delegates to current session)
    //
    // WHY unscoped? These objects belong to the *session*, not the app process.
    // If @Singleton were used here, the store would survive logout and serve
    // stale data to the next user. Unscoped means Hilt calls requireSession()
    // on every injection, always returning the current session's store.
    //
    // WHY is this safe? Authenticated screens are always behind navigation guards.
    // requireSession() only throws if there is no active session — which cannot
    // happen on screens that are only reachable after login.
    // ─────────────────────────────────────────────────────────────────────────

    @Provides
    fun provideFooStateStore(sm: SessionManager): FooStateStore =
        sm.requireSession().fooStateStore

    @Provides
    fun provideAgentStateStore(sm: SessionManager): AgentStateStore =
        sm.requireSession().agentStateStore

    @Provides
    fun provideRefreshScheduler(sm: SessionManager): RefreshScheduler =
        sm.requireSession().refreshScheduler

    // ─────────────────────────────────────────────────────────────────────────
    // Tier 3: Unscoped — repositories and use cases (stateless or session-scoped
    // via their injected dependencies)
    // ─────────────────────────────────────────────────────────────────────────

    @Provides
    fun provideFooRepository(
        api: FooApi,
        store: FooStateStore,
        scheduler: RefreshScheduler
    ): FooRepository = FooRepositoryImpl(api, store, scheduler)

    @Provides
    fun provideNavigateFooUseCase(
        api: FooApi,
        store: FooStateStore,
        prepareUseCase: PrepareFooUseCase
    ): NavigateFooUseCase = NavigateFooUseCaseImpl(api, store, prepareUseCase)
}
```

### Application and Activity Setup

```kotlin
// SpaceTradersApplication.kt
@HiltAndroidApp
class SpaceTradersApplication : Application() {
    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var appLifecycleObserver: AppLifecycleObserver

    override fun onCreate() {
        super.onCreate()
        sessionManager.restoreIfAuthenticated()
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
    }
}

// MainActivity.kt
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var tokenRepository: TokenRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                AppNavHost(tokenRepository = tokenRepository)
            }
        }
    }
}
```

> ⚠️ **Gotcha — `ProcessLifecycleOwner` dependency:**
> Using `ProcessLifecycleOwner.get()` requires adding `androidx-lifecycle-process` to `app/build.gradle.kts`. It is not bundled with `lifecycle-runtime-ktx`. Without it, the class will not be found at runtime.

---

## 7. Testing Philosophy

### Standards

- **100% class, method, line, and branch coverage** for all new code. No exceptions.
- **No mocking frameworks** (no Mockk, no Mockito). Hand-write fakes that implement the same interface.
- **No testing of implementation details** — test public contracts (inputs → outputs, state transitions, events emitted).
- **SDK tests** live in `spacetradersiosdk/src/androidHostTest/` and run on the JVM.
- **App ViewModel tests** live in `app/src/test/` and run on the JVM.

### The MockEngine Pattern

All SDK HTTP tests use Ktor's `MockEngine`. The `buildMockSpaceTradersClient` factory is the central test helper:

```kotlin
// testHelpers/TestHelpers.kt

// FakeTokenRepository — hand-written fake, used across all tests
class FakeTokenRepository(var storedToken: String? = "test-token") : TokenRepository {
    override fun getToken(): String? = storedToken
    override fun saveToken(token: String) { storedToken = token }
    override fun clearToken() { storedToken = null }
    override fun hasToken(): Boolean = storedToken != null
}

// buildMockSpaceTradersClient — the only way to build a test client
// ⚠️ The token MUST be passed through. If you use { _ -> ... } instead of
//    { token -> ... }, the Authorization header is never set and auth tests fail.
// ⚠️ defaultRequest with contentType IS required — omitting it causes
//    "Fail to prepare request body" because setBody() requires Content-Type.
fun buildMockSpaceTradersClient(
    tokenRepository: TokenRepository = FakeTokenRepository(),
    handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
): SpaceTradersClient = SpaceTradersClient(
    tokenRepository = tokenRepository,
    httpClientFactory = { token ->
        HttpClient(MockEngine { request -> handler(request) }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest {
                contentType(ContentType.Application.Json)
                if (token != null) headers.append("Authorization", "Bearer $token")
            }
        }
    }
)
```

### MockRequestHandleScope Response Helpers

Response helpers MUST be `MockRequestHandleScope` extension functions. `respond()` is only available as an extension on that scope — it is not a standalone function.

```kotlin
// ✅ Correct — extension on MockRequestHandleScope
private fun MockRequestHandleScope.okJson(content: String) = respond(
    content = content,
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
)

private fun MockRequestHandleScope.errorJson(status: HttpStatusCode, content: String) = respond(
    content = content,
    status = status,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
)

// ❌ Wrong — standalone function; respond() not available here
fun okJson(content: String) = respond(...)  // does not compile
```

### Fake API Classes

Hand-written fakes implement the API interface and expose spy properties for asserting what was called:

```kotlin
class FakeFooApi(
    private val foos: List<FooDto> = listOf(testFooDto()),
    private val singleFoo: FooDto = testFooDto()
) : FooApi {

    // Spy properties — assert on these in tests
    var getFoosCallCount = 0
    var lastGetFoosPage: Int = -1
    var lastGetFoosLimit: Int = -1
    var lastGetFooId: String? = null

    override suspend fun getFoos(page: Int, limit: Int): PaginatedResponse<FooDto> {
        getFoosCallCount++
        lastGetFoosPage = page
        lastGetFoosLimit = limit
        return PaginatedResponse(data = foos, meta = MetaDto(total = foos.size, page = page, limit = limit))
    }

    override suspend fun getFoo(id: String): FooDto {
        lastGetFooId = id
        return singleFoo
    }

    override suspend fun performAction(id: String): ActionResultDto = testActionResultDto()
}
```

### ViewModel Test Pattern

```kotlin
class FooViewModelTest {

    // StandardTestDispatcher: coroutines do NOT run until you call advanceUntilIdle()
    // This gives you control over timing — check state before and after async work
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `item selected emits navigation event`() = runTest {
        val vm = FooViewModel(doFooUseCase = FakeDoFooUseCase())

        // Turbine's .test{} extension collects Channel/Flow emissions
        vm.navEvent.test {
            vm.onEvent(FooEvent.ItemSelected("foo-1"))
            advanceUntilIdle()                             // let coroutines run

            val target = awaitItem()
            assertIs<NavigationTarget.FooDetail>(target)
            assertEquals("foo-1", target.id)
        }
    }

    @Test
    fun `error state set when use case throws`() = runTest {
        val vm = FooViewModel(
            doFooUseCase = FakeDoFooUseCase(throws = SpaceTradersApiException(...))
        )

        vm.onEvent(FooEvent.ItemSelected("foo-1"))
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        assertEquals(false, vm.uiState.value.isLoading)
    }
}
```

> ⚠️ **Gotcha — `backgroundScope` for `RefreshScheduler`:**
> Do NOT create `RefreshScheduler(this)` inside `runTest`. The scheduler launches a long-lived coroutine; `runTest` will see it as uncompleted and throw `UncompletedCoroutinesError`. Always use `backgroundScope`:
> ```kotlin
> val scheduler = RefreshScheduler(backgroundScope)
> ```
> `backgroundScope` is cancelled automatically at the end of the test without causing the error.

> ⚠️ **Gotcha — Far-future arrival times in test fixtures:**
> Transit ships in test fixtures must use a far-future `arrivalTime` such as `"2099-01-01T01:00:00.000Z"`. A past expiry date causes `delay(0)` in `RefreshScheduler`, which causes the refresh action to reschedule itself immediately, creating an infinite loop that OOMs the JVM test runner.

> ⚠️ **Gotcha — `kotlin-test-junit` in `:app` tests:**
> Use `kotlin-test-junit` (not plain `kotlin-test`) for JVM ViewModel tests. Plain `kotlin-test` lacks the JVM-specific bridge and `@BeforeTest`/`@AfterTest` annotations will not be resolved correctly.

> ⚠️ **Gotcha — Material3 `RoundedCornerShape(0.dp)` vs `RectangleShape`:**
> Material3 `Shapes` slots (e.g., `ButtonDefaults.shape`) require a `CornerBasedShape`. `RectangleShape` is the generic `Shape` type and does not satisfy that constraint — it won't compile. For sharp corners, use `RoundedCornerShape(0.dp)` instead.
