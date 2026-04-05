# Fleet Management — Ship List, Ship Detail, and Ship Actions

**Date:** 2026-04-04
**Status:** Approved

## Purpose

Add fleet management to the SpaceTraders app: view all ships in a summary list, inspect individual ship details, and perform basic ship actions (orbit, dock, refuel). This is the foundation for future navigation features — "get something safely into orbit and back down again."

## Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Entry point | Tappable fleet card on dashboard | Most intuitive; no new nav chrome needed |
| List summary fields | Symbol, frame, status, location, live countdown for in-transit | Enough at a glance without overwhelming |
| Detail fields | Type, role, location, destination, ETA, status, flight mode, fuel, cargo capacity | No modules/mounts/reactor/engine — keep it focused |
| Action presentation | Only show valid actions for current state | Cleaner than disabled buttons |
| Refuel granularity | Always refuel to max | Start simple; partial refuel can come later |
| Action feedback | Command-output style showing response data | Fits terminal aesthetic, gives useful info (cost, balance) |
| Architecture | Repository + use cases for actions, direct repo calls for reads | Matches existing patterns; use cases earn their keep on actions |
| Read-only use cases | Skipped for getMyShips / getMyShip | Pure delegation with no business logic |

## SDK Layer

### New Enums (`domain/model/enums/`)

- **`ShipNavFlightMode`** — DRIFT, STEALTH, CRUISE, BURN
- **`ShipRole`** — FABRICATOR, HARVESTER, HAULER, INTERCEPTOR, EXCAVATOR, TRANSPORT, REPAIR, SURVEYOR, COMMAND, CARRIER, PATROL, SATELLITE, EXPLORER, REFINERY
- **`WaypointType`** — PLANET, GAS_GIANT, MOON, ORBITAL_STATION, JUMP_GATE, ASTEROID_FIELD, ASTEROID, ENGINEERED_ASTEROID, ASTEROID_BASE, NEBULA, DEBRIS_FIELD, GRAVITY_WELL, ARTIFICIAL_GRAVITY_WELL, FUEL_STATION

Existing `ShipNavStatus` (IN_TRANSIT, IN_ORBIT, DOCKED) is reused.

### Expanded Domain Models (`domain/model/`)

**`Ship`** (replace current minimal model):
- `symbol: String`
- `registration: ShipRegistration` (role, factionSymbol)
- `nav: ShipNav`
- `cargo: ShipCargo` (units, capacity)
- `fuel: ShipFuel` (current, capacity)
- `frameName: String`
- `cooldown: Cooldown`

**`ShipRegistration`** — role: ShipRole, factionSymbol: String

**`ShipNav`** — systemSymbol, waypointSymbol, status: ShipNavStatus, flightMode: ShipNavFlightMode, route: ShipNavRoute

**`ShipNavRoute`** — origin: ShipNavRouteWaypoint, destination: ShipNavRouteWaypoint, departureTime: Instant, arrivalTime: Instant

**`ShipNavRouteWaypoint`** — symbol, type: WaypointType, systemSymbol, x: Int, y: Int

**`ShipFuel`** — current: Int, capacity: Int

**`ShipCargo`** — units: Int, capacity: Int (no inventory detail for now)

**`Cooldown`** — shipSymbol, totalSeconds: Int, remainingSeconds: Int, expiration: Instant?

**`MarketTransaction`** — waypointSymbol, shipSymbol, tradeSymbol, type: String, units: Int, pricePerUnit: Int, totalPrice: Int, timestamp: Instant

### Expanded DTOs (`api/dto/`)

Expand existing `ShipDto.kt` and add new DTO files mirroring the domain models with `@Serializable` annotations. Key new DTOs:

- `ShipRegistrationDto`
- `ShipNavDto` (expanded with route + flightMode)
- `ShipNavRouteDto`, `ShipNavRouteWaypointDto`
- `ShipFuelDto` (expanded with optional consumed object)
- `ShipCargoDto`
- `CooldownDto`
- `MarketTransactionDto`
- `RefuelResponseDto` (agent + fuel + transaction + optional cargo)
- `OrbitResponseDto` / `DockResponseDto` (nav wrapper)

### FleetApi (`api/endpoints/FleetApi.kt`)

Interface + `FleetApiImpl`:

```
getMyShips(page: Int = 1, limit: Int = 20): PaginatedResponse<ShipDto>
getMyShip(shipSymbol: String): ShipDto
orbitShip(shipSymbol: String): ShipNavDto
dockShip(shipSymbol: String): ShipNavDto
refuelShip(shipSymbol: String): RefuelResponseDto
```

All use `SpaceTradersClient.authenticated`. Orbit/dock unwrap the `{ "data": { "nav": ... } }` response. Refuel unwraps `{ "data": { "agent": ..., "fuel": ..., "transaction": ..., "cargo": ... } }`.

### Mappers (`api/mapper/`)

Expand `ShipMapper.kt` with extension functions for all new DTOs → domain models. Date string → `Instant` parsing follows the `ContractMapper` pattern.

New mapper file: `MarketTransactionMapper.kt` for `MarketTransactionDto.toDomain()`.

### FleetRepository (`domain/repository/FleetRepository.kt`)

Interface:
```
getMyShips(page: Int, limit: Int): List<Ship>
getMyShip(shipSymbol: String): Ship
```

### FleetRepositoryImpl (`data/repository/FleetRepositoryImpl.kt`)

Takes `FleetApi`, calls endpoints, maps via extensions.

### Use Cases (`domain/usecase/`)

**`OrbitShipUseCase`** — Interface + Impl. `invoke(shipSymbol: String): ShipNav`. Calls `fleetApi.orbitShip()`, maps result.

**`DockShipUseCase`** — Interface + Impl. `invoke(shipSymbol: String): ShipNav`. Calls `fleetApi.dockShip()`, maps result.

**`RefuelShipUseCase`** — Interface + Impl. `invoke(shipSymbol: String): RefuelResult`. Calls `fleetApi.refuelShip()`, maps agent/fuel/transaction into `RefuelResult(agent: Agent, fuel: ShipFuel, transaction: MarketTransaction)`.

## App Layer

### Navigation

**New routes** in `Routes.kt`:
- `ShipListRoute` — `@Serializable object`
- `ShipDetailRoute` — `@Serializable data class` with `shipSymbol: String`

**New NavigationTarget variants:**
- `NavigationTarget.ShipList`
- `NavigationTarget.ShipDetail(shipSymbol: String)`

**NavHost updates:** Two new `composable` entries for ship list and detail. Dashboard fleet card gets `onNavigateToShipList` callback.

### Ship List Screen (`ui/ships/`)

**ShipListViewModel:**
- State: `StateFlow<ShipListUiState>` with `ships: List<ShipSummary>`, `isLoading`, `error`
- `ShipSummary` — UI model: symbol, frameName, status: ShipNavStatus, waypointSymbol, arrivalTime: Instant?, departureTime: Instant?
- Events: `RetryClicked`, `ShipSelected(symbol: String)`
- Fetches via `FleetRepository.getMyShips()` on init
- Navigation: `Channel<NavigationTarget>` with `ShipDetail(symbol)`
- Re-fetches when screen is re-composed (covers returning from detail after actions)

**ShipListScreen composable:**
- Stateful wrapper → stateless content (existing pattern)
- Three visual states: loading, error (with retry), loaded
- `LazyColumn` of ship summary TerminalCards
- Each card shows: symbol (header), frame type, status, location
- IN_TRANSIT ships: live countdown timer + `TerminalProgressBar`
  - Progress = elapsed / total travel time (from departureTime/arrivalTime)
  - `LaunchedEffect` with `delay(1000)` tick loop
  - Auto re-fetches when countdown hits zero

### Ship Detail Screen (`ui/ships/`)

**ShipDetailViewModel:**
- Injected: `FleetRepository`, `OrbitShipUseCase`, `DockShipUseCase`, `RefuelShipUseCase`
- `shipSymbol` from `SavedStateHandle`
- State: `StateFlow<ShipDetailUiState>`

**ShipDetailUiState:**
- `ship: ShipDetail?` — UI model: symbol, frameName, role, status, flightMode, systemSymbol, waypointSymbol, route info, fuel current/capacity, cargo units/capacity
- `isLoading: Boolean`
- `isActionInProgress: Boolean`
- `actionResult: ActionResult?`
- `error: String?`

**ActionResult** — sealed interface:
- `Orbited(nav: ShipNav)` — displays "ENTERING ORBIT AT {waypoint}"
- `Docked(nav: ShipNav)` — displays "DOCKING AT {waypoint}"
- `Refueled(fuelBefore: Int, fuelAfter: Int, cost: Int, newCredits: Long)` — displays "REFUELED {units} UNITS — {cost} CREDITS — BALANCE: {credits}"

**Events:** `OrbitClicked`, `DockClicked`, `RefuelClicked`, `ActionResultDismissed`, `RetryClicked`

**Screen layout** (top to bottom):
1. **Header** — ship symbol
2. **Vessel Data TerminalCard** — frame name, role, flight mode
3. **Navigation TerminalCard** — status, system + waypoint, destination/countdown/progress if in-transit
4. **Fuel TerminalCard** — `TerminalProgressBar` with current/capacity
5. **Cargo TerminalCard** — `TerminalProgressBar` with units/capacity
6. **Command Output TerminalCard** — conditionally shown in amber (tertiary) when `actionResult` is non-null. Auto-dismisses after a few seconds or on tap.
7. **Action buttons** — contextual:
   - DOCKED → "ORBIT", "REFUEL"
   - IN_ORBIT → "DOCK"
   - IN_TRANSIT → no buttons

**Action flow:**
1. Tap action → event → `isActionInProgress = true` (prevents double-tap)
2. Use case executes → success: update ship state in UiState, set `actionResult`, buttons swap
3. Failure: catch `SpaceTradersApiException`, display error

### New Reusable Component

**`TerminalProgressBar`** (`ui/components/TerminalProgressBar.kt`):
- Bordered bar with green fill on black background
- Shows label text (e.g. "340/400" or "67%")
- Configurable: value (0f-1f), label, color
- Fits retro terminal aesthetic with sharp corners

### DI Updates (`di/SdkModule.kt`)

Singleton-scoped:
- `FleetApi` → `FleetApiImpl`
- `FleetRepository` → `FleetRepositoryImpl`

Unscoped:
- `OrbitShipUseCase` → `OrbitShipUseCaseImpl`
- `DockShipUseCase` → `DockShipUseCaseImpl`
- `RefuelShipUseCase` → `RefuelShipUseCaseImpl`

## Testing

### SDK Tests (`spacetradersiosdk/src/androidHostTest/`)

- **DTO deserialization** — all new DTOs from JSON fixtures matching API spec shapes
- **Mapper tests** — every DTO → domain mapping including date parsing, enum conversion, null/optional fields
- **Enum tests** — `fromString()` for all new enums, including unknown value fallback
- **FleetApiImpl tests** — MockEngine with canned responses for all 5 endpoints; verify HTTP method, path, headers, pagination params
- **FleetRepositoryImpl tests** — hand-written FleetApi fake; verify mapping delegation
- **Use case tests** — hand-written FleetApi fakes; verify each returns correct domain result. Extra coverage for RefuelShipUseCase multi-field response.

### App Tests (`app/src/test/`)

- **ShipListViewModel** — fake FleetRepository; verify loading → loaded state, error handling, navigation events (Turbine)
- **ShipDetailViewModel** — fake FleetRepository + fake use cases; verify initial load, action state transitions, action result content, error handling, button visibility per status

All tests use: `kotlin.test`, `runTest`, `StandardTestDispatcher` + `advanceUntilIdle()`, Turbine for Channel/Flow, hand-written fakes.

**Coverage target:** 100% class, method, line, and branch for all new code.

## Out of Scope

- Ship navigation (setting destinations) — next feature set
- Module/mount/reactor/engine details on ship detail
- Partial refueling
- Cargo inventory display
- Ship purchasing
- Pagination on ship list (fetch all on first load; most agents have <20 ships)
