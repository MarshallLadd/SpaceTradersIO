# Unified State Management & Smart Refresh System — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the fragmented per-screen state with a single source of truth using per-domain state stores, write-through repositories, a centralized timer-based refresh scheduler, and auth-scoped session management — so all screens react instantly to any state change.

**Architecture:** Per-domain `EntityStateStore<K,T>` classes hold reactive `StateFlow<Map<K,T>>` caches in the SDK's `commonMain`. Repositories become write-through: every API response merges into the store before returning. A `RefreshScheduler` manages a priority queue of timed events (transit arrivals, cooldowns, contract deadlines) and fires targeted single-entity refreshes 1 second after expiry. All stores and the scheduler live inside an auth-scoped `SpaceTradersSession`, created on login and destroyed on logout. ViewModels observe store flows instead of fetching independently.

**Tech Stack:** Kotlin Multiplatform (`commonMain`), `kotlinx.coroutines` (`StateFlow`, `MutableStateFlow`, `CoroutineScope`, `SupervisorJob`), `kotlin.time.Instant`/`Clock.System`, Hilt (Android DI), `ProcessLifecycleOwner` (lifecycle bridge), `kotlin.test` + `kotlinx-coroutines-test` (testing).

---

## File Structure

### New Files (SDK `commonMain`)

| File | Responsibility |
|---|---|
| `sdk/domain/state/EntityStateStore.kt` | Generic thread-safe reactive store base class |
| `sdk/domain/state/FleetStateStore.kt` | Type alias / subclass for `EntityStateStore<String, Ship>` |
| `sdk/domain/state/AgentStateStore.kt` | Single-entity store for the authenticated agent |
| `sdk/domain/state/ContractStateStore.kt` | Type alias / subclass for `EntityStateStore<String, Contract>` |
| `sdk/domain/scheduler/ScheduledRefresh.kt` | Data class for timer entries |
| `sdk/domain/scheduler/RefreshScheduler.kt` | Centralized timer engine with priority-queue loop |
| `sdk/domain/session/SpaceTradersSession.kt` | Auth-scoped container for all stores + scheduler |
| `sdk/domain/session/SessionManager.kt` | Session lifecycle: login, logout, restore |

### New Files (SDK `androidHostTest`)

| File | Responsibility |
|---|---|
| `sdk/domain/state/EntityStateStoreTest.kt` | Tests for generic store operations |
| `sdk/domain/state/AgentStateStoreTest.kt` | Tests for single-entity agent store |
| `sdk/domain/scheduler/RefreshSchedulerTest.kt` | Tests for timer scheduling, expiry, resume |
| `sdk/domain/session/SessionManagerTest.kt` | Tests for login/logout/restore lifecycle |
| `sdk/data/repository/FleetRepositoryImplWriteThroughTest.kt` | Tests for write-through + timer registration |

### New Files (App)

| File | Responsibility |
|---|---|
| `app/.../AppLifecycleObserver.kt` | Bridges `ProcessLifecycleOwner` to `SpaceTradersSession.onResume()` |

### Modified Files

| File | Change Summary |
|---|---|
| `sdk/domain/repository/FleetRepository.kt` | Add `refreshMyShips()` method |
| `sdk/data/repository/FleetRepositoryImpl.kt` | Accept stores + scheduler; write-through; register timers |
| `sdk/domain/usecase/OrbitShipUseCase.kt` | Accept `FleetStateStore`; update store after action |
| `sdk/domain/usecase/DockShipUseCase.kt` | Accept `FleetStateStore`; update store after action |
| `sdk/domain/usecase/RefuelShipUseCase.kt` | Accept `FleetStateStore` + `AgentStateStore`; update stores |
| `sdk/domain/usecase/RegisterAgentUseCase.kt` | Accept `SessionManager`; call `login()` instead of raw `saveToken()` |
| `app/.../di/SdkModule.kt` | Add providers for `SessionManager`, stores, scheduler; rewire repos + use cases |
| `app/.../SpaceTradersApplication.kt` | Register `AppLifecycleObserver`; call `restoreIfAuthenticated()` |
| `app/.../ui/ships/ShipListViewModel.kt` | Observe `FleetStateStore` instead of direct fetch |
| `app/.../ui/ships/ShipDetailViewModel.kt` | Observe `FleetStateStore.observe(symbol)` instead of direct fetch |
| `app/.../ui/dashboard/DashboardViewModel.kt` | Observe `AgentStateStore`; use `SessionManager` for logout |
| `app/.../ui/auth/AuthViewModel.kt` | Use `SessionManager.login()` for token import |

### Paths Reference

All SDK source files are rooted at:
```
spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/
```

All SDK test files are rooted at:
```
spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/
```

All app source files are rooted at:
```
app/src/main/java/com/brokenhuskysledteam/spacetradersio/
```

---

## Task 1: EntityStateStore — Generic Reactive Store

**Files:**
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/state/EntityStateStore.kt`
- Test: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/state/EntityStateStoreTest.kt`

- [ ] **Step 1: Write the failing tests**

Create the test file:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.state

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntityStateStoreTest {

    private fun createStore() = EntityStateStore<String, String>()

    @Test
    fun initialStateIsEmpty() {
        val store = createStore()
        assertTrue(store.entities.value.isEmpty())
    }

    @Test
    fun putAddsEntity() = runTest {
        val store = createStore()
        store.put("key1", "value1")
        assertEquals("value1", store.entities.value["key1"])
    }

    @Test
    fun putReplacesExistingEntity() = runTest {
        val store = createStore()
        store.put("key1", "value1")
        store.put("key1", "value2")
        assertEquals("value2", store.entities.value["key1"])
    }

    @Test
    fun putAllMergesEntities() = runTest {
        val store = createStore()
        store.put("existing", "stays")
        store.putAll(mapOf("a" to "1", "b" to "2"))
        assertEquals(3, store.entities.value.size)
        assertEquals("stays", store.entities.value["existing"])
        assertEquals("1", store.entities.value["a"])
    }

    @Test
    fun updateTransformsEntity() = runTest {
        val store = createStore()
        store.put("key1", "hello")
        store.update("key1") { it.uppercase() }
        assertEquals("HELLO", store.entities.value["key1"])
    }

    @Test
    fun updateIgnoresNonexistentKey() = runTest {
        val store = createStore()
        store.update("missing") { it.uppercase() }
        assertTrue(store.entities.value.isEmpty())
    }

    @Test
    fun removeDeletesEntity() = runTest {
        val store = createStore()
        store.put("key1", "value1")
        store.remove("key1")
        assertNull(store.entities.value["key1"])
    }

    @Test
    fun clearRemovesAllEntities() = runTest {
        val store = createStore()
        store.putAll(mapOf("a" to "1", "b" to "2"))
        store.clear()
        assertTrue(store.entities.value.isEmpty())
    }

    @Test
    fun observeEmitsValueForKey() = runTest {
        val store = createStore()
        store.put("key1", "value1")
        val observed = store.observe("key1").first()
        assertEquals("value1", observed)
    }

    @Test
    fun observeEmitsNullForMissingKey() = runTest {
        val store = createStore()
        val observed = store.observe("missing").first()
        assertNull(observed)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.domain.state.EntityStateStoreTest" -x compileTestKotlinIosArm64 -x compileTestKotlinIosSimulatorArm64`
Expected: Compilation failure — `EntityStateStore` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

Create the source file:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.state

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

open class EntityStateStore<K, T> {

    private val _entities = MutableStateFlow<Map<K, T>>(emptyMap())
    val entities: StateFlow<Map<K, T>> = _entities.asStateFlow()

    fun observe(key: K): Flow<T?> =
        _entities.map { it[key] }.distinctUntilChanged()

    fun observeAll(): StateFlow<Map<K, T>> = entities

    fun put(key: K, entity: T) {
        _entities.update { it + (key to entity) }
    }

    fun putAll(entities: Map<K, T>) {
        _entities.update { it + entities }
    }

    fun update(key: K, transform: (T) -> T) {
        _entities.update { map ->
            val existing = map[key] ?: return@update map
            map + (key to transform(existing))
        }
    }

    fun remove(key: K) {
        _entities.update { it - key }
    }

    fun clear() {
        _entities.update { emptyMap() }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.domain.state.EntityStateStoreTest" -x compileTestKotlinIosArm64 -x compileTestKotlinIosSimulatorArm64`
Expected: All 10 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/state/EntityStateStore.kt spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/state/EntityStateStoreTest.kt
git commit -m "$(cat <<'EOF'
feat(sdk): add EntityStateStore generic reactive cache

Provides a thread-safe, StateFlow-backed key-value store with
per-key observation, merge, and surgical update operations.
Foundation for the unified state management system.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: FleetStateStore, AgentStateStore, ContractStateStore

**Files:**
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/state/FleetStateStore.kt`
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/state/AgentStateStore.kt`
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/state/ContractStateStore.kt`
- Test: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/state/AgentStateStoreTest.kt`

- [ ] **Step 1: Write the AgentStateStore test**

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.state

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentStateStoreTest {

    private fun testAgent(credits: Long = 100000L) = Agent(
        accountId = "acc-1",
        symbol = "LADD",
        headquarters = "X1-DF55-20250Z",
        credits = credits,
        startingFaction = "COSMIC",
        shipCount = 2
    )

    @Test
    fun initialAgentIsNull() {
        val store = AgentStateStore()
        assertNull(store.agent.value)
    }

    @Test
    fun updateSetsAgent() {
        val store = AgentStateStore()
        val agent = testAgent()
        store.update(agent)
        assertEquals(agent, store.agent.value)
    }

    @Test
    fun updateReplacesAgent() {
        val store = AgentStateStore()
        store.update(testAgent(credits = 100L))
        store.update(testAgent(credits = 200L))
        assertEquals(200L, store.agent.value?.credits)
    }

    @Test
    fun clearSetsAgentToNull() {
        val store = AgentStateStore()
        store.update(testAgent())
        store.clear()
        assertNull(store.agent.value)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.domain.state.AgentStateStoreTest" -x compileTestKotlinIosArm64 -x compileTestKotlinIosSimulatorArm64`
Expected: Compilation failure — `AgentStateStore` does not exist yet.

- [ ] **Step 3: Create all three store files**

**FleetStateStore.kt:**
```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.state

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship

class FleetStateStore : EntityStateStore<String, Ship>()
```

**AgentStateStore.kt:**
```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.state

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AgentStateStore {

    private val _agent = MutableStateFlow<Agent?>(null)
    val agent: StateFlow<Agent?> = _agent.asStateFlow()

    fun update(agent: Agent) {
        _agent.value = agent
    }

    fun clear() {
        _agent.value = null
    }
}
```

**ContractStateStore.kt:**
```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.state

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract

class ContractStateStore : EntityStateStore<String, Contract>()
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.domain.state.*" -x compileTestKotlinIosArm64 -x compileTestKotlinIosSimulatorArm64`
Expected: All EntityStateStoreTest + AgentStateStoreTest tests PASS.

- [ ] **Step 5: Commit**

```bash
git add spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/state/FleetStateStore.kt spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/state/AgentStateStore.kt spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/state/ContractStateStore.kt spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/state/AgentStateStoreTest.kt
git commit -m "$(cat <<'EOF'
feat(sdk): add domain-specific state stores

FleetStateStore (keyed by ship symbol), AgentStateStore (single entity),
and ContractStateStore (keyed by contract ID).

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: ScheduledRefresh Data Class + RefreshScheduler

**Files:**
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/scheduler/ScheduledRefresh.kt`
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/scheduler/RefreshScheduler.kt`
- Test: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/scheduler/RefreshSchedulerTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TestClock

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class RefreshSchedulerTest {

    private fun TestScope.createScheduler(): RefreshScheduler =
        RefreshScheduler(this)

    @Test
    fun scheduleAddsEntryToActiveTimers() = runTest {
        val scheduler = createScheduler()
        val expiresAt = Clock.System.now() + 60.seconds
        scheduler.schedule("transit:SHIP-1", expiresAt) { }
        assertEquals(1, scheduler.activeTimers.value.size)
        assertEquals("transit:SHIP-1", scheduler.activeTimers.value.keys.first())
    }

    @Test
    fun scheduleWithSameIdReplacesEntry() = runTest {
        val scheduler = createScheduler()
        val first = Clock.System.now() + 60.seconds
        val second = Clock.System.now() + 30.seconds
        scheduler.schedule("transit:SHIP-1", first) { }
        scheduler.schedule("transit:SHIP-1", second) { }
        assertEquals(1, scheduler.activeTimers.value.size)
        assertEquals(second, scheduler.activeTimers.value["transit:SHIP-1"]?.expiresAt)
    }

    @Test
    fun cancelRemovesEntry() = runTest {
        val scheduler = createScheduler()
        scheduler.schedule("transit:SHIP-1", Clock.System.now() + 60.seconds) { }
        scheduler.cancel("transit:SHIP-1")
        assertTrue(scheduler.activeTimers.value.isEmpty())
    }

    @Test
    fun cancelByPrefixRemovesMatchingEntries() = runTest {
        val scheduler = createScheduler()
        val future = Clock.System.now() + 60.seconds
        scheduler.schedule("transit:SHIP-1", future) { }
        scheduler.schedule("transit:SHIP-2", future) { }
        scheduler.schedule("cooldown:SHIP-1", future) { }
        scheduler.cancelByPrefix("transit:")
        assertEquals(1, scheduler.activeTimers.value.size)
        assertTrue(scheduler.activeTimers.value.containsKey("cooldown:SHIP-1"))
    }

    @Test
    fun timerFiresAfterExpiryPlusOneSecond() = runTest {
        val scheduler = createScheduler()
        var fired = false
        val expiresAt = Clock.System.now() + 10.seconds
        scheduler.schedule("transit:SHIP-1", expiresAt) { fired = true }

        advanceTimeBy(10_000) // At exactly expiry — should NOT have fired yet
        assertEquals(false, fired)

        advanceTimeBy(1_001) // 1s + margin past expiry — should fire
        assertEquals(true, fired)
    }

    @Test
    fun timerIsRemovedAfterFiring() = runTest {
        val scheduler = createScheduler()
        scheduler.schedule("transit:SHIP-1", Clock.System.now() + 5.seconds) { }
        advanceTimeBy(7_000)
        assertTrue(scheduler.activeTimers.value.isEmpty())
    }

    @Test
    fun multipleTimersFireInOrder() = runTest {
        val scheduler = createScheduler()
        val results = mutableListOf<String>()
        val now = Clock.System.now()
        scheduler.schedule("first", now + 5.seconds) { results.add("first") }
        scheduler.schedule("second", now + 10.seconds) { results.add("second") }

        advanceTimeBy(7_000) // first fires at 5s + 1s = 6s
        assertEquals(listOf("first"), results)

        advanceTimeBy(5_000) // second fires at 10s + 1s = 11s
        assertEquals(listOf("first", "second"), results)
    }

    @Test
    fun onResumeFiresExpiredTimers() = runTest {
        val scheduler = createScheduler()
        var fired = false
        // Schedule a timer that already expired
        scheduler.schedule("transit:SHIP-1", Clock.System.now() - 5.seconds) { fired = true }
        scheduler.onResume()
        advanceTimeBy(100) // Let coroutine execute
        assertEquals(true, fired)
    }

    @Test
    fun actionFailureLogsAndRemovesEntry() = runTest {
        val scheduler = createScheduler()
        scheduler.schedule("transit:SHIP-1", Clock.System.now() + 2.seconds) {
            throw RuntimeException("API error")
        }
        advanceTimeBy(4_000)
        // Timer should be removed even though action threw
        assertTrue(scheduler.activeTimers.value.isEmpty())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshSchedulerTest" -x compileTestKotlinIosArm64 -x compileTestKotlinIosSimulatorArm64`
Expected: Compilation failure — `RefreshScheduler` and `ScheduledRefresh` do not exist.

- [ ] **Step 3: Write ScheduledRefresh data class**

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler

import kotlin.time.Instant

data class ScheduledRefresh(
    val id: String,
    val expiresAt: Instant,
    val action: suspend () -> Unit
)
```

- [ ] **Step 4: Write RefreshScheduler implementation**

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class RefreshScheduler(private val scope: CoroutineScope) {

    private val _activeTimers = MutableStateFlow<Map<String, ScheduledRefresh>>(emptyMap())
    val activeTimers: StateFlow<Map<String, ScheduledRefresh>> = _activeTimers.asStateFlow()

    private var loopJob: Job? = null

    fun schedule(id: String, expiresAt: kotlin.time.Instant, action: suspend () -> Unit) {
        _activeTimers.update { it + (id to ScheduledRefresh(id, expiresAt, action)) }
        restartLoop()
    }

    fun cancel(id: String) {
        _activeTimers.update { it - id }
        restartLoop()
    }

    fun cancelByPrefix(prefix: String) {
        _activeTimers.update { map -> map.filterKeys { !it.startsWith(prefix) } }
        restartLoop()
    }

    fun onResume() {
        val now = Clock.System.now()
        val expired = _activeTimers.value.filter { (_, entry) ->
            entry.expiresAt + 1.seconds <= now
        }
        if (expired.isNotEmpty()) {
            _activeTimers.update { it - expired.keys }
            expired.values.forEach { entry ->
                scope.launch {
                    try {
                        entry.action()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Napier.e("RefreshScheduler: action '${entry.id}' failed on resume", e)
                    }
                }
            }
        }
        restartLoop()
    }

    private fun restartLoop() {
        loopJob?.cancel()
        loopJob = scope.launch { runLoop() }
    }

    private suspend fun runLoop() {
        while (true) {
            val entries = _activeTimers.value
            if (entries.isEmpty()) return

            val nearest = entries.values.minBy { it.expiresAt }
            val now = Clock.System.now()
            val waitDuration = (nearest.expiresAt + 1.seconds) - now

            if (waitDuration.isPositive()) {
                delay(waitDuration)
            }

            // Fire the entry
            _activeTimers.update { it - nearest.id }
            try {
                nearest.action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("RefreshScheduler: action '${nearest.id}' failed", e)
            }
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshSchedulerTest" -x compileTestKotlinIosArm64 -x compileTestKotlinIosSimulatorArm64`
Expected: All 9 tests PASS.

Note: The `timerFiresAfterExpiryPlusOneSecond` test depends on `TestScope` controlling virtual time. `Clock.System.now()` uses real time in tests, so we may need to adjust the test to account for this. If `Clock.System.now()` returns real wall-clock time even in `runTest`, the timer offsets must use durations from the actual now. The `advanceTimeBy` only controls `delay()` — it does NOT control `Clock.System.now()`. If tests fail because of this, inject a `Clock` parameter into `RefreshScheduler` (defaulting to `Clock.System`) and use `TestClock` in tests. This is a known pattern — update both the scheduler constructor and the test setup accordingly.

- [ ] **Step 6: Commit**

```bash
git add spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/scheduler/ScheduledRefresh.kt spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/scheduler/RefreshScheduler.kt spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/scheduler/RefreshSchedulerTest.kt
git commit -m "$(cat <<'EOF'
feat(sdk): add RefreshScheduler centralized timer engine

Single-coroutine priority-queue loop that fires targeted API
refreshes 1 second after timed game events expire. Supports
schedule/cancel/cancelByPrefix and onResume for background
catch-up.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: SpaceTradersSession + SessionManager

**Files:**
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/session/SpaceTradersSession.kt`
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/session/SessionManager.kt`
- Test: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/session/SessionManagerTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.session

import com.brokenhuskysledteam.spacetradersio.sdk.testing.FakeTokenRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionManagerTest {

    @Test
    fun requireSessionThrowsWhenNoSession() {
        val manager = SessionManager(FakeTokenRepository(storedToken = null))
        assertFailsWith<IllegalStateException> {
            manager.requireSession()
        }
    }

    @Test
    fun loginCreatesSessionAndSavesToken() {
        val tokenRepo = FakeTokenRepository(storedToken = null)
        val manager = SessionManager(tokenRepo)
        manager.login("my-token")
        assertNotNull(manager.requireSession())
        assertEquals("my-token", tokenRepo.storedToken)
    }

    @Test
    fun logoutDestroysSessionAndClearsToken() {
        val tokenRepo = FakeTokenRepository(storedToken = "my-token")
        val manager = SessionManager(tokenRepo)
        manager.login("my-token")
        manager.logout()
        assertNull(tokenRepo.storedToken)
        assertFailsWith<IllegalStateException> { manager.requireSession() }
    }

    @Test
    fun restoreIfAuthenticatedCreatesSessionWhenTokenExists() {
        val manager = SessionManager(FakeTokenRepository(storedToken = "existing-token"))
        manager.restoreIfAuthenticated()
        assertNotNull(manager.requireSession())
    }

    @Test
    fun restoreIfAuthenticatedDoesNothingWhenNoToken() {
        val manager = SessionManager(FakeTokenRepository(storedToken = null))
        manager.restoreIfAuthenticated()
        assertFailsWith<IllegalStateException> { manager.requireSession() }
    }

    @Test
    fun restoreIfAuthenticatedDoesNotRecreateExistingSession() {
        val tokenRepo = FakeTokenRepository(storedToken = "token")
        val manager = SessionManager(tokenRepo)
        manager.restoreIfAuthenticated()
        val session1 = manager.requireSession()
        manager.restoreIfAuthenticated()
        val session2 = manager.requireSession()
        assertTrue(session1 === session2) // Same instance
    }

    @Test
    fun loginAfterLogoutCreatesNewSession() {
        val tokenRepo = FakeTokenRepository(storedToken = null)
        val manager = SessionManager(tokenRepo)
        manager.login("token-1")
        val session1 = manager.requireSession()
        manager.logout()
        manager.login("token-2")
        val session2 = manager.requireSession()
        assertTrue(session1 !== session2) // Different instance
    }

    @Test
    fun sessionContainsAllStores() {
        val manager = SessionManager(FakeTokenRepository(storedToken = null))
        manager.login("token")
        val session = manager.requireSession()
        assertNotNull(session.fleetStateStore)
        assertNotNull(session.agentStateStore)
        assertNotNull(session.contractStateStore)
        assertNotNull(session.refreshScheduler)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManagerTest" -x compileTestKotlinIosArm64 -x compileTestKotlinIosSimulatorArm64`
Expected: Compilation failure — `SpaceTradersSession` and `SessionManager` do not exist.

- [ ] **Step 3: Write SpaceTradersSession**

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.session

import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.AgentStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.ContractStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

class SpaceTradersSession(private val scope: CoroutineScope) {

    val refreshScheduler = RefreshScheduler(scope)
    val fleetStateStore = FleetStateStore()
    val agentStateStore = AgentStateStore()
    val contractStateStore = ContractStateStore()

    fun onResume() {
        refreshScheduler.onResume()
    }

    fun destroy() {
        scope.cancel()
    }
}
```

- [ ] **Step 4: Write SessionManager**

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.session

import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

- [ ] **Step 5: Run tests to verify they pass**

Run: `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManagerTest" -x compileTestKotlinIosArm64 -x compileTestKotlinIosSimulatorArm64`
Expected: All 8 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/session/SpaceTradersSession.kt spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/session/SessionManager.kt spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/session/SessionManagerTest.kt
git commit -m "$(cat <<'EOF'
feat(sdk): add auth-scoped SpaceTradersSession and SessionManager

Session bundles all state stores and the refresh scheduler, tied to
authentication lifecycle. Created on login, destroyed on logout.
SessionManager.requireSession() provides safe access with a clear
error if the invariant is violated.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Enhance FleetRepository with Write-Through + Timer Registration

**Files:**
- Modify: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/repository/FleetRepository.kt`
- Modify: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/FleetRepositoryImpl.kt`
- Test: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/FleetRepositoryImplWriteThroughTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.CooldownDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.PaginatedResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MetaDto
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
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FleetRepositoryImplWriteThroughTest {

    private fun transitShipDto(symbol: String = "LADD-1") = ShipDto(
        symbol = symbol,
        registration = ShipRegistrationDto(name = symbol, factionSymbol = "COSMIC", role = "COMMAND"),
        frame = ShipFrameSummaryDto(symbol = "FRAME_SHUTTLE", name = "Shuttle Frame"),
        nav = ShipNavDto(
            systemSymbol = "X1-DF55",
            waypointSymbol = "X1-DF55-20250Z",
            route = ShipNavRouteDto(
                destination = ShipNavRouteWaypointDto("X1-DF55-30A", "PLANET", "X1-DF55", 10, 20),
                origin = ShipNavRouteWaypointDto("X1-DF55-20250Z", "MOON", "X1-DF55", 0, 0),
                departureTime = "2025-06-01T10:00:00.000Z",
                arrival = "2025-06-01T10:05:00.000Z"
            ),
            status = "IN_TRANSIT",
            flightMode = "CRUISE"
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
            systemSymbol = "X1-DF55",
            waypointSymbol = "X1-DF55-20250Z",
            route = ShipNavRouteDto(
                destination = ShipNavRouteWaypointDto("X1-DF55-20250Z", "MOON", "X1-DF55", 0, 0),
                origin = ShipNavRouteWaypointDto("X1-DF55-20250Z", "MOON", "X1-DF55", 0, 0),
                departureTime = "2025-06-01T10:00:00.000Z",
                arrival = "2025-06-01T10:00:00.000Z"
            ),
            status = "DOCKED",
            flightMode = "CRUISE"
        ),
        cargo = ShipCargoDto(capacity = 40, units = 0),
        fuel = ShipFuelDto(current = 400, capacity = 400),
        cooldown = CooldownDto(shipSymbol = symbol, totalSeconds = 0, remainingSeconds = 0)
    )

    private class FakeFleetApi(
        private val ships: List<ShipDto> = emptyList(),
        private val singleShip: ShipDto = dockedShipDto()
    ) : FleetApi {
        override suspend fun getMyShips(page: Int, limit: Int): PaginatedResponse<ShipDto> =
            PaginatedResponse(data = ships, meta = MetaDto(total = ships.size, page = page, limit = limit))

        override suspend fun getMyShip(shipSymbol: String): ShipDto = singleShip
        override suspend fun orbitShip(shipSymbol: String): ShipNavDto = singleShip.nav
        override suspend fun dockShip(shipSymbol: String): ShipNavDto = singleShip.nav
        override suspend fun refuelShip(shipSymbol: String): RefuelResponseDto = RefuelResponseDto(
            agent = AgentDto(null, "LADD", "X1-DF55-20250Z", 100000L, "COSMIC", 2),
            fuel = ShipFuelDto(current = 400, capacity = 400),
            transaction = MarketTransactionDto("X1-DF55-20250Z", shipSymbol, "FUEL", "PURCHASE", 6, 75, 450, "2025-06-01T10:00:00.000Z")
        )
    }

    @Test
    fun getMyShipWritesToStore() = runTest {
        val store = FleetStateStore()
        val scheduler = RefreshScheduler(this)
        val repo = FleetRepositoryImpl(FakeFleetApi(), store, scheduler)

        repo.getMyShip("LADD-2")
        assertEquals("LADD-2", store.entities.value["LADD-2"]?.symbol)
    }

    @Test
    fun refreshMyShipsWritesAllToStore() = runTest {
        val store = FleetStateStore()
        val scheduler = RefreshScheduler(this)
        val ships = listOf(dockedShipDto("LADD-1"), dockedShipDto("LADD-2"))
        val repo = FleetRepositoryImpl(FakeFleetApi(ships = ships), store, scheduler)

        repo.refreshMyShips()
        assertEquals(2, store.entities.value.size)
    }

    @Test
    fun getMyShipRegistersTransitTimer() = runTest {
        val store = FleetStateStore()
        val scheduler = RefreshScheduler(this)
        val repo = FleetRepositoryImpl(
            FakeFleetApi(singleShip = transitShipDto("LADD-1")),
            store, scheduler
        )

        repo.getMyShip("LADD-1")
        assertTrue(scheduler.activeTimers.value.containsKey("transit:LADD-1"))
    }

    @Test
    fun getMyShipDoesNotRegisterTimerForDockedShip() = runTest {
        val store = FleetStateStore()
        val scheduler = RefreshScheduler(this)
        val repo = FleetRepositoryImpl(FakeFleetApi(), store, scheduler)

        repo.getMyShip("LADD-2")
        assertTrue(scheduler.activeTimers.value.isEmpty())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.data.repository.FleetRepositoryImplWriteThroughTest" -x compileTestKotlinIosArm64 -x compileTestKotlinIosSimulatorArm64`
Expected: Compilation failure — `FleetRepositoryImpl` does not accept `FleetStateStore` or `RefreshScheduler`.

- [ ] **Step 3: Add `refreshMyShips` to FleetRepository interface**

Modify `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/repository/FleetRepository.kt`:

Replace the entire file content with:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.repository

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship

interface FleetRepository {
    suspend fun getMyShips(page: Int = 1, limit: Int = 20): List<Ship>
    suspend fun getMyShip(shipSymbol: String): Ship
    suspend fun refreshMyShips(page: Int = 1, limit: Int = 20)
}
```

- [ ] **Step 4: Rewrite FleetRepositoryImpl with write-through + timer registration**

Replace the entire content of `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/FleetRepositoryImpl.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore

class FleetRepositoryImpl(
    private val fleetApi: FleetApi,
    private val fleetStateStore: FleetStateStore,
    private val refreshScheduler: RefreshScheduler
) : FleetRepository {

    override suspend fun getMyShips(page: Int, limit: Int): List<Ship> {
        val ships = fleetApi.getMyShips(page, limit).data.map { it.toDomain() }
        fleetStateStore.putAll(ships.associateBy { it.symbol })
        ships.forEach { registerTimersForShip(it) }
        return ships
    }

    override suspend fun getMyShip(shipSymbol: String): Ship {
        val ship = fleetApi.getMyShip(shipSymbol).toDomain()
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
        ship.cooldown.expiration?.let { expiry ->
            refreshScheduler.schedule(
                id = "cooldown:${ship.symbol}",
                expiresAt = expiry,
                action = { getMyShip(ship.symbol) }
            )
        }
    }
}
```

- [ ] **Step 5: Fix existing FleetRepositoryImplTest**

The existing `FleetRepositoryImplTest` creates `FleetRepositoryImpl(FakeFleetApi(...))` with only one argument. Update it to pass the new required parameters. In `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/FleetRepositoryImplTest.kt`, add imports and update all `FleetRepositoryImpl(...)` calls:

Add imports at the top:
```kotlin
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore
import kotlinx.coroutines.test.TestScope
```

Replace every `FleetRepositoryImpl(FakeFleetApi(...))` with `FleetRepositoryImpl(FakeFleetApi(...), FleetStateStore(), RefreshScheduler(this))` — there are 6 instances across the test methods. The `this` inside `runTest { }` is the `TestScope`.

- [ ] **Step 6: Run all repository tests**

Run: `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.data.repository.*" -x compileTestKotlinIosArm64 -x compileTestKotlinIosSimulatorArm64`
Expected: All tests in both `FleetRepositoryImplTest` and `FleetRepositoryImplWriteThroughTest` PASS.

- [ ] **Step 7: Commit**

```bash
git add spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/repository/FleetRepository.kt spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/FleetRepositoryImpl.kt spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/FleetRepositoryImplTest.kt spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/FleetRepositoryImplWriteThroughTest.kt
git commit -m "$(cat <<'EOF'
feat(sdk): enhance FleetRepository with write-through caching

FleetRepositoryImpl now writes all API responses to FleetStateStore
and registers transit/cooldown timers with RefreshScheduler. Adds
refreshMyShips() for store-only population without return value.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Enhance Use Cases to Update Stores

**Files:**
- Modify: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/OrbitShipUseCase.kt`
- Modify: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/DockShipUseCase.kt`
- Modify: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/RefuelShipUseCase.kt`
- Modify: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/RegisterAgentUseCase.kt`

- [ ] **Step 1: Update OrbitShipUseCase to write to FleetStateStore**

Replace the entire content of `OrbitShipUseCase.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore

interface OrbitShipUseCase {
    suspend operator fun invoke(shipSymbol: String): ShipNav
}

class OrbitShipUseCaseImpl(
    private val fleetApi: FleetApi,
    private val fleetStateStore: FleetStateStore
) : OrbitShipUseCase {
    override suspend operator fun invoke(shipSymbol: String): ShipNav {
        val nav = fleetApi.orbitShip(shipSymbol).toDomain()
        fleetStateStore.update(shipSymbol) { ship -> ship.copy(nav = nav) }
        return nav
    }
}
```

- [ ] **Step 2: Update DockShipUseCase to write to FleetStateStore**

Replace the entire content of `DockShipUseCase.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore

interface DockShipUseCase {
    suspend operator fun invoke(shipSymbol: String): ShipNav
}

class DockShipUseCaseImpl(
    private val fleetApi: FleetApi,
    private val fleetStateStore: FleetStateStore
) : DockShipUseCase {
    override suspend operator fun invoke(shipSymbol: String): ShipNav {
        val nav = fleetApi.dockShip(shipSymbol).toDomain()
        fleetStateStore.update(shipSymbol) { ship -> ship.copy(nav = nav) }
        return nav
    }
}
```

- [ ] **Step 3: Update RefuelShipUseCase to write to both stores**

Replace the entire content of `RefuelShipUseCase.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.RefuelResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.AgentStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore

interface RefuelShipUseCase {
    suspend operator fun invoke(shipSymbol: String): RefuelResult
}

class RefuelShipUseCaseImpl(
    private val fleetApi: FleetApi,
    private val fleetStateStore: FleetStateStore,
    private val agentStateStore: AgentStateStore
) : RefuelShipUseCase {
    override suspend operator fun invoke(shipSymbol: String): RefuelResult {
        val response = fleetApi.refuelShip(shipSymbol)
        val result = RefuelResult(
            agent = response.agent.toDomain(),
            fuel = ShipFuel(current = response.fuel.current, capacity = response.fuel.capacity),
            transaction = response.transaction.toDomain()
        )
        fleetStateStore.update(shipSymbol) { ship -> ship.copy(fuel = result.fuel) }
        agentStateStore.update(result.agent)
        return result
    }
}
```

- [ ] **Step 4: Update RegisterAgentUseCase to use SessionManager**

Replace the entire content of `RegisterAgentUseCase.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AccountsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.FactionSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManager

data class RegistrationResult(
    val agent: Agent,
    val token: String
)

interface RegisterAgentUseCase {
    suspend operator fun invoke(
        symbol: String,
        faction: FactionSymbol = FactionSymbol.COSMIC,
        accountToken: String
    ): RegistrationResult
}

class RegisterAgentUseCaseImpl(
    private val accountsApi: AccountsApi,
    private val sessionManager: SessionManager
) : RegisterAgentUseCase {
    override suspend operator fun invoke(
        symbol: String,
        faction: FactionSymbol,
        accountToken: String
    ): RegistrationResult {
        val response = accountsApi.register(
            symbol = symbol,
            faction = faction.name,
            accountToken = accountToken
        )
        sessionManager.login(response.token)
        return RegistrationResult(
            agent = response.agent.toDomain(),
            token = response.token
        )
    }
}
```

- [ ] **Step 5: Fix use case tests for new constructor signatures**

Update `OrbitShipUseCaseTest.kt`, `DockShipUseCaseTest.kt`, `RefuelShipUseCaseTest.kt`, and `RegisterAgentUseCaseTest.kt` to pass the new dependencies. Each test should create a `FleetStateStore()` (and `AgentStateStore()` for refuel, `SessionManager(FakeTokenRepository())` for register) and pass them to the use case impl constructors.

- [ ] **Step 6: Run all use case tests**

Run: `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.*" -x compileTestKotlinIosArm64 -x compileTestKotlinIosSimulatorArm64`
Expected: All use case tests PASS.

- [ ] **Step 7: Commit**

```bash
git add spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/OrbitShipUseCase.kt spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/DockShipUseCase.kt spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/RefuelShipUseCase.kt spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/RegisterAgentUseCase.kt spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/OrbitShipUseCaseTest.kt spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/DockShipUseCaseTest.kt spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/RefuelShipUseCaseTest.kt spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/RegisterAgentUseCaseTest.kt
git commit -m "$(cat <<'EOF'
feat(sdk): use cases now update state stores after actions

OrbitShipUseCase and DockShipUseCase surgically update ship nav in
FleetStateStore. RefuelShipUseCase updates both FleetStateStore
(fuel) and AgentStateStore (credits). RegisterAgentUseCase uses
SessionManager.login() to create the auth-scoped session.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Rewire Hilt DI Module

**Files:**
- Modify: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/di/SdkModule.kt`

- [ ] **Step 1: Rewrite SdkModule with session-aware providers**

Replace the entire content of `SdkModule.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.di

import com.brokenhuskysledteam.spacetradersio.sdk.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AccountsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AgentsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AgentsApiImpl
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ContractsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApiImpl
import com.brokenhuskysledteam.spacetradersio.sdk.data.repository.FleetRepositoryImpl
import com.brokenhuskysledteam.spacetradersio.sdk.data.repository.TokenRepositoryImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManager
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.AgentStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.AcceptContractUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.DockShipUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.DockShipUseCaseImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.FulfillContractUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.GetMyContractsUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.OrbitShipUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.OrbitShipUseCaseImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RefuelShipUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RefuelShipUseCaseImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RegisterAgentUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RegisterAgentUseCaseImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SdkModule {

    // --- Infrastructure (Singleton) ---

    @Provides
    @Singleton
    fun provideTokenRepository(): TokenRepository =
        TokenRepositoryImpl(com.russhwolf.settings.Settings())

    @Provides
    @Singleton
    fun provideSpaceTradersClient(tokenRepository: TokenRepository): SpaceTradersClient =
        SpaceTradersClient(tokenRepository)

    @Provides
    @Singleton
    fun provideSessionManager(tokenRepository: TokenRepository): SessionManager =
        SessionManager(tokenRepository)

    // --- API Clients (Singleton) ---

    @Provides
    @Singleton
    fun provideAccountsApi(client: SpaceTradersClient): AccountsApi =
        AccountsApi(client)

    @Provides
    @Singleton
    fun provideAgentsApi(client: SpaceTradersClient): AgentsApi =
        AgentsApiImpl(client)

    @Provides
    @Singleton
    fun provideContractsApi(client: SpaceTradersClient): ContractsApi =
        ContractsApi(client)

    @Provides
    @Singleton
    fun provideFleetApi(client: SpaceTradersClient): FleetApi =
        FleetApiImpl(client)

    // --- Session-Scoped State (Unscoped — fetches from current session) ---

    @Provides
    fun provideFleetStateStore(sm: SessionManager): FleetStateStore =
        sm.requireSession().fleetStateStore

    @Provides
    fun provideAgentStateStore(sm: SessionManager): AgentStateStore =
        sm.requireSession().agentStateStore

    @Provides
    fun provideRefreshScheduler(sm: SessionManager): RefreshScheduler =
        sm.requireSession().refreshScheduler

    // --- Repositories ---

    @Provides
    fun provideFleetRepository(
        fleetApi: FleetApi,
        fleetStateStore: FleetStateStore,
        refreshScheduler: RefreshScheduler
    ): FleetRepository = FleetRepositoryImpl(fleetApi, fleetStateStore, refreshScheduler)

    // --- Use Cases ---

    @Provides
    fun provideRegisterAgentUseCase(
        accountsApi: AccountsApi,
        sessionManager: SessionManager
    ): RegisterAgentUseCase = RegisterAgentUseCaseImpl(accountsApi, sessionManager)

    @Provides
    fun provideAcceptContractUseCase(contractsApi: ContractsApi): AcceptContractUseCase =
        AcceptContractUseCase(contractsApi)

    @Provides
    fun provideGetMyContractsUseCase(contractsApi: ContractsApi): GetMyContractsUseCase =
        GetMyContractsUseCase(contractsApi)

    @Provides
    fun provideFulfillContractUseCase(contractsApi: ContractsApi): FulfillContractUseCase =
        FulfillContractUseCase(contractsApi)

    @Provides
    fun provideOrbitShipUseCase(
        fleetApi: FleetApi,
        fleetStateStore: FleetStateStore
    ): OrbitShipUseCase = OrbitShipUseCaseImpl(fleetApi, fleetStateStore)

    @Provides
    fun provideDockShipUseCase(
        fleetApi: FleetApi,
        fleetStateStore: FleetStateStore
    ): DockShipUseCase = DockShipUseCaseImpl(fleetApi, fleetStateStore)

    @Provides
    fun provideRefuelShipUseCase(
        fleetApi: FleetApi,
        fleetStateStore: FleetStateStore,
        agentStateStore: AgentStateStore
    ): RefuelShipUseCase = RefuelShipUseCaseImpl(fleetApi, fleetStateStore, agentStateStore)
}
```

- [ ] **Step 2: Verify compilation**

Run: `gradlew.bat :app:compileDebugKotlinAndroid`
Expected: Build may fail due to ViewModel changes not yet made — that's fine. Verify that `SdkModule` itself compiles (no unresolved references in the DI module).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/brokenhuskysledteam/spacetradersio/di/SdkModule.kt
git commit -m "$(cat <<'EOF'
refactor(di): rewire Hilt module for session-scoped state stores

SessionManager, FleetStateStore, AgentStateStore, and RefreshScheduler
are now provided via Hilt. Stores and scheduler are unscoped (fetched
from current session each injection). Repositories and use cases
receive their new store dependencies.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Refactor ShipListViewModel to Observe Store

**Files:**
- Modify: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/ships/ShipListViewModel.kt`

- [ ] **Step 1: Rewrite ShipListViewModel**

Replace the entire content of `ShipListViewModel.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.ui.ships

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.navigation.NavigationTarget
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShipListViewModel @Inject constructor(
    private val fleetStateStore: FleetStateStore,
    private val fleetRepository: FleetRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ShipListUiState> = combine(
        fleetStateStore.observeAll(),
        _isLoading,
        _error
    ) { ships, isLoading, error ->
        ShipListUiState(
            ships = ships.values.map { it.toSummary() },
            isLoading = isLoading,
            error = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShipListUiState())

    private val _navigationEvent = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    init {
        if (fleetStateStore.entities.value.isEmpty()) {
            loadShips()
        }
    }

    fun onEvent(event: ShipListEvent) {
        when (event) {
            is ShipListEvent.RetryClicked -> loadShips()
            is ShipListEvent.ShipSelected -> viewModelScope.launch {
                _navigationEvent.send(NavigationTarget.ShipDetail(event.symbol))
            }
        }
    }

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

private fun Ship.toSummary(): ShipSummary {
    val inTransit = nav.status == ShipNavStatus.IN_TRANSIT
    return ShipSummary(
        symbol = symbol,
        frameName = frameName,
        status = nav.status,
        waypointSymbol = nav.waypointSymbol,
        systemSymbol = nav.systemSymbol,
        arrivalTime = if (inTransit) nav.route.arrivalTime else null,
        departureTime = if (inTransit) nav.route.departureTime else null
    )
}
```

- [ ] **Step 2: Verify compilation**

Run: `gradlew.bat :app:compileDebugKotlinAndroid`
Expected: May still fail due to other ViewModels not yet updated. Check that `ShipListViewModel` itself has no errors.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/ships/ShipListViewModel.kt
git commit -m "$(cat <<'EOF'
refactor(app): ShipListViewModel observes FleetStateStore

Replaces direct API fetch with reactive observation of the fleet
store. Ships list auto-updates when any ship's state changes
(transit completion, orbit/dock, refuel). Only fetches from API
if the store is empty (first load).

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Refactor ShipDetailViewModel to Observe Store

**Files:**
- Modify: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/ships/ShipDetailViewModel.kt`

- [ ] **Step 1: Rewrite ShipDetailViewModel**

Replace the entire content of `ShipDetailViewModel.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.ui.ships

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.DockShipUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.OrbitShipUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RefuelShipUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShipDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val fleetStateStore: FleetStateStore,
    private val fleetRepository: FleetRepository,
    private val orbitShipUseCase: OrbitShipUseCase,
    private val dockShipUseCase: DockShipUseCase,
    private val refuelShipUseCase: RefuelShipUseCase
) : ViewModel() {

    private val shipSymbol: String = checkNotNull(savedStateHandle["shipSymbol"])

    private val _localState = MutableStateFlow(LocalState())
    
    val uiState: StateFlow<ShipDetailUiState> = combine(
        fleetStateStore.observe(shipSymbol),
        _localState
    ) { ship, local ->
        ShipDetailUiState(
            ship = ship?.toDetail(),
            isLoading = local.isLoading,
            isActionInProgress = local.isActionInProgress,
            actionResult = local.actionResult,
            error = local.error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShipDetailUiState())

    init {
        if (fleetStateStore.entities.value[shipSymbol] == null) {
            loadShip()
        }
    }

    fun onEvent(event: ShipDetailEvent) {
        when (event) {
            is ShipDetailEvent.OrbitClicked -> performAction {
                val nav = orbitShipUseCase(shipSymbol)
                _localState.update { it.copy(actionResult = ActionResult.Orbited(nav.waypointSymbol)) }
            }

            is ShipDetailEvent.DockClicked -> performAction {
                val nav = dockShipUseCase(shipSymbol)
                _localState.update { it.copy(actionResult = ActionResult.Docked(nav.waypointSymbol)) }
            }

            is ShipDetailEvent.RefuelClicked -> performAction {
                val result = refuelShipUseCase(shipSymbol)
                _localState.update {
                    it.copy(
                        actionResult = ActionResult.Refueled(
                            fuelAdded = result.transaction.units,
                            totalCost = result.transaction.totalPrice,
                            newCredits = result.agent.credits
                        )
                    )
                }
            }

            is ShipDetailEvent.ActionResultDismissed -> _localState.update { it.copy(actionResult = null) }

            is ShipDetailEvent.RetryClicked -> loadShip()
        }
    }

    private fun loadShip() {
        _localState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                fleetRepository.getMyShip(shipSymbol)
                _localState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _localState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load ship")
                }
            }
        }
    }

    private fun performAction(block: suspend () -> Unit) {
        _localState.update { it.copy(isActionInProgress = true, actionResult = null, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _localState.update { it.copy(error = e.message ?: "Action failed") }
            } finally {
                _localState.update { it.copy(isActionInProgress = false) }
            }
        }
    }

    private data class LocalState(
        val isLoading: Boolean = false,
        val isActionInProgress: Boolean = false,
        val actionResult: ActionResult? = null,
        val error: String? = null
    )
}

private fun Ship.toDetail(): ShipDetail {
    val inTransit = nav.status == ShipNavStatus.IN_TRANSIT
    return ShipDetail(
        symbol = symbol,
        frameName = frameName,
        role = registration.role,
        navStatus = nav.status,
        flightMode = nav.flightMode,
        systemSymbol = nav.systemSymbol,
        waypointSymbol = nav.waypointSymbol,
        destinationSymbol = nav.route.destination.symbol,
        destinationType = nav.route.destination.type,
        arrivalTime = if (inTransit) nav.route.arrivalTime else null,
        departureTime = if (inTransit) nav.route.departureTime else null,
        fuelCurrent = fuel.current,
        fuelCapacity = fuel.capacity,
        cargoUnits = cargo.units,
        cargoCapacity = cargo.capacity
    )
}
```

- [ ] **Step 2: Verify compilation**

Run: `gradlew.bat :app:compileDebugKotlinAndroid`
Expected: May still fail due to DashboardViewModel/AuthViewModel not yet updated.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/ships/ShipDetailViewModel.kt
git commit -m "$(cat <<'EOF'
refactor(app): ShipDetailViewModel observes FleetStateStore

Ship detail data now comes from the reactive store via
observe(shipSymbol). Use cases update the store directly,
so the detail screen auto-reacts. LocalState handles
transient UI concerns (loading, action progress, errors).

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: Refactor DashboardViewModel to Observe AgentStateStore

**Files:**
- Modify: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/dashboard/DashboardViewModel.kt`

- [ ] **Step 1: Rewrite DashboardViewModel**

Replace the entire content of `DashboardViewModel.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.navigation.NavigationTarget
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AgentsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersApiException
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersError
import com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManager
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.AgentStateStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val agentsApi: AgentsApi,
    private val agentStateStore: AgentStateStore,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DashboardUiState> = combine(
        agentStateStore.agent,
        _isLoading,
        _error
    ) { agent, isLoading, error ->
        DashboardUiState(agent = agent, isLoading = isLoading, error = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    private val _navigationEvent = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    init {
        loadAgent()
    }

    fun onEvent(event: DashboardEvent) {
        when (event) {
            is DashboardEvent.RetryClicked -> loadAgent()
            is DashboardEvent.LogoutClicked -> logout()
            is DashboardEvent.ErrorDismissed -> _error.value = null
            is DashboardEvent.FleetCardClicked -> viewModelScope.launch {
                _navigationEvent.send(NavigationTarget.ShipList)
            }
        }
    }

    private fun loadAgent() {
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val agent = agentsApi.getMyAgent().toDomain()
                agentStateStore.update(agent)
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

    private fun logout() {
        sessionManager.logout()
        viewModelScope.launch {
            _navigationEvent.send(NavigationTarget.Auth)
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `gradlew.bat :app:compileDebugKotlinAndroid`
Expected: May still fail due to AuthViewModel not yet updated.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/dashboard/DashboardViewModel.kt
git commit -m "$(cat <<'EOF'
refactor(app): DashboardViewModel observes AgentStateStore

Agent data flows reactively from the store. Logout uses
SessionManager to destroy session + clear token atomically.
Auth errors also go through SessionManager.logout().

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: Refactor AuthViewModel to Use SessionManager

**Files:**
- Modify: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/auth/AuthViewModel.kt`

- [ ] **Step 1: Update AuthViewModel**

The only change: replace `tokenRepository.saveToken(...)` in `importToken()` with `sessionManager.login(...)`, and replace the `tokenRepository` dependency with `sessionManager`. The `RegisterAgentUseCase` already calls `sessionManager.login()` internally, so the registration path is handled.

Replace the entire content of `AuthViewModel.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.navigation.NavigationTarget
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersApiException
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersError
import com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManager
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RegisterAgentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val registerAgentUseCase: RegisterAgentUseCase,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _navigationEvent = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    fun onEvent(event: AuthEvent) {
        when (event) {
            is AuthEvent.TabSelected -> _uiState.update { it.copy(selectedTab = event.tab, error = null) }
            is AuthEvent.CallsignChanged -> _uiState.update { it.copy(callsign = event.value) }
            is AuthEvent.FactionSelected -> _uiState.update { it.copy(selectedFaction = event.faction) }
            is AuthEvent.AccountTokenChanged -> _uiState.update { it.copy(accountToken = event.value) }
            is AuthEvent.AgentTokenChanged -> _uiState.update { it.copy(agentToken = event.value) }
            is AuthEvent.RegisterClicked -> register()
            is AuthEvent.ImportClicked -> importToken()
            is AuthEvent.ErrorDismissed -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun register() {
        val state = _uiState.value
        if (state.callsign.isBlank()) {
            _uiState.update { it.copy(error = "Callsign cannot be empty") }
            return
        }
        if (state.accountToken.isBlank()) {
            _uiState.update { it.copy(error = "Account token cannot be empty") }
            return
        }
        _uiState.update { it.copy(isRegistering = true, error = null) }
        viewModelScope.launch {
            try {
                registerAgentUseCase(state.callsign.trim(), state.selectedFaction, state.accountToken.trim())
                _uiState.update { it.copy(isRegistering = false) }
                _navigationEvent.send(NavigationTarget.Dashboard)
            } catch (e: SpaceTradersApiException) {
                val errorMessage = when (e.error) {
                    is SpaceTradersError.AuthError.RegisterAgentConflictSymbol -> "Callsign already taken"
                    is SpaceTradersError.AuthError.RegisterAgentSymbolReserved -> "Callsign is reserved"
                    else -> e.message ?: "Registration failed"
                }
                _uiState.update { it.copy(isRegistering = false, error = errorMessage) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRegistering = false, error = e.message ?: "Registration failed") }
            }
        }
    }

    private fun importToken() {
        val state = _uiState.value
        if (state.agentToken.isBlank()) {
            _uiState.update { it.copy(error = "Token cannot be empty") }
            return
        }
        _uiState.update { it.copy(isImporting = true, error = null) }
        sessionManager.login(state.agentToken.trim())
        _uiState.update { it.copy(isImporting = false) }
        viewModelScope.launch {
            _navigationEvent.send(NavigationTarget.Dashboard)
        }
    }
}
```

- [ ] **Step 2: Verify full app compilation**

Run: `gradlew.bat :app:compileDebugKotlinAndroid`
Expected: PASS — all ViewModels now compile with the new dependencies.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/auth/AuthViewModel.kt
git commit -m "$(cat <<'EOF'
refactor(app): AuthViewModel uses SessionManager for token import

Token import now goes through SessionManager.login() which creates
the auth-scoped session. Registration already uses SessionManager
via the updated RegisterAgentUseCase.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: Application Lifecycle Observer + Session Restore

**Files:**
- Create: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/AppLifecycleObserver.kt`
- Modify: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/SpaceTradersApplication.kt`

- [ ] **Step 1: Create AppLifecycleObserver**

```kotlin
package com.brokenhuskysledteam.spacetradersio

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManager
import javax.inject.Inject

class AppLifecycleObserver @Inject constructor(
    private val sessionManager: SessionManager
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        try {
            sessionManager.requireSession().onResume()
        } catch (_: IllegalStateException) {
            // No active session (user not authenticated) — nothing to resume
        }
    }
}
```

- [ ] **Step 2: Update SpaceTradersApplication**

Replace the entire content of `SpaceTradersApplication.kt`:

```kotlin
package com.brokenhuskysledteam.spacetradersio

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

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
```

- [ ] **Step 3: Verify full compilation**

Run: `gradlew.bat :app:compileDebugKotlinAndroid`
Expected: PASS. Note: You may need to add `implementation("androidx.lifecycle:lifecycle-process:2.10.0")` to `app/build.gradle.kts` if `ProcessLifecycleOwner` is not already available. Check the existing dependencies first.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/brokenhuskysledteam/spacetradersio/AppLifecycleObserver.kt app/src/main/java/com/brokenhuskysledteam/spacetradersio/SpaceTradersApplication.kt
git commit -m "$(cat <<'EOF'
feat(app): add lifecycle observer and session restore on cold start

AppLifecycleObserver calls session.onResume() when the app returns
to foreground, triggering catch-up for any timers that expired while
backgrounded. Application.onCreate restores the session if a token
exists in storage.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: Run Full Test Suite + Build Verification

- [ ] **Step 1: Run all SDK tests**

Run: `gradlew.bat :spacetradersiosdk:allTests`
Expected: All tests PASS. If any existing tests fail due to the constructor changes, fix them (they should have been caught in Tasks 5-6).

- [ ] **Step 2: Run all app unit tests**

Run: `gradlew.bat :app:testDebugUnitTest`
Expected: All tests PASS. If app ViewModel tests exist and fail due to constructor changes, update them to inject the new dependencies (SessionManager, stores).

- [ ] **Step 3: Build debug APK**

Run: `gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit any test fixes**

If tests needed fixing:
```bash
git add -u
git commit -m "$(cat <<'EOF'
fix(tests): update existing tests for new state management dependencies

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 14: Manual Verification on Emulator

- [ ] **Step 1: Install and launch the app**

Install the debug APK on the Android emulator. Login with a valid agent token.

- [ ] **Step 2: Verify fleet list loads from store**

Navigate to the fleet screen. Ships should load and display. Navigate away and back — ships should appear instantly (from store, no loading spinner).

- [ ] **Step 3: Verify cross-screen reactivity**

Open a ship detail screen. Perform an orbit or dock action. Navigate back to the fleet list. The ship's status should already be updated (no stale "DOCKED" when you just orbited).

- [ ] **Step 4: Verify transit timer auto-refresh**

If a ship is IN_TRANSIT: watch the countdown on the detail screen. When it reaches zero, the ship's status should auto-update to IN_ORBIT (triggered by RefreshScheduler). Navigate to the list screen — it should also show the updated status.

- [ ] **Step 5: Verify logout clears state**

Logout. Verify you return to the auth screen. Login with a different token (or the same). Verify ships load fresh from the API (store was cleared on logout).
