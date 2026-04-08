# System & Waypoint Exploration — Design Spec

**Date:** 2026-04-08
**Status:** Approved
**Scope:** SDK data layer for system waypoints, navigation use case, and a new SystemMapScreen with sorting, filtering, and ship navigation.

---

## Overview

Add the ability for players to explore waypoints within a star system, view them in a hierarchical list (planets with orbiting moons), sort and filter by type/traits/distance, and command a ship to navigate to a selected waypoint. The feature is accessible from the Ship Detail screen via a "View System" button.

### Design Decisions

- **Fuel estimation:** The API provides no pre-flight fuel cost endpoint. The UI displays Euclidean distance and current fuel level; the user decides. If navigation fails due to insufficient fuel, the API's `InsufficientFuelForTravel` error is surfaced.
- **Architecture:** `WaypointStateStore` in `SpaceTradersSession` (consistent with `FleetStateStore`/`AgentStateStore` pattern). Waypoints cached per session, cleaned up on logout.
- **No use case for waypoint fetching:** `SystemRepository.getSystemWaypoints()` is a direct fetch-and-cache with no business logic — wrapping it in a use case would be pure indirection.

---

## 1. Domain Models

### 1.1 New Models (`sdk/domain/model/`)

**`Waypoint.kt`**

```kotlin
data class Waypoint(
    val symbol: String,
    val type: WaypointType,
    val systemSymbol: String,
    val x: Int,
    val y: Int,
    val orbits: String?,
    val orbitals: List<String>,
    val traits: List<WaypointTrait>,
    val isUnderConstruction: Boolean
)
```

- `orbits`: parent waypoint symbol, `null` for top-level bodies.
- `orbitals`: list of child waypoint symbols (flattened from `WaypointOrbital` DTO which only contains a symbol).

**`WaypointTrait.kt`**

```kotlin
data class WaypointTrait(
    val symbol: WaypointTraitSymbol,
    val name: String,
    val description: String
)
```

**`NavigateResult.kt`**

```kotlin
data class NavigateResult(
    val nav: ShipNav,
    val fuel: ShipFuel
)
```

**`Distance.kt`**

```kotlin
fun euclideanDistance(x1: Int, y1: Int, x2: Int, y2: Int): Double =
    sqrt(((x2 - x1).toDouble().pow(2) + (y2 - y1).toDouble().pow(2)))
```

Euclidean distance is a core game concept used in sorting and UI display. Lives in the domain layer, not a util package.

### 1.2 New Enum (`sdk/domain/model/enums/`)

**`WaypointTraitSymbol.kt`**

All 69 values from the API spec:

```kotlin
enum class WaypointTraitSymbol {
    UNCHARTED, UNDER_CONSTRUCTION, MARKETPLACE, SHIPYARD, OUTPOST,
    SCATTERED_SETTLEMENTS, SPRAWLING_CITIES, MEGA_STRUCTURES, PIRATE_BASE,
    OVERCROWDED, HIGH_TECH, CORRUPT, BUREAUCRATIC, TRADING_HUB, INDUSTRIAL,
    BLACK_MARKET, RESEARCH_FACILITY, MILITARY_BASE, SURVEILLANCE_OUTPOST,
    EXPLORATION_OUTPOST, MINERAL_DEPOSITS, COMMON_METAL_DEPOSITS,
    PRECIOUS_METAL_DEPOSITS, RARE_METAL_DEPOSITS, METHANE_POOLS, ICE_CRYSTALS,
    EXPLOSIVE_GASES, STRONG_MAGNETOSPHERE, VIBRANT_AURORAS, SALT_FLATS, CANYONS,
    PERPETUAL_DAYLIGHT, PERPETUAL_OVERCAST, DRY_SEABEDS, MAGMA_SEAS,
    SUPERVOLCANOES, ASH_CLOUDS, VAST_RUINS, MUTATED_FLORA, TERRAFORMED,
    EXTREME_TEMPERATURES, EXTREME_PRESSURE, DIVERSE_LIFE, SCARCE_LIFE, FOSSILS,
    WEAK_GRAVITY, STRONG_GRAVITY, CRUSHING_GRAVITY, TOXIC_ATMOSPHERE,
    CORROSIVE_ATMOSPHERE, BREATHABLE_ATMOSPHERE, THIN_ATMOSPHERE, JOVIAN, ROCKY,
    VOLCANIC, FROZEN, SWAMP, BARREN, TEMPERATE, JUNGLE, OCEAN, RADIOACTIVE,
    MICRO_GRAVITY_ANOMALIES, DEBRIS_CLUSTER, DEEP_CRATERS, SHALLOW_CRATERS,
    UNSTABLE_COMPOSITION, HOLLOWED_INTERIOR, STRIPPED;

    companion object {
        fun fromString(value: String): WaypointTraitSymbol =
            entries.firstOrNull { it.name == value } ?: UNCHARTED
    }
}
```

---

## 2. API Layer

### 2.1 New DTOs (`sdk/api/dto/`)

**`WaypointDto.kt`**

```kotlin
@Serializable
data class WaypointDto(
    val symbol: String,
    val type: String,
    val systemSymbol: String,
    val x: Int,
    val y: Int,
    val orbits: String? = null,
    val orbitals: List<WaypointOrbitalDto> = emptyList(),
    val traits: List<WaypointTraitDto> = emptyList(),
    val isUnderConstruction: Boolean = false
)

@Serializable
data class WaypointOrbitalDto(val symbol: String)

@Serializable
data class WaypointTraitDto(
    val symbol: String,
    val name: String,
    val description: String
)
```

DTO defaults handle optional API fields. `ignoreUnknownKeys = true` on the JSON config skips `chart`, `modifiers`, `faction` which we don't need yet.

**`NavigateResponseDto.kt`**

```kotlin
@Serializable
data class NavigateResponseDto(
    val nav: ShipNavDto,
    val fuel: ShipFuelDto
)

@Serializable
data class NavigateRequestDto(val waypointSymbol: String)
```

### 2.2 New Endpoint (`sdk/api/endpoints/`)

**`SystemsApi.kt`**

```kotlin
interface SystemsApi {
    suspend fun getSystemWaypoints(
        systemSymbol: String,
        page: Int = 1,
        limit: Int = 20
    ): PaginatedResponse<WaypointDto>
}

class SystemsApiImpl(private val client: SpaceTradersClient) : SystemsApi {
    override suspend fun getSystemWaypoints(
        systemSymbol: String, page: Int, limit: Int
    ): PaginatedResponse<WaypointDto> =
        client.authenticated.get("systems/$systemSymbol/waypoints") {
            parameter("page", page)
            parameter("limit", limit)
        }.body()
}
```

Uses `client.authenticated` for consistency with the rest of the codebase.

### 2.3 Modified Endpoint (`FleetApi`)

Add to the existing `FleetApi` interface and `FleetApiImpl`:

```kotlin
// Interface
suspend fun navigateShip(shipSymbol: String, waypointSymbol: String): NavigateResponseDto

// Implementation
override suspend fun navigateShip(shipSymbol: String, waypointSymbol: String): NavigateResponseDto =
    client.authenticated.post("my/ships/$shipSymbol/navigate") {
        setBody(NavigateRequestDto(waypointSymbol))
    }.body<ApiResponse<NavigateResponseDto>>().data
```

Navigate is a fleet action (`/my/ships/` path), not a systems query — it belongs on `FleetApi`.

### 2.4 New Mappers (`sdk/api/mapper/`)

**`WaypointMapper.kt`**

```kotlin
fun WaypointDto.toDomain(): Waypoint = Waypoint(
    symbol = symbol,
    type = WaypointType.fromString(type),
    systemSymbol = systemSymbol,
    x = x, y = y,
    orbits = orbits,
    orbitals = orbitals.map { it.symbol },
    traits = traits.map { it.toDomain() },
    isUnderConstruction = isUnderConstruction
)

fun WaypointTraitDto.toDomain(): WaypointTrait = WaypointTrait(
    symbol = WaypointTraitSymbol.fromString(symbol),
    name = name,
    description = description
)
```

**`NavigateMapper.kt`**

```kotlin
fun NavigateResponseDto.toDomain(): NavigateResult = NavigateResult(
    nav = nav.toDomain(),
    fuel = fuel.toDomain()
)
```

Reuses existing `ShipNavDto.toDomain()` and `ShipFuelDto.toDomain()` from `ShipMapper.kt`.

---

## 3. State Store + Repository

### 3.1 WaypointStateStore (`sdk/domain/state/`)

```kotlin
class WaypointStateStore : EntityStateStoreImpl<String, Waypoint>()
```

Keyed by waypoint symbol. Session-scoped — added to `SpaceTradersSession` interface and `SpaceTradersSessionImpl`.

### 3.2 Session Changes

```kotlin
// SpaceTradersSession.kt — add property:
val waypointStateStore: WaypointStateStore

// SpaceTradersSessionImpl.kt — add instantiation:
override val waypointStateStore = WaypointStateStore()
```

### 3.3 SystemRepository

**Interface** (`sdk/domain/repository/SystemRepository.kt`):

```kotlin
interface SystemRepository {
    suspend fun getSystemWaypoints(systemSymbol: String): List<Waypoint>
}
```

**Implementation** (`sdk/data/repository/SystemRepositoryImpl.kt`):

```kotlin
class SystemRepositoryImpl(
    private val systemsApi: SystemsApi,
    private val waypointStateStore: WaypointStateStore
) : SystemRepository {

    override suspend fun getSystemWaypoints(systemSymbol: String): List<Waypoint> {
        val allWaypoints = fetchAllPages(systemSymbol)
        waypointStateStore.putAll(allWaypoints.associateBy { it.symbol })
        return allWaypoints
    }

    private suspend fun fetchAllPages(systemSymbol: String): List<Waypoint> {
        val result = mutableListOf<Waypoint>()
        var page = 1
        do {
            val response = systemsApi.getSystemWaypoints(systemSymbol, page = page, limit = 20)
            result.addAll(response.data.map { it.toDomain() })
            val total = response.meta.total
            page++
        } while (result.size < total)
        return result
    }
}
```

The `fetchAllPages` loop exhausts all pages before returning. Systems typically have 10-60 waypoints — bounded and safe to fetch fully.

---

## 4. Use Cases

### 4.1 NavigateShipUseCase (`sdk/domain/usecase/`)

```kotlin
interface NavigateShipUseCase {
    suspend operator fun invoke(shipSymbol: String, waypointSymbol: String): NavigateResult
}

class NavigateShipUseCaseImpl(
    private val fleetApi: FleetApi,
    private val fleetStateStore: FleetStateStore,
    private val orbitShipUseCase: OrbitShipUseCase
) : NavigateShipUseCase {

    override suspend operator fun invoke(
        shipSymbol: String,
        waypointSymbol: String
    ): NavigateResult {
        // Pre-flight: auto-orbit if docked
        val ship = fleetStateStore.entities.value[shipSymbol]
        if (ship?.nav?.status == ShipNavStatus.DOCKED) {
            orbitShipUseCase(shipSymbol)
        }

        val response = fleetApi.navigateShip(shipSymbol, waypointSymbol).toDomain()

        // Update ship state with new nav + fuel
        fleetStateStore.update(shipSymbol) { s ->
            s.copy(nav = response.nav, fuel = response.fuel)
        }

        return response
    }
}
```

Earns its existence with real business logic:
1. Reads ship state to decide auto-orbit
2. Delegates to `OrbitShipUseCase` (reuses existing logic + state update)
3. Updates fleet store with post-navigation state

Depends on `OrbitShipUseCase` interface — testable with a fake.

---

## 5. DI Wiring (`SdkModule.kt`)

New providers:

```kotlin
@Provides @Singleton
fun provideSystemsApi(client: SpaceTradersClient): SystemsApi =
    SystemsApiImpl(client)

@Provides
fun provideWaypointStateStore(sm: SessionManager): WaypointStateStore =
    sm.requireSession().waypointStateStore

@Provides
fun provideSystemRepository(
    systemsApi: SystemsApi,
    waypointStateStore: WaypointStateStore
): SystemRepository = SystemRepositoryImpl(systemsApi, waypointStateStore)

@Provides
fun provideNavigateShipUseCase(
    fleetApi: FleetApi,
    fleetStateStore: FleetStateStore,
    orbitShipUseCase: OrbitShipUseCase
): NavigateShipUseCase = NavigateShipUseCaseImpl(fleetApi, fleetStateStore, orbitShipUseCase)
```

`SystemsApi` is singleton (stateless HTTP wrapper). `WaypointStateStore` and `SystemRepository` are session-scoped (unscoped providers delegate to `SessionManager`).

---

## 6. Navigation

### 6.1 Routes (`Routes.kt`)

```kotlin
@Serializable
data class SystemMapRoute(
    val systemSymbol: String,
    val focusWaypointSymbol: String? = null,
    val shipSymbol: String? = null
)
```

- `focusWaypointSymbol`: highlights the ship's current waypoint in the list.
- `shipSymbol`: enables navigation actions. Optional so the screen could be opened without a ship context in the future.

### 6.2 NavigationTarget (`NavigationTarget.kt`)

```kotlin
data class SystemMap(
    val systemSymbol: String,
    val focusWaypointSymbol: String? = null,
    val shipSymbol: String? = null
) : NavigationTarget
```

### 6.3 NavHost (`SpaceTradersNavHost.kt`)

```kotlin
// New destination:
composable<SystemMapRoute> {
    SystemMapScreen(onNavigateBack = { navController.popBackStack() })
}

// Updated ShipDetailScreen wiring:
composable<ShipDetailRoute> {
    ShipDetailScreen(
        onNavigateToSystemMap = { systemSymbol, waypointSymbol, shipSymbol ->
            navController.navigate(
                SystemMapRoute(systemSymbol, waypointSymbol, shipSymbol)
            )
        }
    )
}
```

### 6.4 ShipDetailScreen Changes

Add `onNavigateToSystemMap` callback parameter. Add `ViewSystemClicked` event to `ShipDetailEvent`. Add "View System" `TerminalButton` in the Navigation card after the SYSTEM/LOCATION rows. The button passes the ship's `systemSymbol`, `waypointSymbol`, and `shipSymbol` to the callback.

---

## 7. SystemMapViewModel

### 7.1 UI State (`ui/systemmap/SystemMapUiState.kt`)

```kotlin
data class SystemMapUiState(
    val systemSymbol: String = "",
    val waypoints: List<WaypointNode> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val sortMode: SortMode = SortMode.NAME,
    val distanceOrigin: DistanceOrigin = DistanceOrigin.SYSTEM_CENTER,
    val activeTypeFilters: Set<WaypointType> = emptySet(),
    val activeTraitFilters: Set<WaypointTraitSymbol> = emptySet(),
    val selectedShip: ShipSnapshot? = null,
    val focusWaypointSymbol: String? = null,
    val isActionInProgress: Boolean = false,
    val actionResult: SystemMapActionResult? = null
)

data class WaypointNode(
    val waypoint: WaypointSummary,
    val distance: Double,
    val orbitals: List<WaypointNode>
)

data class WaypointSummary(
    val symbol: String,
    val type: WaypointType,
    val x: Int,
    val y: Int,
    val traits: List<WaypointTraitSymbol>,
    val hasMarketplace: Boolean,
    val hasShipyard: Boolean,
    val isUncharted: Boolean,
    val isUnderConstruction: Boolean
)

data class ShipSnapshot(
    val symbol: String,
    val waypointSymbol: String,
    val x: Int,
    val y: Int,
    val fuelCurrent: Int,
    val fuelCapacity: Int,
    val navStatus: ShipNavStatus
)

enum class SortMode { NAME, DISTANCE }
enum class DistanceOrigin { SYSTEM_CENTER, SHIP_LOCATION }

sealed interface SystemMapActionResult {
    data class NavigationStarted(
        val destinationSymbol: String,
        val fuelConsumed: Int,
        val fuelRemaining: Int
    ) : SystemMapActionResult
}

sealed interface SystemMapEvent {
    data object RetryClicked : SystemMapEvent
    data class SortModeSelected(val mode: SortMode) : SystemMapEvent
    data class DistanceOriginSelected(val origin: DistanceOrigin) : SystemMapEvent
    data class TypeFilterToggled(val type: WaypointType) : SystemMapEvent
    data class TraitFilterToggled(val trait: WaypointTraitSymbol) : SystemMapEvent
    data class NavigateToWaypoint(val waypointSymbol: String) : SystemMapEvent
    data object ActionResultDismissed : SystemMapEvent
}
```

### 7.2 ViewModel (`ui/systemmap/SystemMapViewModel.kt`)

```kotlin
@HiltViewModel
class SystemMapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val systemRepository: SystemRepository,
    private val fleetStateStore: FleetStateStore,
    private val waypointStateStore: WaypointStateStore,
    private val navigateShipUseCase: NavigateShipUseCase
) : ViewModel()
```

**State production:**

```kotlin
val uiState: StateFlow<SystemMapUiState> = combine(
    waypointStateStore.entities,
    fleetStateStore.entities,
    _localState
) { waypointMap, fleetMap, local -> /* ... */ }
    .stateIn(viewModelScope, SharingStarted.Eagerly, SystemMapUiState())
```

The triple-combine reacts to: waypoint data loaded, ship state changes (post-navigate fuel/nav update), and local UI state (sort/filter toggles).

**Key internal functions:**

- **`buildTree(waypoints, originX, originY)`**: Groups by `orbits` field. `orbits == null` → root node. Others nest under their parent. Each node gets `distance` from `euclideanDistance()`. If a filter hides a parent but not its moon, the moon promotes to a root node.
- **`applyFilters(typeFilters, traitFilters)`**: Empty set = show all. Non-empty = intersection (waypoint must match at least one active type AND at least one active trait if both are set).
- **`applySort(sortMode)`**: `NAME` → alphabetical by symbol. `DISTANCE` → ascending distance. Orbitals within a parent always sort by name.

**`LocalState`:**

```kotlin
private data class LocalState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val sortMode: SortMode = SortMode.NAME,
    val distanceOrigin: DistanceOrigin = DistanceOrigin.SYSTEM_CENTER,
    val activeTypeFilters: Set<WaypointType> = emptySet(),
    val activeTraitFilters: Set<WaypointTraitSymbol> = emptySet(),
    val isActionInProgress: Boolean = false,
    val actionResult: SystemMapActionResult? = null
)
```

---

## 8. SystemMapScreen Composable Hierarchy

```
SystemMapScreen (stateful — hiltViewModel + collectAsStateWithLifecycle)
└── SystemMapScreenContent (stateless — uiState + onEvent)
    ├── Header: system symbol + ship location badge
    ├── SortBar
    │   ├── SortMode toggle (NAME | DISTANCE)
    │   └── DistanceOrigin toggle (SYSTEM CENTER | FROM SHIP) — visible only when sort=DISTANCE
    ├── FilterChipRow (horizontal scrollable Row)
    │   ├── Type chips: PLANET, GAS_GIANT, ASTEROID_FIELD, JUMP_GATE
    │   └── Trait chips: MARKETPLACE, SHIPYARD, UNCHARTED
    ├── WaypointList (LazyColumn)
    │   └── WaypointRow (per WaypointNode)
    │       ├── WaypointType indicator
    │       ├── Symbol + type label
    │       ├── Distance display ("123.4 AU")
    │       ├── Trait badges (MARKETPLACE, SHIPYARD chips)
    │       ├── "YOU ARE HERE" indicator (when symbol == focusWaypointSymbol)
    │       ├── "Navigate" button (when shipSymbol present, not current location, not in transit)
    │       └── OrbitalList (indented children, indentLevel * 24.dp)
    │           └── WaypointRow (recursive, capped at indentLevel=1)
    ├── ActionResult card (conditional — amber/tertiary, after successful navigation)
    │   ├── "> NAVIGATION INITIATED"
    │   ├── Destination, fuel consumed, fuel remaining
    │   └── Dismiss button
    ├── Error card (conditional)
    └── ScanlineOverlay
```

**Filter chips:** Only the most gameplay-relevant types and traits are surfaced (not all 14 types or 69 traits):
- Types: PLANET, GAS_GIANT, ASTEROID_FIELD, JUMP_GATE
- Traits: MARKETPLACE, SHIPYARD, UNCHARTED

Active filters use primary (green) fill; inactive use outline style. Built with existing `TerminalButton`.

**Visual language:** Green-on-black, `TerminalCard`, `TerminalButton`, monospace, uppercase headers — matching the existing retro terminal aesthetic.

---

## 9. Testing Strategy

### 9.1 SDK Tests (`spacetradersiosdk/src/androidHostTest/`)

| Test Class | Coverage |
|---|---|
| `WaypointMapperTest` | `WaypointDto.toDomain()`, `WaypointTraitDto.toDomain()` — all fields, null orbits, empty orbitals/traits |
| `NavigateMapperTest` | `NavigateResponseDto.toDomain()` — nav + fuel fields |
| `WaypointTraitSymbolTest` | `fromString()` — known values, unknown fallback to `UNCHARTED` |
| `DistanceTest` | `euclideanDistance` — zero distance, integer coords, 3-4-5 triangle, negative coords |
| `SystemRepositoryImplTest` | Single-page fetch, multi-page pagination (mock 3 pages, total=45), state store population |
| `NavigateShipUseCaseTest` | Ship in orbit (navigate directly), ship docked (auto-orbit then navigate), fleet store updated, orbit skipped when already in orbit |

**SDK test patterns:**
- `MockEngine` with `MockRequestHandleScope.okJson()` helper.
- Pagination test: mock returns `meta.total = 45, limit = 20` → repository makes 3 requests.
- `FakeOrbitShipUseCase` for `NavigateShipUseCaseTest`.

### 9.2 App Tests (`app/src/test/`)

| Test Class | Coverage |
|---|---|
| `SystemMapViewModelTest` | Loading state, waypoints displayed after load, sort by name, sort by distance, distance origin toggle (system center vs ship), type filter toggle, trait filter toggle, tree hierarchy (moons nest under planets), navigate success, navigate error, auto-orbit for docked ship, action result display + dismiss, focus waypoint passthrough, moon promotes to root when parent filtered out |

**App test patterns:**
- `FakeSystemRepository`, `FakeNavigateShipUseCase` — hand-written, implement interfaces.
- Real `EntityStateStoreImpl` for state stores (pure in-memory, no mocking needed).
- `StandardTestDispatcher` + `advanceUntilIdle()`.

---

## 10. File Inventory

### New Files (18)

| Layer | File |
|---|---|
| Domain model | `Waypoint.kt`, `WaypointTrait.kt`, `NavigateResult.kt`, `Distance.kt` |
| Domain enum | `WaypointTraitSymbol.kt` |
| Domain state | `WaypointStateStore.kt` |
| Domain repository | `SystemRepository.kt` |
| Domain use case | `NavigateShipUseCase.kt` |
| API DTO | `WaypointDto.kt`, `NavigateResponseDto.kt` |
| API endpoint | `SystemsApi.kt` |
| API mapper | `WaypointMapper.kt`, `NavigateMapper.kt` |
| Data repository | `SystemRepositoryImpl.kt` |
| App UI | `SystemMapUiState.kt`, `SystemMapScreen.kt`, `SystemMapViewModel.kt` |

### Modified Files (7)

| File | Change |
|---|---|
| `FleetApi.kt` / `FleetApiImpl` | Add `navigateShip()` |
| `SpaceTradersSession.kt` | Add `waypointStateStore` property |
| `SpaceTradersSessionImpl.kt` | Instantiate `WaypointStateStore` |
| `SdkModule.kt` | Add providers for SystemsApi, WaypointStateStore, SystemRepository, NavigateShipUseCase |
| `Routes.kt` | Add `SystemMapRoute` |
| `NavigationTarget.kt` | Add `SystemMap` |
| `SpaceTradersNavHost.kt` | Add `SystemMapRoute` composable, update `ShipDetailScreen` wiring |
| `ShipDetailScreen.kt` | Add "View System" button, accept `onNavigateToSystemMap` callback |
| `ShipDetailUiState.kt` | Add `ViewSystemClicked` event |
