# SQLDelight Offline-First Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace in-memory `EntityStateStore`-based caching with SQLDelight as the persistent single source of truth for Ship and Agent entities, enabling an offline-first cache-then-network data strategy.

**Architecture:** SQLDelight generates type-safe Kotlin from `.sq` schema files. Platform-specific `SqlDriver` factories provide Android (AndroidSqliteDriver), iOS (NativeSqliteDriver), and JVM test (JdbcSqliteDriver) drivers. Repositories expose `Flow<T>` backed by SQLDelight's coroutine extensions; ViewModels observe these Flows directly. Network fetches write to the DB, which automatically pushes updates through the Flows.

**Tech Stack:** SQLDelight 2.0.2, SQLDelight Coroutine Extensions, AndroidSqliteDriver, NativeSqliteDriver, JdbcSqliteDriver (tests), Ktor MockEngine (tests), Turbine (Flow tests)

---

## File Structure

### New Files (SDK - commonMain)
- `spacetradersiosdk/src/commonMain/sqldelight/com/brokenhuskysledteam/spacetradersio/sdk/data/db/Ship.sq` — Ship table schema and queries
- `spacetradersiosdk/src/commonMain/sqldelight/com/brokenhuskysledteam/spacetradersio/sdk/data/db/Agent.sq` — Agent table schema and queries
- `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/SqlDriverFactory.kt` — `expect class SqlDriverFactory`
- `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/ShipDbMapper.kt` — SQLDelight row <-> domain model mapping
- `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/AgentDbMapper.kt` — SQLDelight row <-> domain model mapping
- `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/AgentRepositoryImpl.kt` — AgentRepository implementation
- `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/repository/AgentRepository.kt` — AgentRepository interface

### New Files (SDK - platform actuals)
- `spacetradersiosdk/src/androidMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/SqlDriverFactory.android.kt` — Android actual
- `spacetradersiosdk/src/iosMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/SqlDriverFactory.ios.kt` — iOS actual

### New Files (SDK - tests)
- `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/ShipDbMapperTest.kt`
- `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/AgentDbMapperTest.kt`
- `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/AgentRepositoryImplTest.kt`
- `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/testing/TestDatabase.kt` — `createTestDatabase()` helper

### Modified Files (SDK)
- `gradle/libs.versions.toml` — Add SQLDelight version, libraries, plugin
- `spacetradersiosdk/build.gradle.kts` — Apply SQLDelight plugin, add dependencies per source set
- `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/repository/FleetRepository.kt` — New interface with observe/refresh/update/clearAll
- `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/FleetRepositoryImpl.kt` — Rewrite with SQLDelight
- `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/DockShipUseCase.kt` — Use FleetRepository instead of FleetStateStore
- `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/OrbitShipUseCase.kt` — Use FleetRepository instead of FleetStateStore
- `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/RefuelShipUseCase.kt` — Use FleetRepository + AgentRepository instead of state stores
- `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/NavigateShipUseCase.kt` — Use FleetRepository instead of FleetStateStore
- `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/session/SpaceTradersSession.kt` — Remove fleetStateStore/agentStateStore, add database ref
- `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/session/SpaceTradersSessionImpl.kt` — Remove state stores, add clearAll on destroy

### Modified Files (SDK - tests)
- `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/FleetRepositoryImplTest.kt` — Rewrite with in-memory DB
- `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/FleetRepositoryImplWriteThroughTest.kt` — Rewrite with in-memory DB
- `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/DockShipUseCaseTest.kt` — Use FleetRepository instead of FleetStateStore
- `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/OrbitShipUseCaseTest.kt` — Use FleetRepository instead of FleetStateStore
- `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/RefuelShipUseCaseTest.kt` — Use FleetRepository + AgentRepository instead of state stores
- `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/NavigateShipUseCaseTest.kt` — Use FleetRepository instead of FleetStateStore
- `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/session/SessionManagerTest.kt` — Remove fleetStateStore/agentStateStore assertions

### Deleted Files (SDK)
- `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/state/FleetStateStore.kt`
- `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/state/AgentStateStore.kt`
- `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/state/AgentStateStoreTest.kt`

### Modified Files (App)
- `app/src/main/java/com/brokenhuskysledteam/spacetradersio/di/SdkModule.kt` — Provide SqlDriverFactory, SpaceTradersDatabase, AgentRepository; remove state store providers; rewire use case constructors
- `app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/ships/ShipListViewModel.kt` — Replace FleetStateStore with FleetRepository.observeShips()
- `app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/ships/ShipDetailViewModel.kt` — Replace FleetStateStore with FleetRepository.observeShip()
- `app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/dashboard/DashboardViewModel.kt` — Replace AgentStateStore+AgentsApi with AgentRepository
- `app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/systemmap/SystemMapViewModel.kt` — Replace FleetStateStore with FleetRepository.observeShips()
- `app/build.gradle.kts` — Add sqldelight-android-driver dependency (for SqlDriverFactory Context)

### Modified Files (App - tests)
- `app/src/test/java/com/brokenhuskysledteam/spacetradersio/ui/ships/ShipListViewModelTest.kt` — Replace FakeFleetRepository+FleetStateStore with new FakeFleetRepository using MutableStateFlow
- `app/src/test/java/com/brokenhuskysledteam/spacetradersio/ui/ships/ShipDetailViewModelTest.kt` — Replace FleetStateStore with FleetRepository.observeShip()
- `app/src/test/java/com/brokenhuskysledteam/spacetradersio/ui/dashboard/DashboardViewModelTest.kt` — Replace AgentStateStore+FakeAgentsApi with FakeAgentRepository
- `app/src/test/java/com/brokenhuskysledteam/spacetradersio/ui/systemmap/SystemMapViewModelTest.kt` — Replace FleetStateStore with FakeFleetRepository

### Documentation Updates (Final Step)
- `CLAUDE.md` — Add SQLDelight dependency, patterns, gotchas
- `docs/ARCHITECTURE_REFERENCE_HUMAN.md` — Add offline-first pattern narrative
- `docs/ARCHITECTURE_REFERENCE_AI.md` — Add offline-first rules bullets
- `README.md` — Add SQLDelight to key dependencies, describe offline-first strategy

---

## Task 1: Gradle Setup — Add SQLDelight Plugin and Dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `spacetradersiosdk/build.gradle.kts`

- [ ] **Step 1: Add SQLDelight entries to version catalog**

In `gradle/libs.versions.toml`, add under `[versions]`:

```toml
sqldelight = "2.0.2"
```

Under `[libraries]`, add:

```toml
sqldelight-coroutines = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
sqldelight-android-driver = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-native-driver = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }
sqldelight-jvm-driver = { module = "app.cash.sqldelight:sqlite-driver", version.ref = "sqldelight" }
sqldelight-dialect = { module = "app.cash.sqldelight:sqlite-3-38-dialect", version.ref = "sqldelight" }
```

Under `[plugins]`, add:

```toml
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
```

- [ ] **Step 2: Apply SQLDelight plugin in SDK build file**

In `spacetradersiosdk/build.gradle.kts`, add to the `plugins` block:

```kotlin
alias(libs.plugins.sqldelight)
```

- [ ] **Step 3: Add SQLDelight database configuration**

In `spacetradersiosdk/build.gradle.kts`, add after the `kotlin { ... }` block:

```kotlin
sqldelight {
    databases {
        create("SpaceTradersDatabase") {
            packageName.set("com.brokenhuskysledteam.spacetradersio.sdk.data.db")
            dialect(libs.sqldelight.dialect)
        }
    }
}
```

- [ ] **Step 4: Add SQLDelight dependencies per source set**

In `spacetradersiosdk/build.gradle.kts`, add to the existing `sourceSets` block:

In `commonMain.dependencies`:
```kotlin
implementation(libs.sqldelight.coroutines)
```

In `androidMain.dependencies`:
```kotlin
implementation(libs.sqldelight.android.driver)
```

In `iosMain.dependencies`:
```kotlin
implementation(libs.sqldelight.native.driver)
```

In `commonTest.dependencies` (which feeds androidHostTest):
```kotlin
implementation(libs.sqldelight.jvm.driver)
```

- [ ] **Step 5: Verify Gradle sync succeeds**

Run: `gradlew.bat :spacetradersiosdk:dependencies --configuration commonMainImplementationDependenciesMetadata`
Expected: Build resolves SQLDelight dependencies without errors.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml spacetradersiosdk/build.gradle.kts
git commit -m "chore: add SQLDelight plugin and dependencies to SDK module"
```

---

## Task 2: Database Schema — Ship.sq and Agent.sq

**Files:**
- Create: `spacetradersiosdk/src/commonMain/sqldelight/com/brokenhuskysledteam/spacetradersio/sdk/data/db/Ship.sq`
- Create: `spacetradersiosdk/src/commonMain/sqldelight/com/brokenhuskysledteam/spacetradersio/sdk/data/db/Agent.sq`

- [ ] **Step 1: Create the Ship.sq schema file**

Create `spacetradersiosdk/src/commonMain/sqldelight/com/brokenhuskysledteam/spacetradersio/sdk/data/db/Ship.sq`:

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

- [ ] **Step 2: Create the Agent.sq schema file**

Create `spacetradersiosdk/src/commonMain/sqldelight/com/brokenhuskysledteam/spacetradersio/sdk/data/db/Agent.sq`:

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

- [ ] **Step 3: Verify SQLDelight code generation**

Run: `gradlew.bat :spacetradersiosdk:generateCommonMainSpaceTradersDatabaseInterface`
Expected: Generates `SpaceTradersDatabase`, `ShipQueries`, `AgentQueries` in build output without errors.

- [ ] **Step 4: Commit**

```bash
git add spacetradersiosdk/src/commonMain/sqldelight/
git commit -m "feat: add SQLDelight schema for ship and agent tables"
```

---

## Task 3: Platform SqlDriverFactory — expect/actual

**Files:**
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/SqlDriverFactory.kt`
- Create: `spacetradersiosdk/src/androidMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/SqlDriverFactory.android.kt`
- Create: `spacetradersiosdk/src/iosMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/SqlDriverFactory.ios.kt`
- Create: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/testing/TestDatabase.kt`

- [ ] **Step 1: Create the expect declaration in commonMain**

Create `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/SqlDriverFactory.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.data.db

import app.cash.sqldelight.db.SqlDriver

expect class SqlDriverFactory {
    fun create(): SqlDriver
}
```

- [ ] **Step 2: Create the Android actual**

Create `spacetradersiosdk/src/androidMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/SqlDriverFactory.android.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.data.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class SqlDriverFactory(private val context: Context) {
    actual fun create(): SqlDriver =
        AndroidSqliteDriver(SpaceTradersDatabase.Schema, context, "spacetraders.db")
}
```

- [ ] **Step 3: Create the iOS actual**

Create `spacetradersiosdk/src/iosMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/SqlDriverFactory.ios.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class SqlDriverFactory {
    actual fun create(): SqlDriver =
        NativeSqliteDriver(SpaceTradersDatabase.Schema, "spacetraders.db")
}
```

- [ ] **Step 4: Create test database helper**

Create `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/testing/TestDatabase.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.testing

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.SpaceTradersDatabase

fun createTestDatabase(): SpaceTradersDatabase {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    SpaceTradersDatabase.Schema.create(driver)
    return SpaceTradersDatabase(driver)
}
```

- [ ] **Step 5: Verify compilation across all targets**

Run: `gradlew.bat :spacetradersiosdk:compileDebugKotlinAndroid`
Expected: Compiles without errors (verifies Android actual resolves).

Run: `gradlew.bat :spacetradersiosdk:compileKotlinIosArm64`
Expected: Compiles without errors (verifies iOS actual resolves).

- [ ] **Step 6: Commit**

```bash
git add spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/SqlDriverFactory.kt
git add spacetradersiosdk/src/androidMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/SqlDriverFactory.android.kt
git add spacetradersiosdk/src/iosMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/SqlDriverFactory.ios.kt
git add spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/testing/TestDatabase.kt
git commit -m "feat: add platform SqlDriverFactory expect/actual and test helper"
```

---

## Task 4: Ship DB Mapper — Domain <-> SQLDelight Row

**Files:**
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/ShipDbMapper.kt`
- Create: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/ShipDbMapperTest.kt`

- [ ] **Step 1: Write the failing test for Ship round-trip mapping**

Create `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/ShipDbMapperTest.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.data.db

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Cooldown
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipCargo
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRoute
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRouteWaypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipRegistration
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavFlightMode
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipRole
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import com.brokenhuskysledteam.spacetradersio.sdk.testing.createTestDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class ShipDbMapperTest {

    private val testShip = Ship(
        symbol = "LADD-1",
        registration = ShipRegistration(ShipRole.COMMAND, "COSMIC"),
        nav = ShipNav(
            systemSymbol = "X1-DF55",
            waypointSymbol = "X1-DF55-20250Z",
            status = ShipNavStatus.DOCKED,
            flightMode = ShipNavFlightMode.CRUISE,
            route = ShipNavRoute(
                origin = ShipNavRouteWaypoint("X1-DF55-20250Z", WaypointType.MOON, "X1-DF55", 0, 0),
                destination = ShipNavRouteWaypoint("X1-DF55-17335A", WaypointType.PLANET, "X1-DF55", -21, -16),
                departureTime = Instant.parse("2025-06-01T10:00:00Z"),
                arrivalTime = Instant.parse("2025-06-01T10:30:00Z")
            )
        ),
        cargo = ShipCargo(units = 5, capacity = 40),
        fuel = ShipFuel(current = 350, capacity = 400),
        frameName = "Shuttle Frame",
        cooldown = Cooldown(shipSymbol = "LADD-1", totalSeconds = 60, remainingSeconds = 30, expiration = Instant.parse("2025-06-01T10:01:00Z"))
    )

    @Test
    fun roundTrip_upsertThenSelect_returnsEqualShip() {
        val db = createTestDatabase()
        db.shipQueries.upsertShip(testShip)
        val result = db.shipQueries.selectShipBySymbol("LADD-1").executeAsOne().toDomain()
        assertEquals(testShip, result)
    }

    @Test
    fun roundTrip_nullCooldownExpiration_preservesNull() {
        val db = createTestDatabase()
        val shipNoCooldown = testShip.copy(
            cooldown = Cooldown("LADD-1", 0, 0, null)
        )
        db.shipQueries.upsertShip(shipNoCooldown)
        val result = db.shipQueries.selectShipBySymbol("LADD-1").executeAsOne().toDomain()
        assertNull(result.cooldown.expiration)
    }

    @Test
    fun roundTrip_multipleShips_selectAllReturnsAll() {
        val db = createTestDatabase()
        val ship2 = testShip.copy(symbol = "LADD-2", cooldown = testShip.cooldown.copy(shipSymbol = "LADD-2"))
        db.shipQueries.upsertShip(testShip)
        db.shipQueries.upsertShip(ship2)
        val result = db.shipQueries.selectAllShips().executeAsList().map { it.toDomain() }
        assertEquals(2, result.size)
        assertEquals(setOf("LADD-1", "LADD-2"), result.map { it.symbol }.toSet())
    }

    @Test
    fun upsert_sameSymbol_replacesExisting() {
        val db = createTestDatabase()
        db.shipQueries.upsertShip(testShip)
        val updated = testShip.copy(fuel = ShipFuel(200, 400))
        db.shipQueries.upsertShip(updated)
        val result = db.shipQueries.selectShipBySymbol("LADD-1").executeAsOne().toDomain()
        assertEquals(200, result.fuel.current)
    }

    @Test
    fun deleteAllShips_emptiesTable() {
        val db = createTestDatabase()
        db.shipQueries.upsertShip(testShip)
        db.shipQueries.deleteAllShips()
        val result = db.shipQueries.selectAllShips().executeAsList()
        assertEquals(0, result.size)
    }

    @Test
    fun updateShipNav_updatesOnlyNavFields() {
        val db = createTestDatabase()
        db.shipQueries.upsertShip(testShip)
        val newNav = testShip.nav.copy(status = ShipNavStatus.IN_ORBIT, waypointSymbol = "X1-DF55-30A")
        db.shipQueries.updateShipNav(newNav, "LADD-1")
        val result = db.shipQueries.selectShipBySymbol("LADD-1").executeAsOne().toDomain()
        assertEquals(ShipNavStatus.IN_ORBIT, result.nav.status)
        assertEquals("X1-DF55-30A", result.nav.waypointSymbol)
        assertEquals(350, result.fuel.current) // fuel unchanged
    }

    @Test
    fun updateShipFuel_updatesOnlyFuelFields() {
        val db = createTestDatabase()
        db.shipQueries.upsertShip(testShip)
        db.shipQueries.updateShipFuel(fuel_current = 100, fuel_capacity = 400, symbol = "LADD-1")
        val result = db.shipQueries.selectShipBySymbol("LADD-1").executeAsOne().toDomain()
        assertEquals(100, result.fuel.current)
        assertEquals(ShipNavStatus.DOCKED, result.nav.status) // nav unchanged
    }

    @Test
    fun updateShipCargo_updatesOnlyCargoFields() {
        val db = createTestDatabase()
        db.shipQueries.upsertShip(testShip)
        db.shipQueries.updateShipCargo(cargo_units = 20, cargo_capacity = 40, symbol = "LADD-1")
        val result = db.shipQueries.selectShipBySymbol("LADD-1").executeAsOne().toDomain()
        assertEquals(20, result.cargo.units)
    }

    @Test
    fun updateShipCooldown_updatesOnlyCooldownFields() {
        val db = createTestDatabase()
        db.shipQueries.upsertShip(testShip)
        db.shipQueries.updateShipCooldown(
            cooldown_total_seconds = 0,
            cooldown_remaining_seconds = 0,
            cooldown_expiration = null,
            symbol = "LADD-1"
        )
        val result = db.shipQueries.selectShipBySymbol("LADD-1").executeAsOne().toDomain()
        assertEquals(0, result.cooldown.totalSeconds)
        assertNull(result.cooldown.expiration)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :spacetradersiosdk:testAndroidHostTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.data.db.ShipDbMapperTest"`
Expected: FAIL — `upsertShip(Ship)` and `toDomain()` extension functions do not exist yet.

- [ ] **Step 3: Write the ShipDbMapper implementation**

Create `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/ShipDbMapper.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.data.db

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Cooldown
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipCargo
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRoute
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRouteWaypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipRegistration
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavFlightMode
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipRole
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import kotlin.time.Instant

fun Ship_Table.toDomain(): Ship = Ship(
    symbol = symbol,
    registration = ShipRegistration(
        role = ShipRole.valueOf(reg_role),
        factionSymbol = reg_faction_symbol
    ),
    nav = ShipNav(
        systemSymbol = nav_system_symbol,
        waypointSymbol = nav_waypoint_symbol,
        status = ShipNavStatus.valueOf(nav_status),
        flightMode = ShipNavFlightMode.valueOf(nav_flight_mode),
        route = ShipNavRoute(
            origin = ShipNavRouteWaypoint(
                symbol = route_origin_symbol,
                type = WaypointType.valueOf(route_origin_type),
                systemSymbol = route_origin_system,
                x = route_origin_x.toInt(),
                y = route_origin_y.toInt()
            ),
            destination = ShipNavRouteWaypoint(
                symbol = route_dest_symbol,
                type = WaypointType.valueOf(route_dest_type),
                systemSymbol = route_dest_system,
                x = route_dest_x.toInt(),
                y = route_dest_y.toInt()
            ),
            departureTime = Instant.parse(route_departure),
            arrivalTime = Instant.parse(route_arrival)
        )
    ),
    cargo = ShipCargo(units = cargo_units.toInt(), capacity = cargo_capacity.toInt()),
    fuel = ShipFuel(current = fuel_current.toInt(), capacity = fuel_capacity.toInt()),
    frameName = frame_name,
    cooldown = Cooldown(
        shipSymbol = symbol,
        totalSeconds = cooldown_total_seconds.toInt(),
        remainingSeconds = cooldown_remaining_seconds.toInt(),
        expiration = cooldown_expiration?.let { Instant.parse(it) }
    )
)

fun ShipQueries.upsertShip(ship: Ship) {
    upsertShip(
        symbol = ship.symbol,
        reg_role = ship.registration.role.name,
        reg_faction_symbol = ship.registration.factionSymbol,
        nav_system_symbol = ship.nav.systemSymbol,
        nav_waypoint_symbol = ship.nav.waypointSymbol,
        nav_status = ship.nav.status.name,
        nav_flight_mode = ship.nav.flightMode.name,
        route_origin_symbol = ship.nav.route.origin.symbol,
        route_origin_type = ship.nav.route.origin.type.name,
        route_origin_system = ship.nav.route.origin.systemSymbol,
        route_origin_x = ship.nav.route.origin.x.toLong(),
        route_origin_y = ship.nav.route.origin.y.toLong(),
        route_dest_symbol = ship.nav.route.destination.symbol,
        route_dest_type = ship.nav.route.destination.type.name,
        route_dest_system = ship.nav.route.destination.systemSymbol,
        route_dest_x = ship.nav.route.destination.x.toLong(),
        route_dest_y = ship.nav.route.destination.y.toLong(),
        route_departure = ship.nav.route.departureTime.toString(),
        route_arrival = ship.nav.route.arrivalTime.toString(),
        cargo_units = ship.cargo.units.toLong(),
        cargo_capacity = ship.cargo.capacity.toLong(),
        fuel_current = ship.fuel.current.toLong(),
        fuel_capacity = ship.fuel.capacity.toLong(),
        frame_name = ship.frameName,
        cooldown_total_seconds = ship.cooldown.totalSeconds.toLong(),
        cooldown_remaining_seconds = ship.cooldown.remainingSeconds.toLong(),
        cooldown_expiration = ship.cooldown.expiration?.toString()
    )
}

fun ShipQueries.updateShipNav(nav: ShipNav, shipSymbol: String) {
    updateShipNav(
        nav_system_symbol = nav.systemSymbol,
        nav_waypoint_symbol = nav.waypointSymbol,
        nav_status = nav.status.name,
        nav_flight_mode = nav.flightMode.name,
        route_origin_symbol = nav.route.origin.symbol,
        route_origin_type = nav.route.origin.type.name,
        route_origin_system = nav.route.origin.systemSymbol,
        route_origin_x = nav.route.origin.x.toLong(),
        route_origin_y = nav.route.origin.y.toLong(),
        route_dest_symbol = nav.route.destination.symbol,
        route_dest_type = nav.route.destination.type.name,
        route_dest_system = nav.route.destination.systemSymbol,
        route_dest_x = nav.route.destination.x.toLong(),
        route_dest_y = nav.route.destination.y.toLong(),
        route_departure = nav.route.departureTime.toString(),
        route_arrival = nav.route.arrivalTime.toString(),
        symbol = shipSymbol
    )
}
```

NOTE: The generated `Ship_Table` class name depends on what SQLDelight generates from the `ship` table. It may be `Ship` (matching the table name). Check the generated code in `build/generated/sqldelight/` after Task 2's code generation step and adjust the receiver type name accordingly. The same applies to `ShipQueries` — it will be named based on the `.sq` file name.

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :spacetradersiosdk:testAndroidHostTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.data.db.ShipDbMapperTest"`
Expected: All 9 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/ShipDbMapper.kt
git add spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/ShipDbMapperTest.kt
git commit -m "feat: add Ship DB mapper with round-trip tests"
```

---

## Task 5: Agent DB Mapper — Domain <-> SQLDelight Row

**Files:**
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/AgentDbMapper.kt`
- Create: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/AgentDbMapperTest.kt`

- [ ] **Step 1: Write the failing test for Agent round-trip mapping**

Create `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/AgentDbMapperTest.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.data.db

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import com.brokenhuskysledteam.spacetradersio.sdk.testing.createTestDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentDbMapperTest {

    private val testAgent = Agent(
        accountId = "acc-123",
        symbol = "LADD",
        headquarters = "X1-DF55-20250Z",
        credits = 150000L,
        startingFaction = "COSMIC",
        shipCount = 3
    )

    @Test
    fun roundTrip_upsertThenSelect_returnsEqualAgent() {
        val db = createTestDatabase()
        db.agentQueries.upsertAgent(testAgent)
        val result = db.agentQueries.selectAgent().executeAsOneOrNull()?.toDomain()
        assertEquals(testAgent, result)
    }

    @Test
    fun roundTrip_nullAccountId_preservesNull() {
        val db = createTestDatabase()
        val agentNoAccount = testAgent.copy(accountId = null)
        db.agentQueries.upsertAgent(agentNoAccount)
        val result = db.agentQueries.selectAgent().executeAsOneOrNull()?.toDomain()
        assertNull(result?.accountId)
    }

    @Test
    fun upsert_sameSymbol_replacesExisting() {
        val db = createTestDatabase()
        db.agentQueries.upsertAgent(testAgent)
        val updated = testAgent.copy(credits = 200000L)
        db.agentQueries.upsertAgent(updated)
        val result = db.agentQueries.selectAgent().executeAsOneOrNull()?.toDomain()
        assertEquals(200000L, result?.credits)
    }

    @Test
    fun updateCredits_changesOnlyCreditsField() {
        val db = createTestDatabase()
        db.agentQueries.upsertAgent(testAgent)
        db.agentQueries.updateCredits(credits = 999L, symbol = "LADD")
        val result = db.agentQueries.selectAgent().executeAsOneOrNull()?.toDomain()
        assertEquals(999L, result?.credits)
        assertEquals("X1-DF55-20250Z", result?.headquarters) // unchanged
    }

    @Test
    fun deleteAll_emptiesTable() {
        val db = createTestDatabase()
        db.agentQueries.upsertAgent(testAgent)
        db.agentQueries.deleteAll()
        val result = db.agentQueries.selectAgent().executeAsOneOrNull()
        assertNull(result)
    }

    @Test
    fun selectAgent_emptyTable_returnsNull() {
        val db = createTestDatabase()
        val result = db.agentQueries.selectAgent().executeAsOneOrNull()
        assertNull(result)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :spacetradersiosdk:testAndroidHostTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.data.db.AgentDbMapperTest"`
Expected: FAIL — `upsertAgent(Agent)` and `toDomain()` do not exist yet.

- [ ] **Step 3: Write the AgentDbMapper implementation**

Create `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/AgentDbMapper.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.data.db

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent

fun Agent_Table.toDomain(): Agent = Agent(
    accountId = account_id,
    symbol = symbol,
    headquarters = headquarters,
    credits = credits,
    startingFaction = starting_faction,
    shipCount = ship_count.toInt()
)

fun AgentQueries.upsertAgent(agent: Agent) {
    upsertAgent(
        symbol = agent.symbol,
        account_id = agent.accountId,
        headquarters = agent.headquarters,
        credits = agent.credits,
        starting_faction = agent.startingFaction,
        ship_count = agent.shipCount.toLong()
    )
}
```

NOTE: Same caveat as Task 4 — `Agent_Table` is a placeholder for whatever SQLDelight generates from the `agent` table. Check the generated code and adjust.

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :spacetradersiosdk:testAndroidHostTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.data.db.AgentDbMapperTest"`
Expected: All 6 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/AgentDbMapper.kt
git add spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/db/AgentDbMapperTest.kt
git commit -m "feat: add Agent DB mapper with round-trip tests"
```

---

## Task 6: AgentRepository Interface and Implementation

**Files:**
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/repository/AgentRepository.kt`
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/AgentRepositoryImpl.kt`
- Create: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/AgentRepositoryImplTest.kt`

- [ ] **Step 1: Write the failing test for AgentRepositoryImpl**

Create `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/AgentRepositoryImplTest.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.AgentDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AgentsApi
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.SpaceTradersDatabase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import com.brokenhuskysledteam.spacetradersio.sdk.testing.createTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class FakeAgentsApi(
    var agentResult: AgentDto = AgentDto(
        accountId = "acc-1",
        symbol = "LADD",
        headquarters = "X1-DF55-20250Z",
        credits = 150000L,
        startingFaction = "COSMIC",
        shipCount = 3
    ),
    var exception: Exception? = null
) : AgentsApi {
    override suspend fun getMyAgent(): AgentDto {
        exception?.let { throw it }
        return agentResult
    }
    override suspend fun getAgent(symbol: String): AgentDto = agentResult
}

class AgentRepositoryImplTest {

    private fun createRepo(
        db: SpaceTradersDatabase = createTestDatabase(),
        api: FakeAgentsApi = FakeAgentsApi()
    ) = AgentRepositoryImpl(api, db) to db

    @Test
    fun observeAgent_emptyDb_emitsNull() = runTest {
        val (repo, _) = createRepo()
        val result = repo.observeAgent().first()
        assertNull(result)
    }

    @Test
    fun refreshAgent_writesToDb_observeEmitsAgent() = runTest {
        val (repo, _) = createRepo()
        repo.refreshAgent()
        val result = repo.observeAgent().first()
        assertEquals("LADD", result?.symbol)
        assertEquals(150000L, result?.credits)
    }

    @Test
    fun refreshAgent_networkFails_dbUnchanged() = runTest {
        val api = FakeAgentsApi(exception = RuntimeException("Offline"))
        val (repo, _) = createRepo(api = api)
        try { repo.refreshAgent() } catch (_: RuntimeException) {}
        val result = repo.observeAgent().first()
        assertNull(result)
    }

    @Test
    fun saveAgent_writesToDb() = runTest {
        val (repo, _) = createRepo()
        val agent = Agent("acc-1", "LADD", "X1-DF55-20250Z", 100L, "COSMIC", 1)
        repo.saveAgent(agent)
        val result = repo.observeAgent().first()
        assertEquals(100L, result?.credits)
    }

    @Test
    fun updateCredits_changesOnlyCredits() = runTest {
        val (repo, _) = createRepo()
        repo.refreshAgent()
        repo.updateCredits("LADD", 999L)
        val result = repo.observeAgent().first()
        assertEquals(999L, result?.credits)
        assertEquals(3, result?.shipCount) // unchanged
    }

    @Test
    fun clearAll_emptiesTable() = runTest {
        val (repo, _) = createRepo()
        repo.refreshAgent()
        repo.clearAll()
        val result = repo.observeAgent().first()
        assertNull(result)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :spacetradersiosdk:testAndroidHostTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.data.repository.AgentRepositoryImplTest"`
Expected: FAIL — `AgentRepository` and `AgentRepositoryImpl` do not exist.

- [ ] **Step 3: Create the AgentRepository interface**

Create `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/repository/AgentRepository.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.repository

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import kotlinx.coroutines.flow.Flow

interface AgentRepository {
    fun observeAgent(): Flow<Agent?>
    suspend fun refreshAgent()
    suspend fun saveAgent(agent: Agent)
    suspend fun updateCredits(symbol: String, credits: Long)
    suspend fun clearAll()
}
```

- [ ] **Step 4: Create the AgentRepositoryImpl**

Create `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/AgentRepositoryImpl.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AgentsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.SpaceTradersDatabase
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.upsertAgent
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.AgentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
        queries.upsertAgent(agent)
    }

    override suspend fun saveAgent(agent: Agent) {
        queries.upsertAgent(agent)
    }

    override suspend fun updateCredits(symbol: String, credits: Long) {
        queries.updateCredits(credits = credits, symbol = symbol)
    }

    override suspend fun clearAll() {
        queries.deleteAll()
    }
}
```

NOTE: The `agentsApi.getMyAgent().toDomain()` calls the existing DTO-to-domain mapper from `api/mapper/AgentMapper.kt`. The `queries.upsertAgent(agent)` calls the DB mapper extension from Task 5.

- [ ] **Step 5: Run tests to verify they pass**

Run: `gradlew.bat :spacetradersiosdk:testAndroidHostTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.data.repository.AgentRepositoryImplTest"`
Expected: All 6 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/repository/AgentRepository.kt
git add spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/AgentRepositoryImpl.kt
git add spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/AgentRepositoryImplTest.kt
git commit -m "feat: add AgentRepository with SQLDelight-backed observe and refresh"
```

---

## Task 7: FleetRepository Refactor — SQLDelight-Backed

**Files:**
- Modify: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/repository/FleetRepository.kt`
- Modify: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/FleetRepositoryImpl.kt`
- Modify: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/FleetRepositoryImplTest.kt`
- Modify: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/FleetRepositoryImplWriteThroughTest.kt`

- [ ] **Step 1: Rewrite FleetRepositoryImplTest for new interface**

Replace the entire contents of `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/FleetRepositoryImplTest.kt` with:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.CooldownDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MetaDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.NavigateResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.PaginatedResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.RefuelResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipCargoDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipFrameSummaryDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipFuelDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavRouteDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavRouteWaypointDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipRegistrationDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.AgentDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MarketTransactionDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.SpaceTradersDatabase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRoute
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRouteWaypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavFlightMode
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.testing.createTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private fun minimalShipDto(
    symbol: String = "LADD-1",
    navStatus: String = "DOCKED"
) = ShipDto(
    symbol = symbol,
    registration = ShipRegistrationDto(name = symbol, factionSymbol = "COSMIC", role = "COMMAND"),
    frame = ShipFrameSummaryDto(symbol = "FRAME_SHUTTLE", name = "Shuttle Frame"),
    nav = ShipNavDto(
        systemSymbol = "X1-DF55",
        waypointSymbol = "X1-DF55-20250Z",
        route = ShipNavRouteDto(
            destination = ShipNavRouteWaypointDto("X1-DF55-20250Z", "MOON", "X1-DF55", 0, 0),
            origin = ShipNavRouteWaypointDto("X1-DF55-20250Z", "MOON", "X1-DF55", 0, 0),
            departureTime = "2025-06-01T10:00:00.000Z",
            arrival = "2025-06-01T10:00:00.000Z"
        ),
        status = navStatus,
        flightMode = "CRUISE"
    ),
    cargo = ShipCargoDto(capacity = 40, units = 0),
    fuel = ShipFuelDto(current = 400, capacity = 400),
    cooldown = CooldownDto(shipSymbol = symbol, totalSeconds = 0, remainingSeconds = 0)
)

private class FakeFleetApi(
    private val ships: List<ShipDto> = listOf(minimalShipDto()),
    private val singleShip: ShipDto = minimalShipDto()
) : FleetApi {
    var lastGetMyShipsPage: Int = -1
    var lastGetMyShipsLimit: Int = -1

    override suspend fun getMyShips(page: Int, limit: Int): PaginatedResponse<ShipDto> {
        lastGetMyShipsPage = page
        lastGetMyShipsLimit = limit
        return PaginatedResponse(data = ships, meta = MetaDto(total = ships.size, page = page, limit = limit))
    }
    override suspend fun getMyShip(shipSymbol: String): ShipDto = singleShip
    override suspend fun orbitShip(shipSymbol: String): ShipNavDto = singleShip.nav
    override suspend fun dockShip(shipSymbol: String): ShipNavDto = singleShip.nav
    override suspend fun refuelShip(shipSymbol: String): RefuelResponseDto = RefuelResponseDto(
        agent = AgentDto(null, "LADD", "X1-DF55-20250Z", 148500L, "COSMIC", 2),
        fuel = ShipFuelDto(current = 400, capacity = 400),
        transaction = MarketTransactionDto("X1-DF55-20250Z", shipSymbol, "FUEL", "PURCHASE", 6, 75, 450, "2025-06-01T10:00:00.000Z")
    )
    override suspend fun navigateShip(shipSymbol: String, waypointSymbol: String): NavigateResponseDto =
        NavigateResponseDto(nav = singleShip.nav, fuel = ShipFuelDto(current = 400, capacity = 400))
}

class FleetRepositoryImplTest {

    private fun createRepo(
        api: FakeFleetApi = FakeFleetApi(),
        db: SpaceTradersDatabase = createTestDatabase()
    ): FleetRepositoryImpl = runTest {
        FleetRepositoryImpl(api, db, RefreshScheduler(backgroundScope))
    }

    @Test
    fun observeShips_emptyDb_emitsEmptyList() = runTest {
        val repo = FleetRepositoryImpl(FakeFleetApi(), createTestDatabase(), RefreshScheduler(backgroundScope))
        val result = repo.observeShips().first()
        assertEquals(emptyList(), result)
    }

    @Test
    fun refreshMyShips_writesToDb_observeEmitsShips() = runTest {
        val db = createTestDatabase()
        val api = FakeFleetApi(ships = listOf(minimalShipDto("LADD-1"), minimalShipDto("LADD-2")))
        val repo = FleetRepositoryImpl(api, db, RefreshScheduler(backgroundScope))
        repo.refreshMyShips()
        val result = repo.observeShips().first()
        assertEquals(2, result.size)
    }

    @Test
    fun refreshMyShips_mapsSymbolCorrectly() = runTest {
        val db = createTestDatabase()
        val repo = FleetRepositoryImpl(FakeFleetApi(), db, RefreshScheduler(backgroundScope))
        repo.refreshMyShips()
        val result = repo.observeShips().first()
        assertEquals("LADD-1", result.first().symbol)
    }

    @Test
    fun refreshMyShips_forwardsPaginationParams() = runTest {
        val fake = FakeFleetApi()
        val repo = FleetRepositoryImpl(fake, createTestDatabase(), RefreshScheduler(backgroundScope))
        repo.refreshMyShips(page = 3, limit = 5)
        assertEquals(3, fake.lastGetMyShipsPage)
        assertEquals(5, fake.lastGetMyShipsLimit)
    }

    @Test
    fun observeShip_afterRefresh_emitsCorrectShip() = runTest {
        val db = createTestDatabase()
        val repo = FleetRepositoryImpl(FakeFleetApi(), db, RefreshScheduler(backgroundScope))
        repo.refreshMyShip("LADD-1")
        val result = repo.observeShip("LADD-1").first()
        assertEquals("LADD-1", result?.symbol)
    }

    @Test
    fun observeShip_unknownSymbol_emitsNull() = runTest {
        val repo = FleetRepositoryImpl(FakeFleetApi(), createTestDatabase(), RefreshScheduler(backgroundScope))
        val result = repo.observeShip("UNKNOWN").first()
        assertNull(result)
    }

    @Test
    fun updateShipNav_changesNavFields() = runTest {
        val db = createTestDatabase()
        val repo = FleetRepositoryImpl(FakeFleetApi(), db, RefreshScheduler(backgroundScope))
        repo.refreshMyShip("LADD-1")
        val newNav = ShipNav(
            "X1-DF55", "X1-DF55-20250Z", ShipNavStatus.IN_ORBIT, ShipNavFlightMode.CRUISE,
            ShipNavRoute(
                ShipNavRouteWaypoint("X1-DF55-20250Z", WaypointType.MOON, "X1-DF55", 0, 0),
                ShipNavRouteWaypoint("X1-DF55-20250Z", WaypointType.MOON, "X1-DF55", 0, 0),
                Instant.parse("2025-06-01T10:00:00Z"), Instant.parse("2025-06-01T10:00:00Z")
            )
        )
        repo.updateShipNav("LADD-1", newNav)
        val result = repo.observeShip("LADD-1").first()
        assertEquals(ShipNavStatus.IN_ORBIT, result?.nav?.status)
        assertEquals(400, result?.fuel?.current) // unchanged
    }

    @Test
    fun updateShipFuel_changesFuelFields() = runTest {
        val db = createTestDatabase()
        val repo = FleetRepositoryImpl(FakeFleetApi(), db, RefreshScheduler(backgroundScope))
        repo.refreshMyShip("LADD-1")
        repo.updateShipFuel("LADD-1", ShipFuel(200, 400))
        val result = repo.observeShip("LADD-1").first()
        assertEquals(200, result?.fuel?.current)
    }

    @Test
    fun clearAll_emptiesTable() = runTest {
        val db = createTestDatabase()
        val repo = FleetRepositoryImpl(FakeFleetApi(), db, RefreshScheduler(backgroundScope))
        repo.refreshMyShips()
        repo.clearAll()
        val result = repo.observeShips().first()
        assertEquals(emptyList(), result)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :spacetradersiosdk:testAndroidHostTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.data.repository.FleetRepositoryImplTest"`
Expected: FAIL — FleetRepository interface doesn't have `observeShips()`, `observeShip()`, `updateShipNav()`, etc.

- [ ] **Step 3: Rewrite FleetRepository interface**

Replace `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/repository/FleetRepository.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.repository

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Cooldown
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipCargo
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import kotlinx.coroutines.flow.Flow

interface FleetRepository {
    fun observeShips(): Flow<List<Ship>>
    fun observeShip(shipSymbol: String): Flow<Ship?>
    suspend fun refreshMyShips(page: Int = 1, limit: Int = 20)
    suspend fun refreshMyShip(shipSymbol: String)
    suspend fun saveShip(ship: Ship)
    suspend fun updateShipNav(shipSymbol: String, nav: ShipNav)
    suspend fun updateShipFuel(shipSymbol: String, fuel: ShipFuel)
    suspend fun updateShipCargo(shipSymbol: String, cargo: ShipCargo)
    suspend fun updateShipCooldown(shipSymbol: String, cooldown: Cooldown)
    suspend fun clearAll()
}
```

- [ ] **Step 4: Rewrite FleetRepositoryImpl**

Replace `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/FleetRepositoryImpl.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.SpaceTradersDatabase
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.updateShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.upsertShip
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Cooldown
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipCargo
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
            ships.forEach { queries.upsertShip(it) }
        }
        ships.forEach { registerTimersForShip(it) }
    }

    override suspend fun refreshMyShip(shipSymbol: String) {
        val ship = fleetApi.getMyShip(shipSymbol).toDomain()
        queries.upsertShip(ship)
        registerTimersForShip(ship)
    }

    override suspend fun saveShip(ship: Ship) {
        queries.upsertShip(ship)
    }

    override suspend fun updateShipNav(shipSymbol: String, nav: ShipNav) {
        queries.updateShipNav(nav, shipSymbol)
    }

    override suspend fun updateShipFuel(shipSymbol: String, fuel: ShipFuel) {
        queries.updateShipFuel(
            fuel_current = fuel.current.toLong(),
            fuel_capacity = fuel.capacity.toLong(),
            symbol = shipSymbol
        )
    }

    override suspend fun updateShipCargo(shipSymbol: String, cargo: ShipCargo) {
        queries.updateShipCargo(
            cargo_units = cargo.units.toLong(),
            cargo_capacity = cargo.capacity.toLong(),
            symbol = shipSymbol
        )
    }

    override suspend fun updateShipCooldown(shipSymbol: String, cooldown: Cooldown) {
        queries.updateShipCooldown(
            cooldown_total_seconds = cooldown.totalSeconds.toLong(),
            cooldown_remaining_seconds = cooldown.remainingSeconds.toLong(),
            cooldown_expiration = cooldown.expiration?.toString(),
            symbol = shipSymbol
        )
    }

    override suspend fun clearAll() {
        queries.deleteAllShips()
    }

    private fun registerTimersForShip(ship: Ship) {
        if (ship.nav.status == ShipNavStatus.IN_TRANSIT) {
            refreshScheduler.schedule(
                id = "transit:${ship.symbol}",
                expiresAt = ship.nav.route.arrivalTime,
                action = { refreshMyShip(ship.symbol) }
            )
        }
        ship.cooldown.expiration?.let { expiry ->
            refreshScheduler.schedule(
                id = "cooldown:${ship.symbol}",
                expiresAt = expiry,
                action = { refreshMyShip(ship.symbol) }
            )
        }
    }
}
```

- [ ] **Step 5: Rewrite FleetRepositoryImplWriteThroughTest**

Replace `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/FleetRepositoryImplWriteThroughTest.kt` with tests that verify timer registration using the new DB-backed repo. The tests follow the same structure but use `createTestDatabase()` instead of `FleetStateStore()`, and verify via `observeShip().first()` instead of `store.entities.value`.

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.AgentDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.CooldownDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.NavigateResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MarketTransactionDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MetaDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.PaginatedResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.RefuelResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipCargoDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipFrameSummaryDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipFuelDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavRouteDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavRouteWaypointDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipRegistrationDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.testing.createTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun transitShipDto(symbol: String = "LADD-1") = ShipDto(
    symbol = symbol,
    registration = ShipRegistrationDto(name = symbol, factionSymbol = "COSMIC", role = "COMMAND"),
    frame = ShipFrameSummaryDto(symbol = "FRAME_SHUTTLE", name = "Shuttle Frame"),
    nav = ShipNavDto(
        systemSymbol = "X1-DF55", waypointSymbol = "X1-DF55-20250Z",
        route = ShipNavRouteDto(
            destination = ShipNavRouteWaypointDto("X1-DF55-30A", "PLANET", "X1-DF55", 10, 20),
            origin = ShipNavRouteWaypointDto("X1-DF55-20250Z", "MOON", "X1-DF55", 0, 0),
            departureTime = "2099-01-01T00:00:00.000Z",
            arrival = "2099-01-01T01:00:00.000Z"
        ),
        status = "IN_TRANSIT", flightMode = "CRUISE"
    ),
    cargo = ShipCargoDto(capacity = 40, units = 0),
    fuel = ShipFuelDto(current = 400, capacity = 400),
    cooldown = CooldownDto(shipSymbol = symbol, totalSeconds = 0, remainingSeconds = 0)
)

private fun dockedShipDto(symbol: String = "LADD-2") = ShipDto(
    symbol = symbol,
    registration = ShipRegistrationDto(name = symbol, factionSymbol = "COSMIC", role = "COMMAND"),
    frame = ShipFrameSummaryDto(symbol = "FRAME_SHUTTLE", name = "Shuttle Frame"),
    nav = ShipNavDto(
        systemSymbol = "X1-DF55", waypointSymbol = "X1-DF55-20250Z",
        route = ShipNavRouteDto(
            destination = ShipNavRouteWaypointDto("X1-DF55-20250Z", "MOON", "X1-DF55", 0, 0),
            origin = ShipNavRouteWaypointDto("X1-DF55-20250Z", "MOON", "X1-DF55", 0, 0),
            departureTime = "2025-06-01T10:00:00.000Z",
            arrival = "2025-06-01T10:00:00.000Z"
        ),
        status = "DOCKED", flightMode = "CRUISE"
    ),
    cargo = ShipCargoDto(capacity = 40, units = 0),
    fuel = ShipFuelDto(current = 400, capacity = 400),
    cooldown = CooldownDto(shipSymbol = symbol, totalSeconds = 0, remainingSeconds = 0)
)

private class FakeWriteThroughFleetApi(
    private val ships: List<ShipDto> = emptyList(),
    private val singleShip: ShipDto = dockedShipDto()
) : FleetApi {
    override suspend fun getMyShips(page: Int, limit: Int) =
        PaginatedResponse(data = ships, meta = MetaDto(total = ships.size, page = page, limit = limit))
    override suspend fun getMyShip(shipSymbol: String) = singleShip
    override suspend fun orbitShip(shipSymbol: String) = singleShip.nav
    override suspend fun dockShip(shipSymbol: String) = singleShip.nav
    override suspend fun refuelShip(shipSymbol: String) = RefuelResponseDto(
        agent = AgentDto(null, "LADD", "X1-DF55-20250Z", 100000L, "COSMIC", 2),
        fuel = ShipFuelDto(current = 400, capacity = 400),
        transaction = MarketTransactionDto("X1-DF55-20250Z", shipSymbol, "FUEL", "PURCHASE", 6, 75, 450, "2025-06-01T10:00:00.000Z")
    )
    override suspend fun navigateShip(shipSymbol: String, waypointSymbol: String) =
        NavigateResponseDto(nav = singleShip.nav, fuel = ShipFuelDto(current = 400, capacity = 400))
}

class FleetRepositoryImplWriteThroughTest {

    @Test
    fun refreshMyShipWritesToDb() = runTest {
        val db = createTestDatabase()
        val scheduler = RefreshScheduler(backgroundScope)
        val repo = FleetRepositoryImpl(FakeWriteThroughFleetApi(singleShip = dockedShipDto("LADD-2")), db, scheduler)
        repo.refreshMyShip("LADD-2")
        val result = repo.observeShip("LADD-2").first()
        assertEquals("LADD-2", result?.symbol)
    }

    @Test
    fun refreshMyShipsWritesAllToDb() = runTest {
        val db = createTestDatabase()
        val scheduler = RefreshScheduler(backgroundScope)
        val ships = listOf(dockedShipDto("LADD-1"), dockedShipDto("LADD-2"))
        val repo = FleetRepositoryImpl(FakeWriteThroughFleetApi(ships = ships), db, scheduler)
        repo.refreshMyShips()
        val result = repo.observeShips().first()
        assertEquals(2, result.size)
    }

    @Test
    fun refreshMyShipRegistersTransitTimer() = runTest {
        val db = createTestDatabase()
        val scheduler = RefreshScheduler(backgroundScope)
        val repo = FleetRepositoryImpl(FakeWriteThroughFleetApi(singleShip = transitShipDto("LADD-1")), db, scheduler)
        repo.refreshMyShip("LADD-1")
        assertTrue(scheduler.activeTimers.value.containsKey("transit:LADD-1"))
    }

    @Test
    fun refreshMyShipDoesNotRegisterTimerForDockedShip() = runTest {
        val db = createTestDatabase()
        val scheduler = RefreshScheduler(backgroundScope)
        val repo = FleetRepositoryImpl(FakeWriteThroughFleetApi(singleShip = dockedShipDto()), db, scheduler)
        repo.refreshMyShip("LADD-2")
        assertTrue(scheduler.activeTimers.value.isEmpty())
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `gradlew.bat :spacetradersiosdk:testAndroidHostTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.data.repository.FleetRepositoryImplTest" --tests "com.brokenhuskysledteam.spacetradersio.sdk.data.repository.FleetRepositoryImplWriteThroughTest"`
Expected: All tests PASS.

- [ ] **Step 7: Commit**

```bash
git add spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/repository/FleetRepository.kt
git add spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/FleetRepositoryImpl.kt
git add spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/FleetRepositoryImplTest.kt
git add spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/FleetRepositoryImplWriteThroughTest.kt
git commit -m "feat: rewrite FleetRepository with SQLDelight-backed observe and refresh"
```

---

## Task 8: Use Case Migration — Replace State Stores with Repositories

**Files:**
- Modify: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/DockShipUseCase.kt`
- Modify: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/OrbitShipUseCase.kt`
- Modify: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/RefuelShipUseCase.kt`
- Modify: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/NavigateShipUseCase.kt`
- Modify: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/DockShipUseCaseTest.kt`
- Modify: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/OrbitShipUseCaseTest.kt`
- Modify: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/RefuelShipUseCaseTest.kt`
- Modify: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/NavigateShipUseCaseTest.kt`

This task updates all four use cases. Each use case follows the same pattern: replace `FleetStateStore` (and `AgentStateStore` for refuel) with `FleetRepository` (and `AgentRepository` for refuel). Tests verify DB state via `observeShip().first()` instead of `store.entities.value`.

- [ ] **Step 1: Update DockShipUseCase**

Replace the contents of `DockShipUseCase.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository

interface DockShipUseCase {
    suspend operator fun invoke(shipSymbol: String): ShipNav
}

class DockShipUseCaseImpl(
    private val fleetApi: FleetApi,
    private val fleetRepository: FleetRepository
) : DockShipUseCase {
    override suspend operator fun invoke(shipSymbol: String): ShipNav {
        val nav = fleetApi.dockShip(shipSymbol).toDomain()
        fleetRepository.updateShipNav(shipSymbol, nav)
        return nav
    }
}
```

- [ ] **Step 2: Update OrbitShipUseCase**

Replace the contents of `OrbitShipUseCase.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository

interface OrbitShipUseCase {
    suspend operator fun invoke(shipSymbol: String): ShipNav
}

class OrbitShipUseCaseImpl(
    private val fleetApi: FleetApi,
    private val fleetRepository: FleetRepository
) : OrbitShipUseCase {
    override suspend operator fun invoke(shipSymbol: String): ShipNav {
        val nav = fleetApi.orbitShip(shipSymbol).toDomain()
        fleetRepository.updateShipNav(shipSymbol, nav)
        return nav
    }
}
```

- [ ] **Step 3: Update RefuelShipUseCase**

Replace the contents of `RefuelShipUseCase.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.RefuelResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.AgentRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository

interface RefuelShipUseCase {
    suspend operator fun invoke(shipSymbol: String): RefuelResult
}

class RefuelShipUseCaseImpl(
    private val fleetApi: FleetApi,
    private val fleetRepository: FleetRepository,
    private val agentRepository: AgentRepository
) : RefuelShipUseCase {
    override suspend operator fun invoke(shipSymbol: String): RefuelResult {
        val response = fleetApi.refuelShip(shipSymbol)
        val result = RefuelResult(
            agent = response.agent.toDomain(),
            fuel = ShipFuel(current = response.fuel.current, capacity = response.fuel.capacity),
            transaction = response.transaction.toDomain()
        )
        fleetRepository.updateShipFuel(shipSymbol, result.fuel)
        agentRepository.saveAgent(result.agent)
        return result
    }
}
```

- [ ] **Step 4: Update NavigateShipUseCase**

Replace the contents of `NavigateShipUseCase.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.NavigateResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import kotlinx.coroutines.flow.first

interface NavigateShipUseCase {
    suspend operator fun invoke(shipSymbol: String, waypointSymbol: String): NavigateResult
}

class NavigateShipUseCaseImpl(
    private val fleetApi: FleetApi,
    private val fleetRepository: FleetRepository,
    private val orbitShipUseCase: OrbitShipUseCase
) : NavigateShipUseCase {

    override suspend operator fun invoke(
        shipSymbol: String,
        waypointSymbol: String
    ): NavigateResult {
        val ship = fleetRepository.observeShip(shipSymbol).first()
        if (ship?.nav?.status == ShipNavStatus.DOCKED) {
            orbitShipUseCase(shipSymbol)
        }

        val response = fleetApi.navigateShip(shipSymbol, waypointSymbol).toDomain()

        fleetRepository.updateShipNav(shipSymbol, response.nav)
        fleetRepository.updateShipFuel(shipSymbol, response.fuel)

        return response
    }
}
```

- [ ] **Step 5: Update all four use case test files**

Each test file needs to replace `FleetStateStore` (and `AgentStateStore`) with a real in-memory database + repository. The tests verify DB state via `repo.observeShip().first()` instead of `store.entities.value`. Update each test file to:

1. Import `createTestDatabase`, `FleetRepositoryImpl`, `RefreshScheduler`
2. Replace `FleetStateStore()` with `createTestDatabase()` + `FleetRepositoryImpl(...)`
3. Pre-seed test ships via `repo.saveShip(testShip)` instead of `store.put(...)`
4. Assert DB state via `repo.observeShip("LADD-1").first()` instead of `store.entities.value["LADD-1"]`

For `RefuelShipUseCaseTest`: additionally import `AgentRepositoryImpl` and verify agent credits via `agentRepo.observeAgent().first()`.

For `NavigateShipUseCaseTest`: the `fakeOrbit` now calls `fleetRepository.updateShipNav()` instead of `store.update()`.

NOTE: The full test code follows the same structure as the existing tests but with the FleetStateStore replaced. The implementer should use the existing test files as templates and swap the state store for the repository.

- [ ] **Step 6: Run all use case tests**

Run: `gradlew.bat :spacetradersiosdk:testAndroidHostTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.*"`
Expected: All tests PASS.

- [ ] **Step 7: Commit**

```bash
git add spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/
git add spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/
git commit -m "refactor: migrate use cases from FleetStateStore to FleetRepository"
```

---

## Task 9: Session and State Store Cleanup

**Files:**
- Modify: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/session/SpaceTradersSession.kt`
- Modify: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/session/SpaceTradersSessionImpl.kt`
- Modify: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/session/SessionManagerImpl.kt`
- Modify: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/session/SessionManagerTest.kt`
- Delete: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/state/FleetStateStore.kt`
- Delete: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/state/AgentStateStore.kt`
- Delete: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/state/AgentStateStoreTest.kt`

- [ ] **Step 1: Update SpaceTradersSession interface**

Remove `fleetStateStore` and `agentStateStore` properties. Add `database` property for logout wipe:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.session

import com.brokenhuskysledteam.spacetradersio.sdk.data.db.SpaceTradersDatabase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.ContractStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.WaypointStateStore

interface SpaceTradersSession {
    val refreshScheduler: RefreshScheduler
    val contractStateStore: ContractStateStore
    val waypointStateStore: WaypointStateStore
    val database: SpaceTradersDatabase
    val isActive: Boolean
    fun onResume()
    fun destroy()
}
```

- [ ] **Step 2: Update SpaceTradersSessionImpl**

Remove FleetStateStore and AgentStateStore instantiation. Accept database via constructor. Clear DB tables in `destroy()`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.session

import com.brokenhuskysledteam.spacetradersio.sdk.data.db.SpaceTradersDatabase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.ContractStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.WaypointStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive

class SpaceTradersSessionImpl(
    private val scope: CoroutineScope,
    override val database: SpaceTradersDatabase
) : SpaceTradersSession {

    override val refreshScheduler = RefreshScheduler(scope)
    override val contractStateStore = ContractStateStore()
    override val waypointStateStore = WaypointStateStore()

    override val isActive: Boolean get() = scope.isActive

    override fun onResume() {
        refreshScheduler.onResume()
    }

    override fun destroy() {
        scope.cancel()
        database.shipQueries.deleteAllShips()
        database.agentQueries.deleteAll()
    }
}
```

- [ ] **Step 3: Update SessionManagerImpl to pass database**

`SessionManagerImpl` now accepts a `SpaceTradersDatabase` and passes it to `SpaceTradersSessionImpl`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.session

import com.brokenhuskysledteam.spacetradersio.sdk.data.db.SpaceTradersDatabase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SessionManagerImpl(
    private val tokenRepository: TokenRepository,
    private val database: SpaceTradersDatabase
) : SessionManager {

    @Volatile
    private var _session: SpaceTradersSession? = null

    override fun requireSession(): SpaceTradersSession =
        _session ?: error("No active session. User must be authenticated.")

    override fun login(token: String) {
        tokenRepository.saveToken(token)
        _session = SpaceTradersSessionImpl(
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
            database
        )
    }

    override fun logout() {
        _session?.destroy()
        _session = null
        tokenRepository.clearToken()
    }

    override fun restoreIfAuthenticated() {
        if (tokenRepository.hasToken() && _session == null) {
            _session = SpaceTradersSessionImpl(
                CoroutineScope(SupervisorJob() + Dispatchers.Default),
                database
            )
        }
    }
}
```

- [ ] **Step 4: Update SessionManagerTest**

Update `SessionManagerTest` to pass a test database to `SessionManagerImpl`. Remove the `sessionContainsAllStores` test's assertions for `fleetStateStore` and `agentStateStore`. Add a test that `logout` clears DB tables:

Key changes:
- All `SessionManagerImpl(tokenRepo)` calls become `SessionManagerImpl(tokenRepo, createTestDatabase())`
- The `sessionContainsAllStores` test checks `contractStateStore`, `waypointStateStore`, `refreshScheduler`, and `database` but NOT `fleetStateStore` or `agentStateStore`
- Add: test that after `login` + inserting a ship + `logout`, the ship table is empty

- [ ] **Step 5: Delete FleetStateStore, AgentStateStore, and AgentStateStoreTest**

```bash
git rm spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/state/FleetStateStore.kt
git rm spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/state/AgentStateStore.kt
git rm spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/state/AgentStateStoreTest.kt
```

- [ ] **Step 6: Run all SDK tests to verify nothing is broken**

Run: `gradlew.bat :spacetradersiosdk:allTests`
Expected: All tests PASS. No references to deleted state stores remain in the codebase.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: remove FleetStateStore/AgentStateStore, wire database through session"
```

---

## Task 10: App DI and ViewModel Migration

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/di/SdkModule.kt`
- Modify: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/ships/ShipListViewModel.kt`
- Modify: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/ships/ShipDetailViewModel.kt`
- Modify: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/dashboard/DashboardViewModel.kt`
- Modify: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/systemmap/SystemMapViewModel.kt`

- [ ] **Step 1: Add SQLDelight Android driver to app dependencies**

In `app/build.gradle.kts`, add to the `dependencies` block:

```kotlin
implementation(libs.sqldelight.android.driver)
```

- [ ] **Step 2: Rewrite SdkModule DI**

Update `SdkModule.kt`:
- Add `provideSpaceTradersDatabase` — singleton, takes `@ApplicationContext context: Context`, creates `SqlDriverFactory(context).create()` -> `SpaceTradersDatabase(driver)`
- Update `provideSessionManager` to accept `SpaceTradersDatabase` parameter
- Remove `provideFleetStateStore` and `provideAgentStateStore`
- Add `provideAgentRepository` — takes `AgentsApi`, `SpaceTradersDatabase`
- Update `provideFleetRepository` to take `FleetApi`, `SpaceTradersDatabase`, `RefreshScheduler`
- Update use case providers: `DockShipUseCaseImpl(fleetApi, fleetRepository)`, `OrbitShipUseCaseImpl(fleetApi, fleetRepository)`, `RefuelShipUseCaseImpl(fleetApi, fleetRepository, agentRepository)`, `NavigateShipUseCaseImpl(fleetApi, fleetRepository, orbitShipUseCase)`

- [ ] **Step 3: Rewrite ShipListViewModel**

Replace `FleetStateStore` with `FleetRepository`:

```kotlin
@HiltViewModel
class ShipListViewModel @Inject constructor(
    private val fleetRepository: FleetRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ShipListUiState> = combine(
        fleetRepository.observeShips(),
        _isLoading,
        _error
    ) { ships, isLoading, error ->
        ShipListUiState(
            ships = ships.map { it.toSummary() },
            isLoading = isLoading,
            error = error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ShipListUiState())

    // ...navigation event unchanged...

    init {
        loadShips()
    }

    // ...onEvent unchanged...

    private fun loadShips() {
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                fleetRepository.refreshMyShips()
                _isLoading.value = false
            } catch (e: Exception) {
                _isLoading.value = false
                _error.value = e.message ?: "Failed to load ships"
            }
        }
    }
}
```

Key change: `init` always calls `loadShips()` — no more `if (fleetStateStore.entities.value.isEmpty())` guard. The Flow from the DB handles showing cached data immediately.

- [ ] **Step 4: Rewrite ShipDetailViewModel**

Replace `FleetStateStore` with `FleetRepository`:

```kotlin
@HiltViewModel
class ShipDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val fleetRepository: FleetRepository,
    private val orbitShipUseCase: OrbitShipUseCase,
    private val dockShipUseCase: DockShipUseCase,
    private val refuelShipUseCase: RefuelShipUseCase
) : ViewModel() {

    private val shipSymbol: String = checkNotNull(savedStateHandle["shipSymbol"])
    private val _localState = MutableStateFlow(LocalState())

    val uiState: StateFlow<ShipDetailUiState> = combine(
        fleetRepository.observeShip(shipSymbol),
        _localState
    ) { ship, local ->
        ShipDetailUiState(
            ship = ship?.toDetail(),
            isLoading = local.isLoading,
            isActionInProgress = local.isActionInProgress,
            actionResult = local.actionResult,
            error = local.error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ShipDetailUiState())

    init {
        loadShip()
    }

    // ...onEvent and performAction unchanged...

    private fun loadShip() {
        _localState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                fleetRepository.refreshMyShip(shipSymbol)
                _localState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _localState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load ship")
                }
            }
        }
    }

    // ...LocalState unchanged...
}
```

Key change: `loadShip()` calls `fleetRepository.refreshMyShip()` instead of `fleetRepository.getMyShip()`. The init always calls `loadShip()` — no more state store null check.

- [ ] **Step 5: Rewrite DashboardViewModel**

Replace `AgentsApi` + `AgentStateStore` with `AgentRepository`:

```kotlin
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val agentRepository: AgentRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DashboardUiState> = combine(
        agentRepository.observeAgent(),
        _isLoading,
        _error
    ) { agent, isLoading, error ->
        DashboardUiState(agent = agent, isLoading = isLoading, error = error)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DashboardUiState())

    // ...navigation event unchanged...

    init {
        loadAgent()
    }

    // ...onEvent unchanged...

    private fun loadAgent() {
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                agentRepository.refreshAgent()
                _isLoading.value = false
            } catch (e: SpaceTradersApiException) {
                when (e.error) {
                    is SpaceTradersError.AuthError -> {
                        sessionManager.logout()
                        _navigationEvent.send(NavigationTarget.Auth)
                    }
                    else -> {
                        _isLoading.value = false
                        _error.value = e.message
                    }
                }
            } catch (e: Exception) {
                _isLoading.value = false
                _error.value = e.message ?: "Failed to load agent"
            }
        }
    }

    // ...logout unchanged...
}
```

- [ ] **Step 6: Update SystemMapViewModel**

Replace `FleetStateStore` with `FleetRepository.observeShips()`:

In the `combine`, replace `fleetStateStore.entities` with `fleetRepository.observeShips()`. The `.value` on `fleetStateStore.entities` used in `navigate()` for `fuelBefore` needs to change to use the last-known value from the Flow (capture it from the `uiState` or read from the DB).

Key changes in the constructor:
```kotlin
class SystemMapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val systemRepository: SystemRepository,
    private val fleetRepository: FleetRepository,
    private val waypointStateStore: WaypointStateStore,
    private val navigateShipUseCase: NavigateShipUseCase
) : ViewModel() {
```

In the combine:
```kotlin
val uiState: StateFlow<SystemMapUiState> = combine(
    waypointStateStore.entities,
    fleetRepository.observeShips(),
    _localState
) { waypointMap, ships, local ->
    val fleetMap = ships.associateBy { it.symbol }
    // ...rest of combine logic uses fleetMap instead of fleetMap from state store...
}
```

In `navigate()`, replace `fleetStateStore.entities.value[ship]?.fuel?.current` with `fleetRepository.observeShip(ship).first()?.fuel?.current`.

- [ ] **Step 7: Build the app to verify compilation**

Run: `gradlew.bat :app:compileDebugSources`
Expected: Compiles without errors.

- [ ] **Step 8: Commit**

```bash
git add app/build.gradle.kts
git add app/src/main/java/com/brokenhuskysledteam/spacetradersio/di/SdkModule.kt
git add app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/ships/ShipListViewModel.kt
git add app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/ships/ShipDetailViewModel.kt
git add app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/dashboard/DashboardViewModel.kt
git add app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/systemmap/SystemMapViewModel.kt
git commit -m "refactor: migrate ViewModels and DI from state stores to SQLDelight repositories"
```

---

## Task 11: App ViewModel Test Migration

**Files:**
- Modify: `app/src/test/java/com/brokenhuskysledteam/spacetradersio/ui/ships/ShipListViewModelTest.kt`
- Modify: `app/src/test/java/com/brokenhuskysledteam/spacetradersio/ui/ships/ShipDetailViewModelTest.kt`
- Modify: `app/src/test/java/com/brokenhuskysledteam/spacetradersio/ui/dashboard/DashboardViewModelTest.kt`
- Modify: `app/src/test/java/com/brokenhuskysledteam/spacetradersio/ui/systemmap/SystemMapViewModelTest.kt`

Each ViewModel test file needs a new `FakeFleetRepository` (or `FakeAgentRepository`) that implements the new interface using `MutableStateFlow`s. The fakes are simpler than before — just expose Flows.

- [ ] **Step 1: Create new FakeFleetRepository for ViewModel tests**

The pattern for all ship-related ViewModel tests:

```kotlin
private class FakeFleetRepository(
    ships: List<Ship> = emptyList(),
    var refreshException: Exception? = null
) : FleetRepository {
    private val _ships = MutableStateFlow(ships)
    private val _shipMap = MutableStateFlow(ships.associateBy { it.symbol })

    override fun observeShips(): Flow<List<Ship>> = _ships
    override fun observeShip(shipSymbol: String): Flow<Ship?> =
        _shipMap.map { it[shipSymbol] }
    override suspend fun refreshMyShips(page: Int, limit: Int) {
        refreshException?.let { throw it }
    }
    override suspend fun refreshMyShip(shipSymbol: String) {
        refreshException?.let { throw it }
    }
    override suspend fun saveShip(ship: Ship) {
        _shipMap.update { it + (ship.symbol to ship) }
        _ships.update { _shipMap.value.values.toList() }
    }
    override suspend fun updateShipNav(shipSymbol: String, nav: ShipNav) {}
    override suspend fun updateShipFuel(shipSymbol: String, fuel: ShipFuel) {}
    override suspend fun updateShipCargo(shipSymbol: String, cargo: ShipCargo) {}
    override suspend fun updateShipCooldown(shipSymbol: String, cooldown: Cooldown) {}
    override suspend fun clearAll() {
        _ships.value = emptyList()
        _shipMap.value = emptyMap()
    }

    fun emitShips(ships: List<Ship>) {
        _ships.value = ships
        _shipMap.value = ships.associateBy { it.symbol }
    }
}
```

- [ ] **Step 2: Update ShipListViewModelTest**

Replace `FakeFleetRepository` + `FleetStateStore` with the new `FakeFleetRepository`. Key changes:
- `createViewModel()` takes `FakeFleetRepository` only (no store)
- To simulate "cached data on init", call `fakeRepo.emitShips(...)` before creating the VM
- The old `storeAlreadyPopulated_doesNotCallRefreshMyShips` test changes: now the VM always calls refresh regardless of cached data (init always calls `loadShips()`). The test should verify that cached data appears in the UI immediately, and the refresh call succeeds in parallel.

- [ ] **Step 3: Update ShipDetailViewModelTest**

Replace `FleetStateStore` with `FakeFleetRepository.observeShip()`. The VM constructor no longer takes a state store.

- [ ] **Step 4: Update DashboardViewModelTest**

Create a `FakeAgentRepository`:

```kotlin
private class FakeAgentRepository(
    var refreshException: Exception? = null,
    var agentToEmit: Agent? = null
) : AgentRepository {
    private val _agent = MutableStateFlow<Agent?>(null)
    override fun observeAgent(): Flow<Agent?> = _agent
    override suspend fun refreshAgent() {
        refreshException?.let { throw it }
        _agent.value = agentToEmit
    }
    override suspend fun saveAgent(agent: Agent) { _agent.value = agent }
    override suspend fun updateCredits(symbol: String, credits: Long) {
        _agent.update { it?.copy(credits = credits) }
    }
    override suspend fun clearAll() { _agent.value = null }
}
```

Update the ViewModel constructor to take `AgentRepository` + `SessionManager` (not `AgentsApi` + `AgentStateStore` + `SessionManager`).

- [ ] **Step 5: Update SystemMapViewModelTest**

Replace `FleetStateStore` with the new `FakeFleetRepository`. Update `navigate()` test assertions accordingly.

- [ ] **Step 6: Run all app tests**

Run: `gradlew.bat :app:testDebugUnitTest`
Expected: All tests PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/test/
git commit -m "test: migrate ViewModel tests from state stores to fake repositories"
```

---

## Task 12: Full Test Suite Verification

- [ ] **Step 1: Run all SDK tests**

Run: `gradlew.bat :spacetradersiosdk:allTests`
Expected: All tests PASS.

- [ ] **Step 2: Run all app tests**

Run: `gradlew.bat :app:testDebugUnitTest`
Expected: All tests PASS.

- [ ] **Step 3: Build the debug APK**

Run: `gradlew.bat :app:assembleDebug`
Expected: APK builds successfully.

- [ ] **Step 4: Verify no references to deleted state stores remain**

Search the codebase for `FleetStateStore` and `AgentStateStore` — only `EntityStateStore.kt`, `EntityStateStoreTest.kt`, `ContractStateStore`, and `WaypointStateStore` should remain.

- [ ] **Step 5: Commit (if any fixes were needed)**

Only if test failures required fixes in previous steps.

---

## Task 13: Documentation Updates

**Files:**
- Modify: `CLAUDE.md`
- Modify: `docs/ARCHITECTURE_REFERENCE_HUMAN.md`
- Modify: `docs/ARCHITECTURE_REFERENCE_AI.md`
- Modify: `README.md`

- [ ] **Step 1: Update CLAUDE.md**

Add to Key Dependencies table:
```
| SQLDelight | 2.0.2 |
```

Add to Architecture section — new subsection for data layer:
- SQLDelight is the persistent cache for Ship and Agent entities
- Repositories expose `Flow<T>` backed by SQLDelight queries
- Read pattern: Cache-Then-Network (observe DB, refresh from API)
- Write pattern: Network-First (API call, then DB update)
- Contract and Waypoint still use in-memory EntityStateStore (migration pending)

Add to Testing section:
- SDK tests use `JdbcSqliteDriver(IN_MEMORY)` via `createTestDatabase()` helper
- ViewModel tests use fake repositories with `MutableStateFlow` — no DB or MockEngine needed

Add to Gotchas:
- SQLDelight `INTEGER` columns map to `Long` in Kotlin — use `.toInt()` / `.toLong()` when converting to/from domain models with `Int` fields
- SQLDelight generated class names depend on the table name in the `.sq` file — check `build/generated/sqldelight/` if imports don't resolve
- `SqlDriverFactory` on Android requires `Context` — the `:app` module provides it via Hilt `@ApplicationContext`

- [ ] **Step 2: Update ARCHITECTURE_REFERENCE_HUMAN.md**

Add a new section on the offline-first data strategy with narrative explanation, Mermaid diagram showing the data flow, and gotcha callouts.

- [ ] **Step 3: Update ARCHITECTURE_REFERENCE_AI.md**

Add rules bullets for the offline-first pattern:
- Repositories are the ONLY writers to SQLDelight — never write from ViewModels or use cases directly
- Read operations return `Flow<T>` from SQLDelight, never suspend functions that return `T`
- Write/action operations call API first, then write to DB on success
- Logout clears all DB tables via `SpaceTradersSession.destroy()`
- Test code uses `createTestDatabase()` from `testing/TestDatabase.kt`

- [ ] **Step 4: Update README.md**

Add SQLDelight to the technology list and briefly describe the offline-first caching strategy.

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md docs/ARCHITECTURE_REFERENCE_HUMAN.md docs/ARCHITECTURE_REFERENCE_AI.md README.md
git commit -m "docs: update architecture docs for SQLDelight offline-first cache"
```
