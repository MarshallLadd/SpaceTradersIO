# Unified State Management & Smart Refresh System

**Date:** 2026-04-06
**Status:** Draft
**Problem:** Game state is fragmented across screens. A ship completing transit on the detail screen does not update the list screen. Each ViewModel fetches independently with no shared reactive state, no caching, and no automatic refresh on timed event completion.

**Goal:** Refactor toward a single source of truth (SSOT) state management system with centralized, timer-driven refresh that scales to all 200+ SpaceTraders API endpoints.

---

## Architecture Overview

The new architecture inserts two layers between repositories and ViewModels:

```
Composable Screen
    │ collectAsStateWithLifecycle()
    ▼
ViewModel (per-screen, read-only observer)
    │ observes StateFlows from domain stores
    │ dispatches actions via use cases / repositories
    ▼
Use Cases (thin orchestration, unchanged role)
    │ calls repository methods
    ▼
Domain State Stores (NEW — per-domain, in SDK commonMain)
    │ FleetStateStore, AgentStateStore, ContractStateStore, ...
    │ Each holds MutableStateFlow<Map<K, Entity>>
    │ Single writer: only the owning repository writes
    ▼
Repositories (ENHANCED — write-through to stores)
    │ fetch from API → merge into store → return to caller
    │ register timed events with RefreshScheduler
    ▼
RefreshScheduler (NEW — centralized timer engine, SDK commonMain)
    │ Priority-queue of ScheduledRefresh entries
    │ Single coroutine sleeps until next expiry + 1s
    │ On app resume: fires all expired entries immediately
    ▼
API Layer (unchanged)
    │ SpaceTradersClient → Ktor → REST API
```

**Key invariant:** Repositories are the *only* writers to state stores. ViewModels are read-only observers. Any API response — user action, timer refresh, or initial load — flows through the same store, and all observers react instantly.

---

## State Store Design

### Generic Base

```kotlin
// sdk/domain/state/EntityStateStore.kt
class EntityStateStore<K, T> {
    private val _entities = MutableStateFlow<Map<K, T>>(emptyMap())
    val entities: StateFlow<Map<K, T>> = _entities.asStateFlow()

    fun observe(key: K): Flow<T?> =
        _entities.map { it[key] }.distinctUntilChanged()

    fun observeAll(): StateFlow<Map<K, T>> = entities

    fun put(key: K, entity: T)
    fun putAll(entities: Map<K, T>)
    fun update(key: K, transform: (T) -> T)
    fun remove(key: K)
    fun clear()
}
```

**Thread safety:** `MutableStateFlow.value` assignment is atomic. All mutations use `_entities.update { map -> map + (key to entity) }` (the atomic `update` function on `MutableStateFlow`), ensuring concurrent writes from different coroutines merge correctly without locks.
```

### Domain-Specific Stores

```kotlin
// sdk/domain/state/FleetStateStore.kt
class FleetStateStore : EntityStateStore<String, Ship>()
// Key = ship symbol (e.g., "AGENT-1")

// sdk/domain/state/AgentStateStore.kt
class AgentStateStore {
    private val _agent = MutableStateFlow<Agent?>(null)
    val agent: StateFlow<Agent?> = _agent.asStateFlow()
    fun update(agent: Agent) { _agent.value = agent }
    fun clear() { _agent.value = null }
}

// sdk/domain/state/ContractStateStore.kt
class ContractStateStore : EntityStateStore<String, Contract>()
// Key = contract ID
```

**AgentStateStore** is a special case — there's exactly one agent per session, so it holds a single `Agent?` rather than a map.

### ViewModel Consumption Pattern

```kotlin
// Before: ViewModel fetches in init, holds isolated state
class ShipListViewModel(private val fleetRepository: FleetRepository) {
    init { loadShips() }  // API call on every screen visit
}

// After: ViewModel observes store, triggers refresh only when empty
class ShipListViewModel(
    private val fleetStateStore: FleetStateStore,
    private val fleetRepository: FleetRepository
) {
    val ships: StateFlow<List<Ship>> = fleetStateStore.observeAll()
        .map { it.values.toList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        if (fleetStateStore.entities.value.isEmpty()) {
            viewModelScope.launch { fleetRepository.refreshMyShips() }
        }
    }
}
```

`SharingStarted.WhileSubscribed(5000)` keeps the flow active for 5 seconds after the last collector disappears, preventing re-computation on quick rotations or back-navigations.

### Repository Write-Through Pattern

```kotlin
class FleetRepositoryImpl(
    private val fleetApi: FleetApi,
    private val fleetStateStore: FleetStateStore,
    private val refreshScheduler: RefreshScheduler
) : FleetRepository {

    override suspend fun getMyShip(shipSymbol: String): Ship {
        val ship = fleetApi.getMyShip(shipSymbol).data.toDomain()
        fleetStateStore.put(shipSymbol, ship)
        registerTimersForShip(ship)
        return ship
    }

    override suspend fun refreshMyShips(page: Int, limit: Int) {
        val ships = fleetApi.getMyShips(page, limit).data.map { it.toDomain() }
        fleetStateStore.putAll(ships.associateBy { it.symbol })
        ships.forEach { registerTimersForShip(it) }
    }

    private fun registerTimersForShip(ship: Ship) {
        if (ship.nav.status == ShipNavStatus.IN_TRANSIT) {
            refreshScheduler.schedule(
                id = "transit:${ship.symbol}",
                expiresAt = ship.nav.route.arrivalTime,
                action = { getMyShip(ship.symbol) }
            )
        }
        ship.cooldown?.expiration?.let { expiry ->
            refreshScheduler.schedule(
                id = "cooldown:${ship.symbol}",
                expiresAt = expiry,
                action = { getMyShip(ship.symbol) }
            )
        }
    }
}
```

Timer registration is **idempotent** — scheduling with the same `id` replaces the previous entry.

---

## RefreshScheduler Design

Centralized timer engine that handles all timed game events.

### Interface

```kotlin
// sdk/domain/scheduler/RefreshScheduler.kt
class RefreshScheduler(private val scope: CoroutineScope) {
    private val pending = MutableStateFlow<Map<String, ScheduledRefresh>>(emptyMap())

    fun schedule(id: String, expiresAt: Instant, action: suspend () -> Unit)
    fun cancel(id: String)
    fun cancelByPrefix(prefix: String)

    fun onResume()  // Called on app foreground — fires all expired entries

    val activeTimers: StateFlow<Map<String, ScheduledRefresh>>  // Observable for UI
}

data class ScheduledRefresh(
    val id: String,
    val expiresAt: Instant,
    val action: suspend () -> Unit
)
```

### Execution Model

Single coroutine loop (not one coroutine per timer):

1. Find the earliest `expiresAt` in the pending map
2. `delay(until earliest + 1 second)` — the +1s accounts for server processing time and clock drift
3. Execute the expired entry's `action` suspend function
4. Remove from pending map
5. Loop to step 1

**On `schedule()`:** Adds/replaces entry in pending map. If the new entry is earlier than the current sleep target, restarts the loop.

**On `cancel()`:** Removes entry. If the cancelled entry was the current sleep target, restarts the loop.

**On `onResume()`:** Iterates all pending entries, fires any whose `expiresAt + 1s < Clock.System.now()`, removes them.

**Error handling:** If `action` throws, log the error via Napier and remove the entry. No retry — the next user-initiated navigation will trigger a fresh fetch anyway.

### Timer ID Convention

| Event Type | ID Pattern | expiresAt Source |
|---|---|---|
| Ship transit | `transit:{symbol}` | `ship.nav.route.arrivalTime` |
| Ship cooldown | `cooldown:{symbol}` | `ship.cooldown.expiration` |
| Contract deadline | `contract:{id}` | `contract.terms.deadline` |
| Market refresh | `market:{waypoint}` | last fetch + TTL |

### UI Countdown Display

Countdown display **stays in Compose** as a pure display concern. The `LaunchedEffect` ticks for visual countdown but does NOT trigger API calls — the `RefreshScheduler` handles that centrally:

```kotlin
@Composable
fun TransitProgress(ship: Ship) {
    if (ship.nav.status != ShipNavStatus.IN_TRANSIT) return
    val arrivalTime = ship.nav.route.arrivalTime

    var remainingSeconds by remember(ship.symbol) {
        mutableLongStateOf(max(0L, (arrivalTime - Clock.System.now()).inWholeSeconds))
    }

    LaunchedEffect(ship.symbol, arrivalTime) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds = max(0L, (arrivalTime - Clock.System.now()).inWholeSeconds)
        }
        // Timer reaches zero — no API call here.
        // RefreshScheduler fires the refresh centrally.
    }

    TerminalProgressBar(...)
}
```

---

## Session & Lifecycle Management

### Auth-Scoped Session

The session (and all its stores + scheduler) is tied to authentication, not app lifecycle. Logging out destroys all cached state and cancels all timers.

```kotlin
// sdk/domain/session/SpaceTradersSession.kt
class SpaceTradersSession(private val scope: CoroutineScope) {
    val refreshScheduler = RefreshScheduler(scope)
    val fleetStateStore = FleetStateStore()
    val agentStateStore = AgentStateStore()
    val contractStateStore = ContractStateStore()

    fun onResume() { refreshScheduler.onResume() }

    fun destroy() {
        scope.cancel()  // Cancels all pending timers
    }
}
```

### SessionManager

```kotlin
// sdk/domain/session/SessionManager.kt
class SessionManager(private val tokenRepository: TokenRepository) {
    private var _session: SpaceTradersSession? = null

    fun requireSession(): SpaceTradersSession =
        _session ?: error("No active session. User must be authenticated.")

    fun login(token: String) {
        tokenRepository.saveToken(token)
        _session = SpaceTradersSession(
            CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )
    }

    fun logout() {
        _session?.destroy()
        _session = null
        tokenRepository.clearToken()
    }

    fun restoreIfAuthenticated() {
        if (tokenRepository.hasToken() && _session == null) {
            _session = SpaceTradersSession(
                CoroutineScope(SupervisorJob() + Dispatchers.Default)
            )
        }
    }
}
```

**`SupervisorJob()`** isolates failures — one ship's failed refresh doesn't cancel all other timers.

### Android Integration

```kotlin
// di/SdkModule.kt
@Provides @Singleton
fun provideSessionManager(tokenRepository: TokenRepository): SessionManager =
    SessionManager(tokenRepository)

// Unscoped: creates fresh reference to current session's store each time
@Provides
fun provideFleetStateStore(sm: SessionManager): FleetStateStore =
    sm.requireSession().fleetStateStore

@Provides
fun provideRefreshScheduler(sm: SessionManager): RefreshScheduler =
    sm.requireSession().refreshScheduler
```

`requireSession()` is safe because authenticated screens are behind navigation guards — `AuthScreen` validates the token before navigating to any screen that injects stores.

### Lifecycle Bridge

```kotlin
// Android app module
class AppLifecycleObserver(
    private val sessionManager: SessionManager
) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        sessionManager.requireSession().onResume()
    }
}

// Registered in Application.onCreate():
ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
```

`ProcessLifecycleOwner` tracks app-level foreground/background, not individual Activities.

### Future: Background Scheduling

The `RefreshScheduler` interface is designed to support a future `ScheduledRefreshExecutor` abstraction:

- **Current implementation:** In-memory coroutine timers + `onResume()` catch-up
- **Future Android implementation:** `WorkManager` one-time work requests scheduled at `expiresAt + 1s`
- **Future iOS implementation:** `BGTaskScheduler` with similar timing

The `onResume()` method serves as the bridge — it catches any expirations that background scheduling missed.

---

## Granular Update Strategy

### Principle: Use the most efficient endpoint, merge surgically

| Trigger | Endpoint | Store Update |
|---|---|---|
| Ship transit arrival (timer) | `GET /my/ships/{symbol}` | `fleetStateStore.put(symbol, ship)` |
| Ship cooldown expiry (timer) | `GET /my/ships/{symbol}` | `fleetStateStore.put(symbol, ship)` |
| Contract deadline (timer) | `GET /my/contracts/{id}` | `contractStateStore.put(id, contract)` |
| Ship action (orbit/dock) | Action response (no extra GET) | `fleetStateStore.update(symbol) { it.copy(nav = newNav) }` |
| Refuel | Action response (no extra GET) | `fleetStateStore.update(...)` + `agentStateStore.update(...)` |
| First fleet screen visit | `GET /my/ships` (paginated) | `fleetStateStore.putAll(...)` |

**Action responses merge directly.** Orbit/dock/refuel return updated fields in the response. The repository merges these into the store without making an additional GET call.

**Timer refreshes use single-entity endpoints.** `GET /my/ships/{symbol}` fetches one ship, not the entire fleet.

**Cross-domain updates from a single action.** Refueling changes fuel (fleet domain) and credits (agent domain). The repository writes to both stores:

```kotlin
suspend fun refuelShip(symbol: String): RefuelResult {
    val result = fleetApi.refuelShip(symbol).data.toDomain()
    fleetStateStore.update(symbol) { ship -> ship.copy(fuel = result.fuel) }
    agentStateStore.update(result.agent)
    return result
}
```

**Bulk fetch only on first load.** `refreshMyShips()` is only called when the store is empty.

---

## Scalability: Adding New Domains

To add a new domain (e.g., Markets):

1. **Create store:** `MarketStateStore : EntityStateStore<String, Market>()` in `sdk/domain/state/`
2. **Add to session:** Add `val marketStateStore = MarketStateStore()` in `SpaceTradersSession`
3. **Create/enhance repository:** `MarketRepositoryImpl` writes through to `MarketStateStore` and registers timers if applicable
4. **Hilt provider:** Add `@Provides fun provideMarketStateStore(sm: SessionManager)` in `SdkModule`
5. **ViewModel observes:** Collect from `marketStateStore.observe(waypointSymbol)`

No existing code needs modification beyond `SpaceTradersSession` (adding the new store field) and `SdkModule` (adding the Hilt provider).

---

## Files to Create

| File | Module | Purpose |
|---|---|---|
| `sdk/domain/state/EntityStateStore.kt` | SDK commonMain | Generic reactive store base |
| `sdk/domain/state/FleetStateStore.kt` | SDK commonMain | Ship state store |
| `sdk/domain/state/AgentStateStore.kt` | SDK commonMain | Agent state store |
| `sdk/domain/state/ContractStateStore.kt` | SDK commonMain | Contract state store |
| `sdk/domain/scheduler/RefreshScheduler.kt` | SDK commonMain | Centralized timer engine |
| `sdk/domain/scheduler/ScheduledRefresh.kt` | SDK commonMain | Timer entry data class |
| `sdk/domain/session/SpaceTradersSession.kt` | SDK commonMain | Auth-scoped session container |
| `sdk/domain/session/SessionManager.kt` | SDK commonMain | Session lifecycle manager |

## Files to Modify

| File | Change |
|---|---|
| `FleetRepositoryImpl.kt` | Add write-through to FleetStateStore + timer registration |
| `FleetRepository.kt` (interface) | Add `refreshMyShips()` method |
| `SdkModule.kt` | Add providers for SessionManager, stores, scheduler |
| `ShipListViewModel.kt` | Observe FleetStateStore instead of direct API fetch |
| `ShipDetailViewModel.kt` | Observe FleetStateStore.observe(symbol) instead of direct API fetch |
| `DashboardViewModel.kt` | Observe AgentStateStore instead of direct API fetch |
| `ShipListScreen.kt` | Remove API-triggering timer logic, keep display-only countdown |
| `ShipDetailScreen.kt` | Remove API-triggering timer logic, keep display-only countdown |
| `AuthViewModel.kt` | Use SessionManager.login() instead of direct TokenRepository |
| `Application.kt` or equivalent | Register AppLifecycleObserver + call restoreIfAuthenticated() |

## Verification Plan

1. **Unit tests:** Test EntityStateStore operations (put, update, observe, clear). Test RefreshScheduler with TestScope + advanceTimeBy(). Test FleetRepositoryImpl write-through behavior with MockEngine.
2. **Integration test:** Login → fetch ships → verify store populated → simulate transit timer expiry → verify store updated → verify ShipListScreen recomposes.
3. **Manual test:** On emulator, navigate a ship, watch countdown on detail screen, navigate to list screen, verify list also shows countdown and updates when transit completes.
4. **Logout test:** Login → populate stores → logout → verify stores cleared and timers cancelled → login as different agent → verify clean state.
