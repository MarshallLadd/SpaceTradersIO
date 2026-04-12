# SQLDelight Offline-First Cache Design

**Date:** 2026-04-11
**Status:** Approved
**Scope:** Ship (fleet) + Agent as proof-of-concept. Contract and Waypoint migrated in follow-up work.

## 1. Architecture & Strategy Overview

### The Core Shift

Today, `EntityStateStore` (in-memory `MutableStateFlow<Map>`) is the source of truth for all entities. ViewModels observe state stores directly, and repositories write to them after network fetches. Data dies on process death with no offline story.

SQLDelight replaces in-memory state stores as the single source of truth. The database owns the data, the UI observes the database via `Flow`, and the network is a mechanism to keep the database fresh.

### Data Flow

```
Network API --> Repository --> SQLDelight DB --> Flow<T> --> ViewModel --> UI
                                    ^                |
                                    |                |
                              (writes here)    (observes here)
```

### Two Operation Modes

**Read (Cache-Then-Network):**
- Repository exposes `observeX(): Flow<T>` backed by SQLDelight's `asFlow().mapToOneOrNull()` / `asFlow().mapToList()`.
- ViewModel collects this Flow immediately, showing cached data if present.
- ViewModel calls `suspend fun refreshX()` on the repository, which fetches from the network and writes to the DB.
- The DB write automatically pushes new data through the Flow.
- If refresh fails (offline), cached data stays visible. Error is logged and optionally surfaced to the UI.

**Write/Action (Network-First):**
- Use cases (dock, orbit, refuel) call the API first.
- On success, they write the updated entity fields to SQLDelight via targeted repository update methods.
- On failure, the exception bubbles up to the ViewModel for UI error display.
- The DB is never optimistically updated.

### What Gets Deleted

- `FleetStateStore` (replaced by SQLDelight queries)
- `AgentStateStore` (replaced by SQLDelight queries)

### What Stays

- `EntityStateStore` interface + `EntityStateStoreImpl` base class (still used by `ContractStateStore`, `WaypointStateStore`)
- All contract and waypoint repositories, use cases, and ViewModels (unchanged)

### Database Lifecycle

- Singleton `SqlDriver` created at app startup via platform-specific `SqlDriverFactory`.
- All tables cleared on logout (`SessionManager.destroy()` calls `DELETE FROM` on every table).
- Data does not persist across logout/login cycles.

## 2. SQLDelight Gradle & Driver Setup

### Version Catalog (`libs.versions.toml`)

```toml
[versions]
sqldelight = "2.0.2"

[libraries]
sqldelight-coroutines = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
sqldelight-android-driver = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-native-driver = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }
sqldelight-jvm-driver = { module = "app.cash.sqldelight:sqlite-driver", version.ref = "sqldelight" }

[plugins]
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
```

### SDK `build.gradle.kts`

- Apply the `sqldelight` plugin.
- Add `sqldelight {}` block:
  - Database name: `SpaceTradersDatabase`
  - Package: `com.brokenhuskysledteam.spacetradersio.sdk.data.db`
  - Dialect: `app.cash.sqldelight:sqlite-3-38-dialect:2.0.2`
- Wire dependencies per source set:
  - `commonMain` -> `sqldelight-coroutines`
  - `androidMain` -> `sqldelight-android-driver`
  - `iosMain` -> `sqldelight-native-driver`
  - `commonTest` (androidHostTest) -> `sqldelight-jvm-driver` (in-memory `JdbcSqliteDriver`)

### Platform `SqlDriverFactory` (`expect`/`actual`)

```kotlin
// commonMain
expect class SqlDriverFactory {
    fun create(): SqlDriver
}

// androidMain - AndroidSqliteDriver(schema, context, "spacetraders.db")
// iosMain - NativeSqliteDriver(schema, "spacetraders.db")
```

Tests bypass `SqlDriverFactory` entirely. Test code directly creates `JdbcSqliteDriver(IN_MEMORY)` and passes it to the database constructor.

`SqlDriverFactory` on Android requires a `Context`. The `:app` module's Hilt DI constructs the factory with `@ApplicationContext` and provides it to the SDK. The SDK never references Android `Context` directly.

## 3. Database Schema

Schema files live at: `spacetradersiosdk/src/commonMain/sqldelight/com/brokenhuskysledteam/spacetradersio/sdk/data/db/`

### Design Decisions

- **Flat single-table per entity.** Sub-objects (`ShipNav`, `ShipCargo`, etc.) are flattened into columns. These are value types tightly bound to their parent, not independent entities. Normalization adds JOIN complexity without benefit.
- **Enums stored as `TEXT`** using their `.name`. Mapped via `enumValueOf<T>()` in Kotlin.
- **`Instant` fields stored as ISO-8601 `TEXT`.** Parsed back to `kotlin.time.Instant` in the Kotlin mapper.
- **`INSERT OR REPLACE` (upsert)** is the write strategy. Natural primary keys (ship symbol, agent symbol) make this safe.

### `Agent.sq`

```sql
CREATE TABLE agent (
    symbol TEXT NOT NULL PRIMARY KEY,
    account_id TEXT,
    headquarters TEXT NOT NULL,
    credits INTEGER NOT NULL,
    starting_faction TEXT NOT NULL,
    ship_count INTEGER NOT NULL
);

selectAgent:
SELECT * FROM agent LIMIT 1;

upsertAgent:
INSERT OR REPLACE INTO agent (symbol, account_id, headquarters, credits, starting_faction, ship_count)
VALUES (?, ?, ?, ?, ?, ?);

updateCredits:
UPDATE agent SET credits = ? WHERE symbol = ?;

deleteAll:
DELETE FROM agent;
```

### `Ship.sq`

```sql
CREATE TABLE ship (
    symbol TEXT NOT NULL PRIMARY KEY,
    -- registration
    reg_role TEXT NOT NULL,
    reg_faction_symbol TEXT NOT NULL,
    -- nav
    nav_system_symbol TEXT NOT NULL,
    nav_waypoint_symbol TEXT NOT NULL,
    nav_status TEXT NOT NULL,
    nav_flight_mode TEXT NOT NULL,
    -- route
    route_origin_symbol TEXT NOT NULL,
    route_origin_type TEXT NOT NULL,
    route_origin_system TEXT NOT NULL,
    route_origin_x INTEGER NOT NULL,
    route_origin_y INTEGER NOT NULL,
    route_dest_symbol TEXT NOT NULL,
    route_dest_type TEXT NOT NULL,
    route_dest_system TEXT NOT NULL,
    route_dest_x INTEGER NOT NULL,
    route_dest_y INTEGER NOT NULL,
    route_departure TEXT NOT NULL,
    route_arrival TEXT NOT NULL,
    -- cargo
    cargo_units INTEGER NOT NULL,
    cargo_capacity INTEGER NOT NULL,
    -- fuel
    fuel_current INTEGER NOT NULL,
    fuel_capacity INTEGER NOT NULL,
    -- frame
    frame_name TEXT NOT NULL,
    -- cooldown
    cooldown_total_seconds INTEGER NOT NULL,
    cooldown_remaining_seconds INTEGER NOT NULL,
    cooldown_expiration TEXT
);

selectAllShips:
SELECT * FROM ship;

selectShipBySymbol:
SELECT * FROM ship WHERE symbol = ?;

upsertShip:
INSERT OR REPLACE INTO ship (
    symbol, reg_role, reg_faction_symbol,
    nav_system_symbol, nav_waypoint_symbol, nav_status, nav_flight_mode,
    route_origin_symbol, route_origin_type, route_origin_system, route_origin_x, route_origin_y,
    route_dest_symbol, route_dest_type, route_dest_system, route_dest_x, route_dest_y,
    route_departure, route_arrival,
    cargo_units, cargo_capacity,
    fuel_current, fuel_capacity,
    frame_name,
    cooldown_total_seconds, cooldown_remaining_seconds, cooldown_expiration
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

updateShipNav:
UPDATE ship SET
    nav_system_symbol = ?,
    nav_waypoint_symbol = ?,
    nav_status = ?,
    nav_flight_mode = ?,
    route_origin_symbol = ?,
    route_origin_type = ?,
    route_origin_system = ?,
    route_origin_x = ?,
    route_origin_y = ?,
    route_dest_symbol = ?,
    route_dest_type = ?,
    route_dest_system = ?,
    route_dest_x = ?,
    route_dest_y = ?,
    route_departure = ?,
    route_arrival = ?
WHERE symbol = ?;

updateShipFuel:
UPDATE ship SET fuel_current = ?, fuel_capacity = ? WHERE symbol = ?;

updateShipCargo:
UPDATE ship SET cargo_units = ?, cargo_capacity = ? WHERE symbol = ?;

updateShipCooldown:
UPDATE ship SET cooldown_total_seconds = ?, cooldown_remaining_seconds = ?, cooldown_expiration = ? WHERE symbol = ?;

deleteAllShips:
DELETE FROM ship;
```

## 4. Repository Refactor

### New `FleetRepository` Interface

```kotlin
interface FleetRepository {
    // Observe patterns (Cache-Then-Network)
    fun observeShips(): Flow<List<Ship>>
    fun observeShip(shipSymbol: String): Flow<Ship?>

    // Network refresh triggers
    suspend fun refreshMyShips(page: Int = 1, limit: Int = 20)
    suspend fun refreshMyShip(shipSymbol: String)

    // Targeted DB updates (used by use cases after API success)
    suspend fun updateShipNav(shipSymbol: String, nav: ShipNav)
    suspend fun updateShipFuel(shipSymbol: String, fuel: ShipFuel)
    suspend fun updateShipCargo(shipSymbol: String, cargo: ShipCargo)
    suspend fun updateShipCooldown(shipSymbol: String, cooldown: Cooldown)
    suspend fun saveShip(ship: Ship)

    // Logout wipe
    suspend fun clearAll()
}
```

### `FleetRepositoryImpl` (Cache-Then-Network Read)

```kotlin
class FleetRepositoryImpl(
    private val fleetApi: FleetApi,
    private val database: SpaceTradersDatabase,
    private val refreshScheduler: RefreshScheduler
) : FleetRepository {

    private val queries get() = database.shipQueries

    override fun observeShips(): Flow<List<Ship>> =
        queries.selectAllShips()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeShip(shipSymbol: String): Flow<Ship?> =
        queries.selectShipBySymbol(shipSymbol)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.toDomain() }

    override suspend fun refreshMyShips(page: Int, limit: Int) {
        val ships = fleetApi.getMyShips(page, limit).data.map { it.toDomain() }
        database.transaction {
            ships.forEach { ship -> queries.upsertShip(/* flatten ship fields */) }
        }
        ships.forEach { registerTimersForShip(it) }
    }

    override suspend fun refreshMyShip(shipSymbol: String) {
        val ship = fleetApi.getMyShip(shipSymbol).toDomain()
        queries.upsertShip(/* flatten ship fields */)
        registerTimersForShip(ship)
    }

    override suspend fun updateShipNav(shipSymbol: String, nav: ShipNav) {
        queries.updateShipNav(/* flatten nav fields */, symbol = shipSymbol)
    }

    // ... updateShipFuel, updateShipCargo, updateShipCooldown follow same pattern

    override suspend fun clearAll() { queries.deleteAllShips() }
}
```

### New `AgentRepository` Interface

```kotlin
interface AgentRepository {
    fun observeAgent(): Flow<Agent?>
    suspend fun refreshAgent()
    suspend fun saveAgent(agent: Agent)
    suspend fun updateCredits(symbol: String, credits: Long)
    suspend fun clearAll()
}
```

### `AgentRepositoryImpl`

```kotlin
class AgentRepositoryImpl(
    private val agentsApi: AgentsApi,
    private val database: SpaceTradersDatabase
) : AgentRepository {

    private val queries get() = database.agentQueries

    override fun observeAgent(): Flow<Agent?> =
        queries.selectAgent()
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.toDomain() }

    override suspend fun refreshAgent() {
        val agent = agentsApi.getMyAgent().toDomain()
        queries.upsertAgent(/* flatten agent fields */)
    }

    override suspend fun saveAgent(agent: Agent) {
        queries.upsertAgent(/* flatten agent fields */)
    }

    override suspend fun updateCredits(symbol: String, credits: Long) {
        // Agent table has only one row; update credits column
        // This requires an updateCredits query in Agent.sq
        queries.updateCredits(credits, symbol)
    }

    override suspend fun clearAll() { queries.deleteAll() }
}
```

### Use Case Pattern (Network-First Action)

```kotlin
class DockShipUseCaseImpl(
    private val fleetApi: FleetApi,
    private val fleetRepository: FleetRepository
) : DockShipUseCase {
    override suspend fun invoke(shipSymbol: String): ShipNav {
        val nav = fleetApi.dockShip(shipSymbol).toDomain()  // API first, throws on failure
        fleetRepository.updateShipNav(shipSymbol, nav)       // DB write only on success
        return nav
    }
}
```

### ViewModel Consumption

```kotlin
// ShipListViewModel
class ShipListViewModel(
    private val fleetRepository: FleetRepository,
    // ...
) : ViewModel() {

    val uiState: StateFlow<ShipListUiState> = fleetRepository.observeShips()
        .map { ships -> ShipListUiState(ships = ships) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ShipListUiState())

    init {
        viewModelScope.launch {
            try { fleetRepository.refreshMyShips() }
            catch (e: SpaceTradersApiException) { /* surface error */ }
        }
    }
}
```

## 5. Testing Strategy

### Test Driver

All SDK tests (`androidHostTest`) use `JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)`. Each test creates a fresh database instance.

```kotlin
fun createTestDatabase(): SpaceTradersDatabase {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    SpaceTradersDatabase.Schema.create(driver)
    return SpaceTradersDatabase(driver)
}
```

### Coverage by Layer

| Layer | What's Tested | How |
|---|---|---|
| DB Mappers | Round-trip: domain -> upsert -> select -> domain equals original | Direct DB queries with `createTestDatabase()` |
| Repository (reads) | `observeShips()` emits updated list after `refreshMyShips()`; emits cached data when network throws | `MockEngine` for API + real in-memory DB. Turbine for Flow assertions. |
| Repository (actions) | `updateShipNav()` changes only nav columns; `observeShip()` Flow emits updated ship | Direct DB assertions after calling the method |
| Use cases | API success -> repo update called; API failure -> exception propagates, DB unchanged | `MockEngine` for API + real in-memory DB |
| ViewModel | Observes repository Flow, shows cached data, handles refresh errors | Hand-written fake `FleetRepository` returning test `MutableStateFlow`s. No DB or MockEngine needed. |

### Key Change from Current Tests

ViewModel tests switch from fake state stores to fake repository implementations. The fake is simpler: just expose `MutableStateFlow`s for `observeShips()` / `observeShip()`. No `put()`, `update()`, `remove()` methods to implement.

Coverage target: 100% class, method, line, and branch coverage on all new and modified code.

## 6. Migration Impact

### Files Deleted

- `FleetStateStore.kt`
- `AgentStateStore.kt`

### Files Modified

| File | Change |
|---|---|
| `FleetRepository.kt` | New interface with observe, refresh, targeted update, and clearAll methods |
| `FleetRepositoryImpl.kt` | Rewritten: takes `SpaceTradersDatabase` instead of `FleetStateStore` |
| `DockShipUseCaseImpl.kt` | Takes `FleetRepository` instead of `FleetStateStore`, calls `updateShipNav()` |
| `OrbitShipUseCaseImpl.kt` | Same pattern as dock |
| `RefuelShipUseCaseImpl.kt` | Calls `updateShipFuel()` + agent repository for credits |
| `NavigateShipUseCaseImpl.kt` | Calls `saveShip()` with full updated ship |
| `SpaceTradersSession.kt` | Remove `fleetStateStore`/`agentStateStore` properties, add database ref for logout wipe |
| `SpaceTradersSessionImpl.kt` | `destroy()` calls DELETE FROM on all tables |
| `SdkModule.kt` | Provide `SqlDriverFactory`, `SpaceTradersDatabase`, remove state store bindings |
| `ShipListViewModel.kt` | Inject `FleetRepository` instead of `FleetStateStore` |
| `ShipDetailViewModel.kt` | Inject `FleetRepository` instead of `FleetStateStore` |
| `DashboardViewModel.kt` | Inject `AgentRepository` instead of `AgentStateStore` |
| `SystemMapViewModel.kt` | Inject `FleetRepository` instead of `FleetStateStore` |

### Files Untouched

- `EntityStateStore.kt`, `EntityStateStoreImpl.kt` (kept for Contract/Waypoint)
- `ContractStateStore.kt`, `WaypointStateStore.kt`
- All contract-related repositories, use cases, and ViewModels

### Documentation Updates (Final Step)

- `CLAUDE.md` — new dependency, new patterns, new gotchas
- `docs/ARCHITECTURE_REFERENCE_HUMAN.md` — narrative explanation of offline-first pattern
- `docs/ARCHITECTURE_REFERENCE_AI.md` — rules bullets for the new pattern
- `README.md` — reflect SQLDelight as a key dependency and offline-first strategy
