# System & Waypoint Exploration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add system waypoint exploration with hierarchical display, sorting, filtering, and ship navigation from the Ship Detail screen.

**Architecture:** Bottom-up build: domain models/enums, API DTOs + endpoints + mappers, state store + repository, navigate use case, then app-layer ViewModel + UI. Each layer is TDD'd independently before the next begins.

**Tech Stack:** Kotlin Multiplatform (`commonMain`), Ktor (`MockEngine` for tests), `kotlinx.coroutines` (`StateFlow`, `combine`), `kotlinx-serialization`, Hilt DI, Jetpack Compose, `kotlin.test` + `kotlinx-coroutines-test`.

**Spec:** `docs/superpowers/specs/2026-04-08-system-waypoint-exploration-design.md`

---

## File Structure

### New Files (SDK `commonMain`)

| File | Responsibility |
|---|---|
| `sdk/domain/model/Waypoint.kt` | Domain model for a system waypoint |
| `sdk/domain/model/WaypointTrait.kt` | Domain model for waypoint traits |
| `sdk/domain/model/NavigateResult.kt` | Domain model for navigate response |
| `sdk/domain/model/Distance.kt` | Euclidean distance utility function |
| `sdk/domain/model/enums/WaypointTraitSymbol.kt` | All 69 waypoint trait symbols |
| `sdk/domain/state/WaypointStateStore.kt` | Reactive store keyed by waypoint symbol |
| `sdk/domain/repository/SystemRepository.kt` | Interface for system waypoint data |
| `sdk/domain/usecase/NavigateShipUseCase.kt` | Interface + Impl: auto-orbit + navigate + state update |
| `sdk/api/dto/WaypointDto.kt` | DTOs for waypoint API responses |
| `sdk/api/dto/NavigateResponseDto.kt` | DTOs for navigate request/response |
| `sdk/api/endpoints/SystemsApi.kt` | Interface + Impl for GET /systems/{}/waypoints |
| `sdk/api/mapper/WaypointMapper.kt` | WaypointDto/TraitDto → domain mappers |
| `sdk/api/mapper/NavigateMapper.kt` | NavigateResponseDto → domain mapper |
| `sdk/data/repository/SystemRepositoryImpl.kt` | Fetch all pages + populate state store |

### New Files (SDK `androidHostTest`)

| File | Responsibility |
|---|---|
| `sdk/domain/model/DistanceTest.kt` | Euclidean distance edge cases |
| `sdk/domain/model/enums/WaypointTraitSymbolTest.kt` | fromString known/unknown |
| `sdk/api/mapper/WaypointMapperTest.kt` | DTO → domain mapping for waypoints + traits |
| `sdk/api/mapper/NavigateMapperTest.kt` | DTO → domain mapping for navigate response |
| `sdk/api/endpoints/SystemsApiImplTest.kt` | HTTP method, path, pagination params |
| `sdk/data/repository/SystemRepositoryImplTest.kt` | Single page, multi-page, state store population |
| `sdk/domain/usecase/NavigateShipUseCaseTest.kt` | Auto-orbit logic, fleet store update, happy path |

### New Files (App)

| File | Responsibility |
|---|---|
| `ui/systemmap/SystemMapUiState.kt` | UI state, events, action results, enums |
| `ui/systemmap/SystemMapViewModel.kt` | Combine waypoint/fleet/local state, sort/filter/navigate |
| `ui/systemmap/SystemMapScreen.kt` | Compose UI: hierarchy list, filters, sort, navigate |
| `app/src/test/.../ui/systemmap/SystemMapViewModelTest.kt` | ViewModel test with fakes |

### Modified Files

| File | Change |
|---|---|
| `sdk/api/endpoints/FleetApi.kt` | Add `navigateShip()` to interface + impl |
| `sdk/domain/session/SpaceTradersSession.kt` | Add `waypointStateStore` property |
| `sdk/domain/session/SpaceTradersSessionImpl.kt` | Instantiate `WaypointStateStore` |
| `di/SdkModule.kt` | Add providers for SystemsApi, WaypointStateStore, SystemRepository, NavigateShipUseCase |
| `navigation/Routes.kt` | Add `SystemMapRoute` |
| `navigation/NavigationTarget.kt` | Add `SystemMap` |
| `navigation/SpaceTradersNavHost.kt` | Add `SystemMapRoute` composable, update `ShipDetailScreen` wiring |
| `ui/ships/ShipDetailScreen.kt` | Add "View System" button, accept callback |
| `ui/ships/ShipDetailUiState.kt` | Add `ViewSystemClicked` event |

---

## Task 1: Domain Models & Enums

**Files:**
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/model/enums/WaypointTraitSymbol.kt`
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/model/WaypointTrait.kt`
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/model/Waypoint.kt`
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/model/NavigateResult.kt`
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/model/Distance.kt`
- Test: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/model/enums/WaypointTraitSymbolTest.kt`
- Test: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/model/DistanceTest.kt`

- [ ] **Step 1: Write WaypointTraitSymbol enum tests**

Add tests to the existing `EnumParsingTest.kt` file (follows established pattern of grouping enum tests):

```kotlin
// Add to spacetradersiosdk/src/androidHostTest/.../domain/model/enums/EnumParsingTest.kt

// ── WaypointTraitSymbol ─────────────────────────────────────────────

@Test
fun waypointTraitSymbol_knownValue_returnsCorrectEntry() {
    assertEquals(WaypointTraitSymbol.MARKETPLACE, WaypointTraitSymbol.fromString("MARKETPLACE"))
}

@Test
fun waypointTraitSymbol_shipyard_returnsCorrectEntry() {
    assertEquals(WaypointTraitSymbol.SHIPYARD, WaypointTraitSymbol.fromString("SHIPYARD"))
}

@Test
fun waypointTraitSymbol_unknownValue_fallsBackToUncharted() {
    assertEquals(WaypointTraitSymbol.UNCHARTED, WaypointTraitSymbol.fromString("FUTURE_TRAIT"))
}

@Test
fun waypointTraitSymbol_caseSensitive_lowercaseFallsBack() {
    assertEquals(WaypointTraitSymbol.UNCHARTED, WaypointTraitSymbol.fromString("marketplace"))
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.EnumParsingTest"`
Expected: FAIL — `WaypointTraitSymbol` does not exist yet.

- [ ] **Step 3: Create WaypointTraitSymbol enum**

```kotlin
// spacetradersiosdk/src/commonMain/.../domain/model/enums/WaypointTraitSymbol.kt
package com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums

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

- [ ] **Step 4: Run enum tests to verify they pass**

Run: `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.EnumParsingTest"`
Expected: PASS — all existing + new enum tests pass.

- [ ] **Step 5: Write Distance tests**

```kotlin
// spacetradersiosdk/src/androidHostTest/.../domain/model/DistanceTest.kt
package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class DistanceTest {

    @Test
    fun samePoint_returnsZero() {
        assertEquals(0.0, euclideanDistance(5, 5, 5, 5))
    }

    @Test
    fun knownTriangle_3_4_5() {
        assertEquals(5.0, euclideanDistance(0, 0, 3, 4))
    }

    @Test
    fun horizontalDistance() {
        assertEquals(10.0, euclideanDistance(0, 0, 10, 0))
    }

    @Test
    fun verticalDistance() {
        assertEquals(7.0, euclideanDistance(0, 0, 0, 7))
    }

    @Test
    fun negativeCoordinates() {
        assertEquals(5.0, euclideanDistance(-3, -4, 0, 0))
    }

    @Test
    fun symmetricDistance() {
        val d1 = euclideanDistance(1, 2, 4, 6)
        val d2 = euclideanDistance(4, 6, 1, 2)
        assertEquals(d1, d2)
    }
}
```

- [ ] **Step 6: Run Distance tests to verify they fail**

Run: `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.domain.model.DistanceTest"`
Expected: FAIL — `euclideanDistance` does not exist yet.

- [ ] **Step 7: Create Distance utility and remaining domain models**

```kotlin
// spacetradersiosdk/src/commonMain/.../domain/model/Distance.kt
package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import kotlin.math.pow
import kotlin.math.sqrt

fun euclideanDistance(x1: Int, y1: Int, x2: Int, y2: Int): Double =
    sqrt((x2 - x1).toDouble().pow(2) + (y2 - y1).toDouble().pow(2))
```

```kotlin
// spacetradersiosdk/src/commonMain/.../domain/model/WaypointTrait.kt
package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol

data class WaypointTrait(
    val symbol: WaypointTraitSymbol,
    val name: String,
    val description: String
)
```

```kotlin
// spacetradersiosdk/src/commonMain/.../domain/model/Waypoint.kt
package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType

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

```kotlin
// spacetradersiosdk/src/commonMain/.../domain/model/NavigateResult.kt
package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

data class NavigateResult(
    val nav: ShipNav,
    val fuel: ShipFuel
)
```

- [ ] **Step 8: Run all tests to verify they pass**

Run: `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.domain.model.DistanceTest"` and `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.EnumParsingTest"`
Expected: PASS

- [ ] **Step 9: Compile check**

Run: `gradlew.bat :spacetradersiosdk:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL — all new domain models compile.

- [ ] **Step 10: Commit**

```bash
git add spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/model/enums/WaypointTraitSymbol.kt spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/model/WaypointTrait.kt spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/model/Waypoint.kt spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/model/NavigateResult.kt spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/model/Distance.kt spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/model/DistanceTest.kt spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/model/enums/EnumParsingTest.kt
git commit -m "feat(sdk): add domain models for waypoints, distance, and navigation"
```

---

## Task 2: API DTOs

**Files:**
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/dto/WaypointDto.kt`
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/dto/NavigateResponseDto.kt`

- [ ] **Step 1: Create WaypointDto file**

```kotlin
// spacetradersiosdk/src/commonMain/.../api/dto/WaypointDto.kt
package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

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

- [ ] **Step 2: Create NavigateResponseDto file**

```kotlin
// spacetradersiosdk/src/commonMain/.../api/dto/NavigateResponseDto.kt
package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class NavigateResponseDto(
    val nav: ShipNavDto,
    val fuel: ShipFuelDto
)

@Serializable
data class NavigateRequestDto(val waypointSymbol: String)
```

- [ ] **Step 3: Compile check**

Run: `gradlew.bat :spacetradersiosdk:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/dto/WaypointDto.kt spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/dto/NavigateResponseDto.kt
git commit -m "feat(sdk): add DTOs for waypoint and navigate endpoints"
```

---

## Task 3: Mappers (WaypointMapper + NavigateMapper)

**Files:**
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/mapper/WaypointMapper.kt`
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/mapper/NavigateMapper.kt`
- Test: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/mapper/WaypointMapperTest.kt`
- Test: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/mapper/NavigateMapperTest.kt`

- [ ] **Step 1: Write WaypointMapper tests**

```kotlin
// spacetradersiosdk/src/androidHostTest/.../api/mapper/WaypointMapperTest.kt
package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.WaypointDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.WaypointOrbitalDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.WaypointTraitDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WaypointMapperTest {

    private fun fullDto() = WaypointDto(
        symbol = "X1-DF55-20250Z",
        type = "MOON",
        systemSymbol = "X1-DF55",
        x = -15,
        y = 12,
        orbits = "X1-DF55-17335A",
        orbitals = listOf(WaypointOrbitalDto("X1-DF55-20250Z-STATION")),
        traits = listOf(
            WaypointTraitDto(symbol = "MARKETPLACE", name = "Marketplace", description = "A marketplace")
        ),
        isUnderConstruction = false
    )

    // ── WaypointDto.toDomain ────────────────────────────────────────────────

    @Test
    fun waypoint_symbolMapsCorrectly() {
        assertEquals("X1-DF55-20250Z", fullDto().toDomain().symbol)
    }

    @Test
    fun waypoint_typeMapsCorrectly() {
        assertEquals(WaypointType.MOON, fullDto().toDomain().type)
    }

    @Test
    fun waypoint_systemSymbolMapsCorrectly() {
        assertEquals("X1-DF55", fullDto().toDomain().systemSymbol)
    }

    @Test
    fun waypoint_coordinatesMapCorrectly() {
        val wp = fullDto().toDomain()
        assertEquals(-15, wp.x)
        assertEquals(12, wp.y)
    }

    @Test
    fun waypoint_orbitsMapsCorrectly() {
        assertEquals("X1-DF55-17335A", fullDto().toDomain().orbits)
    }

    @Test
    fun waypoint_nullOrbits_mapsToNull() {
        val dto = fullDto().copy(orbits = null)
        assertNull(dto.toDomain().orbits)
    }

    @Test
    fun waypoint_orbitalsFlattenedToSymbols() {
        assertEquals(listOf("X1-DF55-20250Z-STATION"), fullDto().toDomain().orbitals)
    }

    @Test
    fun waypoint_emptyOrbitals_mapsToEmptyList() {
        val dto = fullDto().copy(orbitals = emptyList())
        assertTrue(dto.toDomain().orbitals.isEmpty())
    }

    @Test
    fun waypoint_traitsMappedCorrectly() {
        val traits = fullDto().toDomain().traits
        assertEquals(1, traits.size)
        assertEquals(WaypointTraitSymbol.MARKETPLACE, traits[0].symbol)
    }

    @Test
    fun waypoint_emptyTraits_mapsToEmptyList() {
        val dto = fullDto().copy(traits = emptyList())
        assertTrue(dto.toDomain().traits.isEmpty())
    }

    @Test
    fun waypoint_isUnderConstructionMapsCorrectly() {
        assertEquals(false, fullDto().toDomain().isUnderConstruction)
    }

    @Test
    fun waypoint_isUnderConstructionTrue() {
        val dto = fullDto().copy(isUnderConstruction = true)
        assertEquals(true, dto.toDomain().isUnderConstruction)
    }

    @Test
    fun waypoint_unknownType_fallsBackToPlanet() {
        val dto = fullDto().copy(type = "FUTURE_TYPE")
        assertEquals(WaypointType.PLANET, dto.toDomain().type)
    }

    // ── WaypointTraitDto.toDomain ───────────────────────────────────────────

    @Test
    fun trait_symbolMapsCorrectly() {
        val traitDto = WaypointTraitDto("SHIPYARD", "Shipyard", "A shipyard")
        assertEquals(WaypointTraitSymbol.SHIPYARD, traitDto.toDomain().symbol)
    }

    @Test
    fun trait_nameMapsCorrectly() {
        val traitDto = WaypointTraitDto("SHIPYARD", "Shipyard", "A shipyard")
        assertEquals("Shipyard", traitDto.toDomain().name)
    }

    @Test
    fun trait_descriptionMapsCorrectly() {
        val traitDto = WaypointTraitDto("SHIPYARD", "Shipyard", "A shipyard")
        assertEquals("A shipyard", traitDto.toDomain().description)
    }

    @Test
    fun trait_unknownSymbol_fallsBackToUncharted() {
        val traitDto = WaypointTraitDto("FUTURE_TRAIT", "Future", "Unknown")
        assertEquals(WaypointTraitSymbol.UNCHARTED, traitDto.toDomain().symbol)
    }
}
```

- [ ] **Step 2: Write NavigateMapper tests**

```kotlin
// spacetradersiosdk/src/androidHostTest/.../api/mapper/NavigateMapperTest.kt
package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.NavigateResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipFuelDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavRouteDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavRouteWaypointDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavFlightMode
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class NavigateMapperTest {

    private fun waypointDto(symbol: String = "X1-DF55-20250Z") =
        ShipNavRouteWaypointDto(symbol = symbol, type = "MOON", systemSymbol = "X1-DF55", x = 0, y = 0)

    private fun dto() = NavigateResponseDto(
        nav = ShipNavDto(
            systemSymbol = "X1-DF55",
            waypointSymbol = "X1-DF55-17335A",
            route = ShipNavRouteDto(
                destination = waypointDto("X1-DF55-17335A"),
                origin = waypointDto("X1-DF55-20250Z"),
                departureTime = "2025-06-01T10:00:00.000Z",
                arrival = "2025-06-01T10:30:00.000Z"
            ),
            status = "IN_TRANSIT",
            flightMode = "CRUISE"
        ),
        fuel = ShipFuelDto(current = 350, capacity = 400)
    )

    @Test
    fun navigate_navStatusMapsCorrectly() {
        assertEquals(ShipNavStatus.IN_TRANSIT, dto().toDomain().nav.status)
    }

    @Test
    fun navigate_navWaypointSymbolMapsCorrectly() {
        assertEquals("X1-DF55-17335A", dto().toDomain().nav.waypointSymbol)
    }

    @Test
    fun navigate_navFlightModeMapsCorrectly() {
        assertEquals(ShipNavFlightMode.CRUISE, dto().toDomain().nav.flightMode)
    }

    @Test
    fun navigate_fuelCurrentMapsCorrectly() {
        assertEquals(350, dto().toDomain().fuel.current)
    }

    @Test
    fun navigate_fuelCapacityMapsCorrectly() {
        assertEquals(400, dto().toDomain().fuel.capacity)
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.WaypointMapperTest"` and `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.NavigateMapperTest"`
Expected: FAIL — mapper functions don't exist yet.

- [ ] **Step 4: Create WaypointMapper**

```kotlin
// spacetradersiosdk/src/commonMain/.../api/mapper/WaypointMapper.kt
package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.WaypointDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.WaypointTraitDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Waypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.WaypointTrait
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType

fun WaypointDto.toDomain(): Waypoint = Waypoint(
    symbol = symbol,
    type = WaypointType.fromString(type),
    systemSymbol = systemSymbol,
    x = x,
    y = y,
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

- [ ] **Step 5: Create NavigateMapper**

```kotlin
// spacetradersiosdk/src/commonMain/.../api/mapper/NavigateMapper.kt
package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.NavigateResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.NavigateResult

fun NavigateResponseDto.toDomain(): NavigateResult = NavigateResult(
    nav = nav.toDomain(),
    fuel = fuel.toDomain()
)
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.WaypointMapperTest"` and `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.NavigateMapperTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/mapper/WaypointMapper.kt spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/mapper/NavigateMapper.kt spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/mapper/WaypointMapperTest.kt spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/mapper/NavigateMapperTest.kt
git commit -m "feat(sdk): add waypoint and navigate mappers with tests"
```

---

## Task 4: SystemsApi Endpoint

**Files:**
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/endpoints/SystemsApi.kt`
- Modify: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/endpoints/FleetApi.kt`
- Test: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/endpoints/SystemsApiImplTest.kt`

- [ ] **Step 1: Write SystemsApiImpl tests**

```kotlin
// spacetradersiosdk/src/androidHostTest/.../api/endpoints/SystemsApiImplTest.kt
package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.testing.buildMockSpaceTradersClient
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val WAYPOINTS_RESPONSE = """
{
  "data": [
    {
      "symbol": "X1-DF55-17335A",
      "type": "PLANET",
      "systemSymbol": "X1-DF55",
      "x": -21,
      "y": -16,
      "orbitals": [{"symbol": "X1-DF55-20250Z"}],
      "traits": [
        {"symbol": "MARKETPLACE", "name": "Marketplace", "description": "A marketplace"}
      ],
      "isUnderConstruction": false
    }
  ],
  "meta": {"total": 1, "page": 1, "limit": 20}
}
"""

private fun MockRequestHandleScope.okJson(content: String) = respond(
    content = content,
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
)

class SystemsApiImplTest {

    @Test
    fun getSystemWaypoints_sendsGetRequest() = runTest {
        var capturedMethod: HttpMethod? = null
        val client = buildMockSpaceTradersClient { request ->
            capturedMethod = request.method
            okJson(WAYPOINTS_RESPONSE)
        }
        SystemsApiImpl(client).getSystemWaypoints("X1-DF55")
        assertEquals(HttpMethod.Get, capturedMethod)
    }

    @Test
    fun getSystemWaypoints_sendsToCorrectPath() = runTest {
        var capturedPath: String? = null
        val client = buildMockSpaceTradersClient { request ->
            capturedPath = request.url.encodedPath
            okJson(WAYPOINTS_RESPONSE)
        }
        SystemsApiImpl(client).getSystemWaypoints("X1-DF55")
        assertEquals("/systems/X1-DF55/waypoints", capturedPath)
    }

    @Test
    fun getSystemWaypoints_sendsPaginationParams() = runTest {
        var capturedPage: String? = null
        var capturedLimit: String? = null
        val client = buildMockSpaceTradersClient { request ->
            capturedPage = request.url.parameters["page"]
            capturedLimit = request.url.parameters["limit"]
            okJson(WAYPOINTS_RESPONSE)
        }
        SystemsApiImpl(client).getSystemWaypoints("X1-DF55", page = 3, limit = 10)
        assertEquals("3", capturedPage)
        assertEquals("10", capturedLimit)
    }

    @Test
    fun getSystemWaypoints_returnsWaypointWithCorrectSymbol() = runTest {
        val client = buildMockSpaceTradersClient { okJson(WAYPOINTS_RESPONSE) }
        val result = SystemsApiImpl(client).getSystemWaypoints("X1-DF55")
        assertEquals("X1-DF55-17335A", result.data.first().symbol)
    }

    @Test
    fun getSystemWaypoints_returnsPaginationMeta() = runTest {
        val client = buildMockSpaceTradersClient { okJson(WAYPOINTS_RESPONSE) }
        val result = SystemsApiImpl(client).getSystemWaypoints("X1-DF55")
        assertEquals(1, result.meta.total)
    }

    @Test
    fun getSystemWaypoints_returnsWaypointWithTraits() = runTest {
        val client = buildMockSpaceTradersClient { okJson(WAYPOINTS_RESPONSE) }
        val result = SystemsApiImpl(client).getSystemWaypoints("X1-DF55")
        assertEquals("MARKETPLACE", result.data.first().traits.first().symbol)
    }

    @Test
    fun getSystemWaypoints_returnsWaypointWithOrbitals() = runTest {
        val client = buildMockSpaceTradersClient { okJson(WAYPOINTS_RESPONSE) }
        val result = SystemsApiImpl(client).getSystemWaypoints("X1-DF55")
        assertEquals("X1-DF55-20250Z", result.data.first().orbitals.first().symbol)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.SystemsApiImplTest"`
Expected: FAIL — `SystemsApiImpl` does not exist.

- [ ] **Step 3: Create SystemsApi interface and implementation**

```kotlin
// spacetradersiosdk/src/commonMain/.../api/endpoints/SystemsApi.kt
package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.PaginatedResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.WaypointDto
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

interface SystemsApi {
    suspend fun getSystemWaypoints(
        systemSymbol: String,
        page: Int = 1,
        limit: Int = 20
    ): PaginatedResponse<WaypointDto>
}

class SystemsApiImpl(private val client: SpaceTradersClient) : SystemsApi {

    override suspend fun getSystemWaypoints(
        systemSymbol: String,
        page: Int,
        limit: Int
    ): PaginatedResponse<WaypointDto> =
        client.authenticated.get("systems/$systemSymbol/waypoints") {
            parameter("page", page)
            parameter("limit", limit)
        }.body()
}
```

- [ ] **Step 4: Add navigateShip to FleetApi**

Add to the `FleetApi` interface in `FleetApi.kt`:

```kotlin
suspend fun navigateShip(shipSymbol: String, waypointSymbol: String): NavigateResponseDto
```

Add to `FleetApiImpl`:

```kotlin
override suspend fun navigateShip(shipSymbol: String, waypointSymbol: String): NavigateResponseDto =
    client.authenticated.post("my/ships/$shipSymbol/navigate") {
        setBody(NavigateRequestDto(waypointSymbol))
    }.body<ApiResponse<NavigateResponseDto>>().data
```

Add imports to FleetApi.kt:
```kotlin
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.NavigateResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.NavigateRequestDto
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.SystemsApiImplTest"`
Expected: PASS

- [ ] **Step 6: Compile check**

Run: `gradlew.bat :spacetradersiosdk:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/endpoints/SystemsApi.kt spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/endpoints/FleetApi.kt spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/api/endpoints/SystemsApiImplTest.kt
git commit -m "feat(sdk): add SystemsApi endpoint and navigateShip to FleetApi"
```

---

## Task 5: WaypointStateStore + Session Wiring

**Files:**
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/state/WaypointStateStore.kt`
- Modify: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/session/SpaceTradersSession.kt`
- Modify: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/session/SpaceTradersSessionImpl.kt`

- [ ] **Step 1: Create WaypointStateStore**

```kotlin
// spacetradersiosdk/src/commonMain/.../domain/state/WaypointStateStore.kt
package com.brokenhuskysledteam.spacetradersio.sdk.domain.state

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Waypoint

class WaypointStateStore : EntityStateStoreImpl<String, Waypoint>()
```

- [ ] **Step 2: Add waypointStateStore to SpaceTradersSession interface**

Add to `SpaceTradersSession.kt`:

```kotlin
val waypointStateStore: WaypointStateStore
```

Add import:
```kotlin
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.WaypointStateStore
```

- [ ] **Step 3: Add waypointStateStore to SpaceTradersSessionImpl**

Add to `SpaceTradersSessionImpl.kt`:

```kotlin
override val waypointStateStore = WaypointStateStore()
```

Add import:
```kotlin
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.WaypointStateStore
```

- [ ] **Step 4: Compile check**

Run: `gradlew.bat :spacetradersiosdk:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Run existing session tests to confirm no breakage**

Run: `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.domain.session.SessionManagerTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/state/WaypointStateStore.kt spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/session/SpaceTradersSession.kt spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/session/SpaceTradersSessionImpl.kt
git commit -m "feat(sdk): add WaypointStateStore and wire into session"
```

---

## Task 6: SystemRepository

**Files:**
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/repository/SystemRepository.kt`
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/SystemRepositoryImpl.kt`
- Test: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/SystemRepositoryImplTest.kt`

- [ ] **Step 1: Write SystemRepositoryImpl tests**

```kotlin
// spacetradersiosdk/src/androidHostTest/.../data/repository/SystemRepositoryImplTest.kt
package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.PaginatedResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MetaDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.WaypointDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.WaypointOrbitalDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.WaypointTraitDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.SystemsApi
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.WaypointStateStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun waypointDto(
    symbol: String,
    type: String = "PLANET",
    x: Int = 0,
    y: Int = 0,
    orbits: String? = null
) = WaypointDto(
    symbol = symbol,
    type = type,
    systemSymbol = "X1-DF55",
    x = x,
    y = y,
    orbits = orbits,
    orbitals = emptyList(),
    traits = emptyList(),
    isUnderConstruction = false
)

class SystemRepositoryImplTest {

    // ── single page ─────────────────────────────────────────────────────────

    @Test
    fun getSystemWaypoints_singlePage_returnsAllWaypoints() = runTest {
        val store = WaypointStateStore()
        val api = FakeSystemsApi(
            pages = mapOf(
                1 to PaginatedResponse(
                    data = listOf(waypointDto("WP-1"), waypointDto("WP-2")),
                    meta = MetaDto(total = 2, page = 1, limit = 20)
                )
            )
        )
        val repo = SystemRepositoryImpl(api, store)

        val result = repo.getSystemWaypoints("X1-DF55")
        assertEquals(2, result.size)
        assertEquals("WP-1", result[0].symbol)
        assertEquals("WP-2", result[1].symbol)
    }

    @Test
    fun getSystemWaypoints_singlePage_populatesStateStore() = runTest {
        val store = WaypointStateStore()
        val api = FakeSystemsApi(
            pages = mapOf(
                1 to PaginatedResponse(
                    data = listOf(waypointDto("WP-1")),
                    meta = MetaDto(total = 1, page = 1, limit = 20)
                )
            )
        )
        val repo = SystemRepositoryImpl(api, store)

        repo.getSystemWaypoints("X1-DF55")
        assertEquals("WP-1", store.entities.value["WP-1"]?.symbol)
    }

    // ── multi page ──────────────────────────────────────────────────────────

    @Test
    fun getSystemWaypoints_multiPage_fetchesAllPages() = runTest {
        val store = WaypointStateStore()
        val page1Data = (1..20).map { waypointDto("WP-$it") }
        val page2Data = (21..40).map { waypointDto("WP-$it") }
        val page3Data = (41..45).map { waypointDto("WP-$it") }

        val api = FakeSystemsApi(
            pages = mapOf(
                1 to PaginatedResponse(data = page1Data, meta = MetaDto(total = 45, page = 1, limit = 20)),
                2 to PaginatedResponse(data = page2Data, meta = MetaDto(total = 45, page = 2, limit = 20)),
                3 to PaginatedResponse(data = page3Data, meta = MetaDto(total = 45, page = 3, limit = 20))
            )
        )
        val repo = SystemRepositoryImpl(api, store)

        val result = repo.getSystemWaypoints("X1-DF55")
        assertEquals(45, result.size)
    }

    @Test
    fun getSystemWaypoints_multiPage_allPagesInStore() = runTest {
        val store = WaypointStateStore()
        val page1Data = (1..20).map { waypointDto("WP-$it") }
        val page2Data = (21..25).map { waypointDto("WP-$it") }

        val api = FakeSystemsApi(
            pages = mapOf(
                1 to PaginatedResponse(data = page1Data, meta = MetaDto(total = 25, page = 1, limit = 20)),
                2 to PaginatedResponse(data = page2Data, meta = MetaDto(total = 25, page = 2, limit = 20))
            )
        )
        val repo = SystemRepositoryImpl(api, store)

        repo.getSystemWaypoints("X1-DF55")
        assertEquals(25, store.entities.value.size)
    }

    @Test
    fun getSystemWaypoints_multiPage_requestsCorrectPages() = runTest {
        val store = WaypointStateStore()
        val api = FakeSystemsApi(
            pages = mapOf(
                1 to PaginatedResponse(data = (1..20).map { waypointDto("WP-$it") }, meta = MetaDto(total = 25, page = 1, limit = 20)),
                2 to PaginatedResponse(data = (21..25).map { waypointDto("WP-$it") }, meta = MetaDto(total = 25, page = 2, limit = 20))
            )
        )
        val repo = SystemRepositoryImpl(api, store)

        repo.getSystemWaypoints("X1-DF55")
        assertEquals(listOf(1, 2), api.requestedPages)
    }

    // ── empty system ────────────────────────────────────────────────────────

    @Test
    fun getSystemWaypoints_emptySystem_returnsEmptyList() = runTest {
        val store = WaypointStateStore()
        val api = FakeSystemsApi(
            pages = mapOf(
                1 to PaginatedResponse(data = emptyList(), meta = MetaDto(total = 0, page = 1, limit = 20))
            )
        )
        val repo = SystemRepositoryImpl(api, store)

        val result = repo.getSystemWaypoints("X1-DF55")
        assertTrue(result.isEmpty())
    }
}

private class FakeSystemsApi(
    private val pages: Map<Int, PaginatedResponse<WaypointDto>>
) : SystemsApi {
    val requestedPages = mutableListOf<Int>()

    override suspend fun getSystemWaypoints(
        systemSymbol: String,
        page: Int,
        limit: Int
    ): PaginatedResponse<WaypointDto> {
        requestedPages.add(page)
        return pages[page] ?: PaginatedResponse(emptyList(), MetaDto(total = 0, page = page, limit = limit))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.data.repository.SystemRepositoryImplTest"`
Expected: FAIL — `SystemRepositoryImpl` does not exist.

- [ ] **Step 3: Create SystemRepository interface**

```kotlin
// spacetradersiosdk/src/commonMain/.../domain/repository/SystemRepository.kt
package com.brokenhuskysledteam.spacetradersio.sdk.domain.repository

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Waypoint

interface SystemRepository {
    suspend fun getSystemWaypoints(systemSymbol: String): List<Waypoint>
}
```

- [ ] **Step 4: Create SystemRepositoryImpl**

```kotlin
// spacetradersiosdk/src/commonMain/.../data/repository/SystemRepositoryImpl.kt
package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.SystemsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Waypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.SystemRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.WaypointStateStore

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

- [ ] **Step 5: Run tests to verify they pass**

Run: `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.data.repository.SystemRepositoryImplTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/repository/SystemRepository.kt spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/SystemRepositoryImpl.kt spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/data/repository/SystemRepositoryImplTest.kt
git commit -m "feat(sdk): add SystemRepository with paginated waypoint fetching"
```

---

## Task 7: NavigateShipUseCase

**Files:**
- Create: `spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/NavigateShipUseCase.kt`
- Test: `spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/NavigateShipUseCaseTest.kt`

- [ ] **Step 1: Write NavigateShipUseCase tests**

```kotlin
// spacetradersiosdk/src/androidHostTest/.../domain/usecase/NavigateShipUseCaseTest.kt
package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApiImpl
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
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.testing.buildMockSpaceTradersClient
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private val NOW = Instant.parse("2025-06-01T10:00:00.000Z")
private val ARRIVAL = Instant.parse("2025-06-01T10:30:00.000Z")

private val testWaypoint = ShipNavRouteWaypoint("X1-DF55-20250Z", WaypointType.MOON, "X1-DF55", 0, 0)
private val testRoute = ShipNavRoute(
    origin = testWaypoint, destination = testWaypoint,
    departureTime = NOW, arrivalTime = NOW
)

private fun testShip(status: ShipNavStatus) = Ship(
    symbol = "LADD-1",
    registration = ShipRegistration(ShipRole.COMMAND, "COSMIC"),
    nav = ShipNav("X1-DF55", "X1-DF55-20250Z", status, ShipNavFlightMode.CRUISE, testRoute),
    cargo = ShipCargo(0, 40),
    fuel = ShipFuel(400, 400),
    frameName = "Shuttle Frame",
    cooldown = Cooldown("LADD-1", 0, 0, null)
)

private const val NAVIGATE_RESPONSE = """
{
  "data": {
    "nav": {
      "systemSymbol": "X1-DF55",
      "waypointSymbol": "X1-DF55-17335A",
      "route": {
        "destination": {"symbol":"X1-DF55-17335A","type":"PLANET","systemSymbol":"X1-DF55","x":-21,"y":-16},
        "origin": {"symbol":"X1-DF55-20250Z","type":"MOON","systemSymbol":"X1-DF55","x":0,"y":0},
        "departureTime": "2025-06-01T10:00:00.000Z",
        "arrival": "2025-06-01T10:30:00.000Z"
      },
      "status": "IN_TRANSIT",
      "flightMode": "CRUISE"
    },
    "fuel": {"current": 350, "capacity": 400},
    "events": []
  }
}
"""

private const val ORBIT_RESPONSE = """
{
  "data": {
    "nav": {
      "systemSymbol": "X1-DF55",
      "waypointSymbol": "X1-DF55-20250Z",
      "route": {
        "destination": {"symbol":"X1-DF55-20250Z","type":"MOON","systemSymbol":"X1-DF55","x":0,"y":0},
        "origin": {"symbol":"X1-DF55-20250Z","type":"MOON","systemSymbol":"X1-DF55","x":0,"y":0},
        "departureTime": "2025-06-01T10:00:00.000Z",
        "arrival": "2025-06-01T10:00:00.000Z"
      },
      "status": "IN_ORBIT",
      "flightMode": "CRUISE"
    }
  }
}
"""

private fun MockRequestHandleScope.okJson(content: String) = respond(
    content = content,
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
)

class NavigateShipUseCaseTest {

    private var orbitInvoked = false

    private fun buildUseCase(
        store: FleetStateStore = FleetStateStore(),
        fakeOrbit: OrbitShipUseCase = object : OrbitShipUseCase {
            override suspend fun invoke(shipSymbol: String): ShipNav {
                orbitInvoked = true
                val nav = ShipNav("X1-DF55", "X1-DF55-20250Z", ShipNavStatus.IN_ORBIT, ShipNavFlightMode.CRUISE, testRoute)
                store.update(shipSymbol) { it.copy(nav = nav) }
                return nav
            }
        }
    ): Pair<NavigateShipUseCaseImpl, FleetStateStore> {
        val client = buildMockSpaceTradersClient { okJson(NAVIGATE_RESPONSE) }
        val fleetApi = FleetApiImpl(client)
        return NavigateShipUseCaseImpl(fleetApi, store, fakeOrbit) to store
    }

    @Test
    fun invoke_shipInOrbit_navigatesDirectly() = runTest {
        orbitInvoked = false
        val store = FleetStateStore()
        store.put("LADD-1", testShip(ShipNavStatus.IN_ORBIT))
        val (useCase, _) = buildUseCase(store)

        val result = useCase("LADD-1", "X1-DF55-17335A")

        assertEquals(ShipNavStatus.IN_TRANSIT, result.nav.status)
        assertEquals(false, orbitInvoked)
    }

    @Test
    fun invoke_shipDocked_callsOrbitFirst() = runTest {
        orbitInvoked = false
        val store = FleetStateStore()
        store.put("LADD-1", testShip(ShipNavStatus.DOCKED))
        val (useCase, _) = buildUseCase(store)

        useCase("LADD-1", "X1-DF55-17335A")

        assertEquals(true, orbitInvoked)
    }

    @Test
    fun invoke_returnsNavigateResult() = runTest {
        val store = FleetStateStore()
        store.put("LADD-1", testShip(ShipNavStatus.IN_ORBIT))
        val (useCase, _) = buildUseCase(store)

        val result = useCase("LADD-1", "X1-DF55-17335A")

        assertEquals("X1-DF55-17335A", result.nav.waypointSymbol)
        assertEquals(350, result.fuel.current)
    }

    @Test
    fun invoke_updatesFleetStoreNavStatus() = runTest {
        val store = FleetStateStore()
        store.put("LADD-1", testShip(ShipNavStatus.IN_ORBIT))
        val (useCase, _) = buildUseCase(store)

        useCase("LADD-1", "X1-DF55-17335A")

        assertEquals(ShipNavStatus.IN_TRANSIT, store.entities.value["LADD-1"]?.nav?.status)
    }

    @Test
    fun invoke_updatesFleetStoreFuel() = runTest {
        val store = FleetStateStore()
        store.put("LADD-1", testShip(ShipNavStatus.IN_ORBIT))
        val (useCase, _) = buildUseCase(store)

        useCase("LADD-1", "X1-DF55-17335A")

        assertEquals(350, store.entities.value["LADD-1"]?.fuel?.current)
    }

    @Test
    fun invoke_shipNotInStore_skipsOrbit_navigatesAnyway() = runTest {
        orbitInvoked = false
        val store = FleetStateStore()
        val (useCase, _) = buildUseCase(store)

        val result = useCase("LADD-1", "X1-DF55-17335A")

        assertEquals(false, orbitInvoked)
        assertEquals(ShipNavStatus.IN_TRANSIT, result.nav.status)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.NavigateShipUseCaseTest"`
Expected: FAIL — `NavigateShipUseCaseImpl` does not exist.

- [ ] **Step 3: Create NavigateShipUseCase**

```kotlin
// spacetradersiosdk/src/commonMain/.../domain/usecase/NavigateShipUseCase.kt
package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.NavigateResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore

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
        val ship = fleetStateStore.entities.value[shipSymbol]
        if (ship?.nav?.status == ShipNavStatus.DOCKED) {
            orbitShipUseCase(shipSymbol)
        }

        val response = fleetApi.navigateShip(shipSymbol, waypointSymbol).toDomain()

        fleetStateStore.update(shipSymbol) { s ->
            s.copy(nav = response.nav, fuel = response.fuel)
        }

        return response
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :spacetradersiosdk:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.NavigateShipUseCaseTest"`
Expected: PASS

- [ ] **Step 5: Run all SDK tests to confirm no breakage**

Run: `gradlew.bat :spacetradersiosdk:allTests`
Expected: PASS — all existing and new tests pass.

- [ ] **Step 6: Commit**

```bash
git add spacetradersiosdk/src/commonMain/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/NavigateShipUseCase.kt spacetradersiosdk/src/androidHostTest/kotlin/com/brokenhuskysledteam/spacetradersio/sdk/domain/usecase/NavigateShipUseCaseTest.kt
git commit -m "feat(sdk): add NavigateShipUseCase with auto-orbit pre-flight"
```

---

## Task 8: DI Wiring + Navigation Routes

**Files:**
- Modify: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/di/SdkModule.kt`
- Modify: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/navigation/Routes.kt`
- Modify: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/navigation/NavigationTarget.kt`

- [ ] **Step 1: Add providers to SdkModule**

Add these providers to `SdkModule.kt`:

```kotlin
// --- Systems API ---

@Provides
@Singleton
fun provideSystemsApi(client: SpaceTradersClient): SystemsApi =
    SystemsApiImpl(client)

// --- Session-Scoped State ---

@Provides
fun provideWaypointStateStore(sm: SessionManager): WaypointStateStore =
    sm.requireSession().waypointStateStore

// --- System Repository ---

@Provides
fun provideSystemRepository(
    systemsApi: SystemsApi,
    waypointStateStore: WaypointStateStore
): SystemRepository = SystemRepositoryImpl(systemsApi, waypointStateStore)

// --- Navigate Use Case ---

@Provides
fun provideNavigateShipUseCase(
    fleetApi: FleetApi,
    fleetStateStore: FleetStateStore,
    orbitShipUseCase: OrbitShipUseCase
): NavigateShipUseCase = NavigateShipUseCaseImpl(fleetApi, fleetStateStore, orbitShipUseCase)
```

Add imports:
```kotlin
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.SystemsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.SystemsApiImpl
import com.brokenhuskysledteam.spacetradersio.sdk.data.repository.SystemRepositoryImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.SystemRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.WaypointStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.NavigateShipUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.NavigateShipUseCaseImpl
```

- [ ] **Step 2: Add SystemMapRoute to Routes.kt**

```kotlin
@Serializable
data class SystemMapRoute(
    val systemSymbol: String,
    val focusWaypointSymbol: String? = null,
    val shipSymbol: String? = null
)
```

- [ ] **Step 3: Add SystemMap to NavigationTarget.kt**

```kotlin
data class SystemMap(
    val systemSymbol: String,
    val focusWaypointSymbol: String? = null,
    val shipSymbol: String? = null
) : NavigationTarget
```

- [ ] **Step 4: Compile check**

Run: `gradlew.bat :app:compileDebugSources`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/brokenhuskysledteam/spacetradersio/di/SdkModule.kt app/src/main/java/com/brokenhuskysledteam/spacetradersio/navigation/Routes.kt app/src/main/java/com/brokenhuskysledteam/spacetradersio/navigation/NavigationTarget.kt
git commit -m "feat(app): add DI wiring and navigation routes for system map"
```

---

## Task 9: SystemMapViewModel + UiState

**Files:**
- Create: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/systemmap/SystemMapUiState.kt`
- Create: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/systemmap/SystemMapViewModel.kt`
- Test: `app/src/test/java/com/brokenhuskysledteam/spacetradersio/ui/systemmap/SystemMapViewModelTest.kt`

- [ ] **Step 1: Create SystemMapUiState**

```kotlin
// app/src/main/java/.../ui/systemmap/SystemMapUiState.kt
package com.brokenhuskysledteam.spacetradersio.ui.systemmap

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType

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

- [ ] **Step 2: Create SystemMapViewModel**

This is the largest file. See the spec (Section 7.2) for the full design. The ViewModel must implement:
- `combine(waypointStateStore.entities, fleetStateStore.entities, _localState)` triple-combine
- `buildTree()` — groups waypoints by `orbits` field, calculates distance, promotes orphaned children
- `applyFilters()` — type and trait filtering with empty = show all semantics
- `applySort()` — NAME = alphabetical, DISTANCE = ascending, orbitals always sorted by name
- `onEvent()` dispatcher for all `SystemMapEvent` variants
- `loadWaypoints()` called from `init`
- `navigate(waypointSymbol)` — calls `NavigateShipUseCase`, sets action result with fuel consumed/remaining

```kotlin
// app/src/main/java/.../ui/systemmap/SystemMapViewModel.kt
package com.brokenhuskysledteam.spacetradersio.ui.systemmap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Waypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.euclideanDistance
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.SystemRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.WaypointStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.NavigateShipUseCase
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
class SystemMapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val systemRepository: SystemRepository,
    private val fleetStateStore: FleetStateStore,
    private val waypointStateStore: WaypointStateStore,
    private val navigateShipUseCase: NavigateShipUseCase
) : ViewModel() {

    private val systemSymbol: String = checkNotNull(savedStateHandle["systemSymbol"])
    private val focusWaypointSymbol: String? = savedStateHandle["focusWaypointSymbol"]
    private val shipSymbol: String? = savedStateHandle["shipSymbol"]

    private val _localState = MutableStateFlow(LocalState())

    val uiState: StateFlow<SystemMapUiState> = combine(
        waypointStateStore.entities,
        fleetStateStore.entities,
        _localState
    ) { waypointMap, fleetMap, local ->
        val ship = shipSymbol?.let { fleetMap[it] }
        val shipSnapshot = ship?.toSnapshot()

        val allWaypoints = waypointMap.values.filter { it.systemSymbol == systemSymbol }
        val filtered = applyFilters(allWaypoints, local.activeTypeFilters, local.activeTraitFilters)

        val originX = when (local.distanceOrigin) {
            DistanceOrigin.SYSTEM_CENTER -> 0
            DistanceOrigin.SHIP_LOCATION -> shipSnapshot?.x ?: 0
        }
        val originY = when (local.distanceOrigin) {
            DistanceOrigin.SYSTEM_CENTER -> 0
            DistanceOrigin.SHIP_LOCATION -> shipSnapshot?.y ?: 0
        }

        val tree = buildTree(filtered, allWaypoints, originX, originY)
        val sorted = applySort(tree, local.sortMode)

        SystemMapUiState(
            systemSymbol = systemSymbol,
            waypoints = sorted,
            isLoading = local.isLoading,
            error = local.error,
            sortMode = local.sortMode,
            distanceOrigin = local.distanceOrigin,
            activeTypeFilters = local.activeTypeFilters,
            activeTraitFilters = local.activeTraitFilters,
            selectedShip = shipSnapshot,
            focusWaypointSymbol = focusWaypointSymbol,
            isActionInProgress = local.isActionInProgress,
            actionResult = local.actionResult
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SystemMapUiState())

    init {
        loadWaypoints()
    }

    fun onEvent(event: SystemMapEvent) {
        when (event) {
            is SystemMapEvent.RetryClicked -> loadWaypoints()
            is SystemMapEvent.SortModeSelected -> _localState.update { it.copy(sortMode = event.mode) }
            is SystemMapEvent.DistanceOriginSelected -> _localState.update { it.copy(distanceOrigin = event.origin) }
            is SystemMapEvent.TypeFilterToggled -> _localState.update { state ->
                val updated = state.activeTypeFilters.toMutableSet()
                if (event.type in updated) updated.remove(event.type) else updated.add(event.type)
                state.copy(activeTypeFilters = updated)
            }
            is SystemMapEvent.TraitFilterToggled -> _localState.update { state ->
                val updated = state.activeTraitFilters.toMutableSet()
                if (event.trait in updated) updated.remove(event.trait) else updated.add(event.trait)
                state.copy(activeTraitFilters = updated)
            }
            is SystemMapEvent.NavigateToWaypoint -> navigate(event.waypointSymbol)
            is SystemMapEvent.ActionResultDismissed -> _localState.update { it.copy(actionResult = null) }
        }
    }

    private fun loadWaypoints() {
        _localState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                systemRepository.getSystemWaypoints(systemSymbol)
                _localState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _localState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load waypoints")
                }
            }
        }
    }

    private fun navigate(waypointSymbol: String) {
        val ship = shipSymbol ?: return
        _localState.update { it.copy(isActionInProgress = true, actionResult = null, error = null) }
        viewModelScope.launch {
            try {
                val result = navigateShipUseCase(ship, waypointSymbol)
                val fuelBefore = fleetStateStore.entities.value[ship]?.fuel?.current ?: result.fuel.current
                _localState.update {
                    it.copy(
                        isActionInProgress = false,
                        actionResult = SystemMapActionResult.NavigationStarted(
                            destinationSymbol = waypointSymbol,
                            fuelConsumed = fuelBefore - result.fuel.current,
                            fuelRemaining = result.fuel.current
                        )
                    )
                }
            } catch (e: Exception) {
                _localState.update {
                    it.copy(isActionInProgress = false, error = e.message ?: "Navigation failed")
                }
            }
        }
    }

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
}

private fun applyFilters(
    waypoints: Collection<Waypoint>,
    typeFilters: Set<WaypointType>,
    traitFilters: Set<WaypointTraitSymbol>
): List<Waypoint> {
    var result = waypoints.toList()
    if (typeFilters.isNotEmpty()) {
        result = result.filter { it.type in typeFilters }
    }
    if (traitFilters.isNotEmpty()) {
        result = result.filter { wp -> wp.traits.any { it.symbol in traitFilters } }
    }
    return result
}

private fun buildTree(
    filtered: List<Waypoint>,
    allWaypoints: Collection<Waypoint>,
    originX: Int,
    originY: Int
): List<WaypointNode> {
    val filteredSymbols = filtered.map { it.symbol }.toSet()
    val roots = mutableListOf<WaypointNode>()

    for (wp in filtered) {
        if (wp.orbits == null || wp.orbits !in filteredSymbols) {
            val children = filtered
                .filter { it.orbits == wp.symbol }
                .map { it.toNode(originX, originY, emptyList()) }
            roots.add(wp.toNode(originX, originY, children))
        }
    }
    return roots
}

private fun applySort(nodes: List<WaypointNode>, sortMode: SortMode): List<WaypointNode> {
    val sorted = when (sortMode) {
        SortMode.NAME -> nodes.sortedBy { it.waypoint.symbol }
        SortMode.DISTANCE -> nodes.sortedBy { it.distance }
    }
    return sorted.map { node ->
        node.copy(orbitals = node.orbitals.sortedBy { it.waypoint.symbol })
    }
}

private fun Waypoint.toNode(originX: Int, originY: Int, children: List<WaypointNode>): WaypointNode {
    val traitSymbols = traits.map { it.symbol }
    return WaypointNode(
        waypoint = WaypointSummary(
            symbol = symbol,
            type = type,
            x = x,
            y = y,
            traits = traitSymbols,
            hasMarketplace = WaypointTraitSymbol.MARKETPLACE in traitSymbols,
            hasShipyard = WaypointTraitSymbol.SHIPYARD in traitSymbols,
            isUncharted = WaypointTraitSymbol.UNCHARTED in traitSymbols,
            isUnderConstruction = isUnderConstruction
        ),
        distance = euclideanDistance(originX, originY, x, y),
        orbitals = children
    )
}

private fun Ship.toSnapshot(): ShipSnapshot = ShipSnapshot(
    symbol = symbol,
    waypointSymbol = nav.waypointSymbol,
    x = nav.route.destination.x,
    y = nav.route.destination.y,
    fuelCurrent = fuel.current,
    fuelCapacity = fuel.capacity,
    navStatus = nav.status
)
```

- [ ] **Step 3: Write SystemMapViewModelTest**

```kotlin
// app/src/test/java/.../ui/systemmap/SystemMapViewModelTest.kt
package com.brokenhuskysledteam.spacetradersio.ui.systemmap

import androidx.lifecycle.SavedStateHandle
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Cooldown
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.NavigateResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipCargo
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRoute
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRouteWaypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipRegistration
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Waypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.WaypointTrait
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavFlightMode
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipRole
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.SystemRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.WaypointStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.NavigateShipUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val NOW = Instant.parse("2025-06-01T10:00:00.000Z")

private fun fakeWaypoint(
    symbol: String,
    type: WaypointType = WaypointType.PLANET,
    x: Int = 0,
    y: Int = 0,
    orbits: String? = null,
    traits: List<WaypointTrait> = emptyList()
) = Waypoint(
    symbol = symbol, type = type, systemSymbol = "X1-DF55",
    x = x, y = y, orbits = orbits, orbitals = emptyList(),
    traits = traits, isUnderConstruction = false
)

private fun fakeShip(
    symbol: String = "LADD-1",
    status: ShipNavStatus = ShipNavStatus.IN_ORBIT,
    waypointSymbol: String = "X1-DF55-20250Z",
    fuel: ShipFuel = ShipFuel(400, 400)
): Ship {
    val wp = ShipNavRouteWaypoint(waypointSymbol, WaypointType.MOON, "X1-DF55", 0, 0)
    return Ship(
        symbol = symbol,
        registration = ShipRegistration(ShipRole.COMMAND, "COSMIC"),
        nav = ShipNav("X1-DF55", waypointSymbol, status, ShipNavFlightMode.CRUISE,
            ShipNavRoute(wp, wp, NOW, NOW)),
        cargo = ShipCargo(0, 40),
        fuel = fuel,
        frameName = "Shuttle Frame",
        cooldown = Cooldown(symbol, 0, 0, null)
    )
}

private class FakeSystemRepository(
    private val store: WaypointStateStore,
    var waypoints: List<Waypoint> = emptyList(),
    var shouldThrow: Boolean = false
) : SystemRepository {
    override suspend fun getSystemWaypoints(systemSymbol: String): List<Waypoint> {
        if (shouldThrow) throw RuntimeException("Network error")
        store.putAll(waypoints.associateBy { it.symbol })
        return waypoints
    }
}

private class FakeNavigateShipUseCase(
    private val fleetStore: FleetStateStore,
    var result: NavigateResult? = null,
    var shouldThrow: Boolean = false
) : NavigateShipUseCase {
    var lastShipSymbol: String? = null
    var lastWaypointSymbol: String? = null

    override suspend fun invoke(shipSymbol: String, waypointSymbol: String): NavigateResult {
        lastShipSymbol = shipSymbol
        lastWaypointSymbol = waypointSymbol
        if (shouldThrow) throw RuntimeException("Navigation failed")
        val r = result ?: throw IllegalStateException("No result configured")
        fleetStore.update(shipSymbol) { it.copy(nav = r.nav, fuel = r.fuel) }
        return r
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SystemMapViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var waypointStore: WaypointStateStore
    private lateinit var fleetStore: FleetStateStore
    private lateinit var systemRepo: FakeSystemRepository
    private lateinit var navigateUseCase: FakeNavigateShipUseCase

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        waypointStore = WaypointStateStore()
        fleetStore = FleetStateStore()
        systemRepo = FakeSystemRepository(waypointStore)
        navigateUseCase = FakeNavigateShipUseCase(fleetStore)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        systemSymbol: String = "X1-DF55",
        focusWaypointSymbol: String? = null,
        shipSymbol: String? = null
    ): SystemMapViewModel {
        val savedState = mutableMapOf<String, Any?>("systemSymbol" to systemSymbol)
        if (focusWaypointSymbol != null) savedState["focusWaypointSymbol"] = focusWaypointSymbol
        if (shipSymbol != null) savedState["shipSymbol"] = shipSymbol
        return SystemMapViewModel(
            savedStateHandle = SavedStateHandle(savedState),
            systemRepository = systemRepo,
            fleetStateStore = fleetStore,
            waypointStateStore = waypointStore,
            navigateShipUseCase = navigateUseCase
        )
    }

    // ── loading ─────────────────────────────────────────────────────────────

    @Test
    fun init_loadsWaypoints_setsLoadingFalse() = runTest {
        systemRepo.waypoints = listOf(fakeWaypoint("WP-1"))
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun init_loadsWaypoints_displaysWaypoints() = runTest {
        systemRepo.waypoints = listOf(fakeWaypoint("WP-1"), fakeWaypoint("WP-2"))
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, vm.uiState.value.waypoints.size)
    }

    @Test
    fun init_loadError_setsErrorMessage() = runTest {
        systemRepo.shouldThrow = true
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    // ── sorting ─────────────────────────────────────────────────────────────

    @Test
    fun sortByName_ordersAlphabetically() = runTest {
        systemRepo.waypoints = listOf(fakeWaypoint("WP-B"), fakeWaypoint("WP-A"))
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(SystemMapEvent.SortModeSelected(SortMode.NAME))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("WP-A", vm.uiState.value.waypoints[0].waypoint.symbol)
        assertEquals("WP-B", vm.uiState.value.waypoints[1].waypoint.symbol)
    }

    @Test
    fun sortByDistance_ordersFromSystemCenter() = runTest {
        systemRepo.waypoints = listOf(
            fakeWaypoint("FAR", x = 100, y = 100),
            fakeWaypoint("NEAR", x = 1, y = 1)
        )
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(SystemMapEvent.SortModeSelected(SortMode.DISTANCE))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("NEAR", vm.uiState.value.waypoints[0].waypoint.symbol)
        assertEquals("FAR", vm.uiState.value.waypoints[1].waypoint.symbol)
    }

    @Test
    fun distanceOriginShipLocation_changesDistances() = runTest {
        fleetStore.put("LADD-1", fakeShip(waypointSymbol = "WP-SHIP"))
        systemRepo.waypoints = listOf(
            fakeWaypoint("WP-A", x = 0, y = 0),
            fakeWaypoint("WP-B", x = 10, y = 10)
        )
        val vm = createViewModel(shipSymbol = "LADD-1")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(SystemMapEvent.DistanceOriginSelected(DistanceOrigin.SHIP_LOCATION))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(DistanceOrigin.SHIP_LOCATION, state.distanceOrigin)
    }

    // ── type filtering ──────────────────────────────────────────────────────

    @Test
    fun typeFilter_toggleOn_filtersWaypoints() = runTest {
        systemRepo.waypoints = listOf(
            fakeWaypoint("PLANET-1", type = WaypointType.PLANET),
            fakeWaypoint("GATE-1", type = WaypointType.JUMP_GATE)
        )
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(SystemMapEvent.TypeFilterToggled(WaypointType.PLANET))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.uiState.value.waypoints.size)
        assertEquals("PLANET-1", vm.uiState.value.waypoints[0].waypoint.symbol)
    }

    @Test
    fun typeFilter_toggleOff_showsAll() = runTest {
        systemRepo.waypoints = listOf(
            fakeWaypoint("PLANET-1", type = WaypointType.PLANET),
            fakeWaypoint("GATE-1", type = WaypointType.JUMP_GATE)
        )
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(SystemMapEvent.TypeFilterToggled(WaypointType.PLANET))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(SystemMapEvent.TypeFilterToggled(WaypointType.PLANET))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, vm.uiState.value.waypoints.size)
    }

    // ── trait filtering ─────────────────────────────────────────────────────

    @Test
    fun traitFilter_marketplace_filtersCorrectly() = runTest {
        systemRepo.waypoints = listOf(
            fakeWaypoint("WITH-MARKET", traits = listOf(
                WaypointTrait(WaypointTraitSymbol.MARKETPLACE, "Marketplace", "A marketplace")
            )),
            fakeWaypoint("NO-MARKET")
        )
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(SystemMapEvent.TraitFilterToggled(WaypointTraitSymbol.MARKETPLACE))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.uiState.value.waypoints.size)
        assertEquals("WITH-MARKET", vm.uiState.value.waypoints[0].waypoint.symbol)
    }

    // ── tree hierarchy ──────────────────────────────────────────────────────

    @Test
    fun moonNestsUnderPlanet() = runTest {
        systemRepo.waypoints = listOf(
            fakeWaypoint("PLANET-1", type = WaypointType.PLANET),
            fakeWaypoint("MOON-1", type = WaypointType.MOON, orbits = "PLANET-1")
        )
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val nodes = vm.uiState.value.waypoints
        assertEquals(1, nodes.size)
        assertEquals("PLANET-1", nodes[0].waypoint.symbol)
        assertEquals(1, nodes[0].orbitals.size)
        assertEquals("MOON-1", nodes[0].orbitals[0].waypoint.symbol)
    }

    @Test
    fun moonPromotesToRootWhenParentFiltered() = runTest {
        systemRepo.waypoints = listOf(
            fakeWaypoint("PLANET-1", type = WaypointType.PLANET),
            fakeWaypoint("MOON-1", type = WaypointType.MOON, orbits = "PLANET-1")
        )
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(SystemMapEvent.TypeFilterToggled(WaypointType.MOON))
        testDispatcher.scheduler.advanceUntilIdle()

        val nodes = vm.uiState.value.waypoints
        assertEquals(1, nodes.size)
        assertEquals("MOON-1", nodes[0].waypoint.symbol)
        assertTrue(nodes[0].orbitals.isEmpty())
    }

    // ── navigation action ───────────────────────────────────────────────────

    @Test
    fun navigateToWaypoint_setsActionResult() = runTest {
        val destWp = ShipNavRouteWaypoint("X1-DF55-17335A", WaypointType.PLANET, "X1-DF55", -21, -16)
        val originWp = ShipNavRouteWaypoint("X1-DF55-20250Z", WaypointType.MOON, "X1-DF55", 0, 0)
        navigateUseCase.result = NavigateResult(
            nav = ShipNav("X1-DF55", "X1-DF55-17335A", ShipNavStatus.IN_TRANSIT, ShipNavFlightMode.CRUISE,
                ShipNavRoute(originWp, destWp, NOW, NOW)),
            fuel = ShipFuel(350, 400)
        )
        fleetStore.put("LADD-1", fakeShip())
        systemRepo.waypoints = listOf(fakeWaypoint("X1-DF55-17335A"))
        val vm = createViewModel(shipSymbol = "LADD-1")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(SystemMapEvent.NavigateToWaypoint("X1-DF55-17335A"))
        testDispatcher.scheduler.advanceUntilIdle()

        val result = assertIs<SystemMapActionResult.NavigationStarted>(vm.uiState.value.actionResult)
        assertEquals("X1-DF55-17335A", result.destinationSymbol)
        assertEquals(350, result.fuelRemaining)
    }

    @Test
    fun navigateToWaypoint_error_setsError() = runTest {
        navigateUseCase.shouldThrow = true
        fleetStore.put("LADD-1", fakeShip())
        systemRepo.waypoints = listOf(fakeWaypoint("WP-1"))
        val vm = createViewModel(shipSymbol = "LADD-1")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(SystemMapEvent.NavigateToWaypoint("WP-1"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isActionInProgress)
    }

    @Test
    fun actionResultDismissed_clearsResult() = runTest {
        val wp = ShipNavRouteWaypoint("WP-1", WaypointType.PLANET, "X1-DF55", 0, 0)
        navigateUseCase.result = NavigateResult(
            nav = ShipNav("X1-DF55", "WP-1", ShipNavStatus.IN_TRANSIT, ShipNavFlightMode.CRUISE,
                ShipNavRoute(wp, wp, NOW, NOW)),
            fuel = ShipFuel(350, 400)
        )
        fleetStore.put("LADD-1", fakeShip())
        systemRepo.waypoints = listOf(fakeWaypoint("WP-1"))
        val vm = createViewModel(shipSymbol = "LADD-1")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(SystemMapEvent.NavigateToWaypoint("WP-1"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(vm.uiState.value.actionResult)

        vm.onEvent(SystemMapEvent.ActionResultDismissed)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.actionResult)
    }

    // ── focus waypoint ──────────────────────────────────────────────────────

    @Test
    fun focusWaypointSymbol_passedThroughToState() = runTest {
        systemRepo.waypoints = listOf(fakeWaypoint("WP-1"))
        val vm = createViewModel(focusWaypointSymbol = "WP-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("WP-1", vm.uiState.value.focusWaypointSymbol)
    }

    // ── ship snapshot ───────────────────────────────────────────────────────

    @Test
    fun selectedShip_populatedFromFleetStore() = runTest {
        fleetStore.put("LADD-1", fakeShip())
        systemRepo.waypoints = listOf(fakeWaypoint("WP-1"))
        val vm = createViewModel(shipSymbol = "LADD-1")
        testDispatcher.scheduler.advanceUntilIdle()

        val ship = assertNotNull(vm.uiState.value.selectedShip)
        assertEquals("LADD-1", ship.symbol)
        assertEquals(400, ship.fuelCurrent)
    }

    @Test
    fun noShipSymbol_selectedShipIsNull() = runTest {
        systemRepo.waypoints = listOf(fakeWaypoint("WP-1"))
        val vm = createViewModel(shipSymbol = null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.uiState.value.selectedShip)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.brokenhuskysledteam.spacetradersio.ui.systemmap.SystemMapViewModelTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/systemmap/SystemMapUiState.kt app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/systemmap/SystemMapViewModel.kt app/src/test/java/com/brokenhuskysledteam/spacetradersio/ui/systemmap/SystemMapViewModelTest.kt
git commit -m "feat(app): add SystemMapViewModel with sort, filter, and navigate"
```

---

## Task 10: SystemMapScreen Composable

**Files:**
- Create: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/systemmap/SystemMapScreen.kt`
- Modify: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/navigation/SpaceTradersNavHost.kt`

- [ ] **Step 1: Create SystemMapScreen**

Build the full Compose UI following the hierarchy from the spec (Section 8). Key composables:
- `SystemMapScreen` — stateful wrapper with `hiltViewModel()` + `collectAsStateWithLifecycle()`
- `SystemMapScreenContent` — stateless, receives `uiState` + `onEvent`
- `SortBar` — toggle buttons for sort mode + distance origin
- `FilterChipRow` — horizontally scrollable row of `TerminalButton` toggles
- `WaypointRow` — displays one `WaypointNode` with indentation, distance, trait badges, navigate button
- Use existing components: `TerminalCard`, `TerminalButton`, `TerminalProgressBar`, `ScanlineOverlay`
- Follow terminal theme: green-on-black, monospace, uppercase headers

```kotlin
// app/src/main/java/.../ui/systemmap/SystemMapScreen.kt
package com.brokenhuskysledteam.spacetradersio.ui.systemmap

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import com.brokenhuskysledteam.spacetradersio.ui.components.ScanlineOverlay
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalButton
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalCard

@Composable
fun SystemMapScreen(
    onNavigateBack: () -> Unit,
    viewModel: SystemMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SystemMapScreenContent(uiState = uiState, onEvent = viewModel::onEvent)
}

@Composable
fun SystemMapScreenContent(
    uiState: SystemMapUiState,
    onEvent: (SystemMapEvent) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            uiState.error != null && uiState.waypoints.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(48.dp))
                    TerminalCard(title = "Error") {
                        Text(text = uiState.error, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        TerminalButton(
                            text = "Retry",
                            onClick = { onEvent(SystemMapEvent.RetryClicked) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(48.dp))
                        Text(
                            text = "SYSTEM: ${uiState.systemSymbol}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        uiState.selectedShip?.let { ship ->
                            Text(
                                text = "// ${ship.symbol} @ ${ship.waypointSymbol} | FUEL: ${ship.fuelCurrent}/${ship.fuelCapacity}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        SortBar(uiState = uiState, onEvent = onEvent)
                        Spacer(modifier = Modifier.height(8.dp))
                        FilterChipRow(uiState = uiState, onEvent = onEvent)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (uiState.error != null) {
                        item {
                            TerminalCard(title = "Error", borderColor = MaterialTheme.colorScheme.error) {
                                Text(text = uiState.error, color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    if (uiState.actionResult != null) {
                        item {
                            ActionResultCard(result = uiState.actionResult, onDismiss = { onEvent(SystemMapEvent.ActionResultDismissed) })
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    items(uiState.waypoints, key = { it.waypoint.symbol }) { node ->
                        WaypointRow(
                            node = node,
                            isCurrentLocation = node.waypoint.symbol == uiState.focusWaypointSymbol,
                            showNavigateButton = uiState.selectedShip != null
                                && node.waypoint.symbol != uiState.selectedShip.waypointSymbol
                                && uiState.selectedShip.navStatus != com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus.IN_TRANSIT,
                            isActionInProgress = uiState.isActionInProgress,
                            indentLevel = 0,
                            onNavigate = { onEvent(SystemMapEvent.NavigateToWaypoint(it)) }
                        )
                    }
                }
            }
        }
        ScanlineOverlay()
    }
}

@Composable
private fun SortBar(uiState: SystemMapUiState, onEvent: (SystemMapEvent) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        TerminalButton(
            text = if (uiState.sortMode == SortMode.NAME) "[NAME]" else "NAME",
            onClick = { onEvent(SystemMapEvent.SortModeSelected(SortMode.NAME)) },
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        TerminalButton(
            text = if (uiState.sortMode == SortMode.DISTANCE) "[DISTANCE]" else "DISTANCE",
            onClick = { onEvent(SystemMapEvent.SortModeSelected(SortMode.DISTANCE)) },
            modifier = Modifier.weight(1f)
        )
    }
    if (uiState.sortMode == SortMode.DISTANCE) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            TerminalButton(
                text = if (uiState.distanceOrigin == DistanceOrigin.SYSTEM_CENTER) "[CENTER]" else "CENTER",
                onClick = { onEvent(SystemMapEvent.DistanceOriginSelected(DistanceOrigin.SYSTEM_CENTER)) },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            TerminalButton(
                text = if (uiState.distanceOrigin == DistanceOrigin.SHIP_LOCATION) "[SHIP]" else "SHIP",
                onClick = { onEvent(SystemMapEvent.DistanceOriginSelected(DistanceOrigin.SHIP_LOCATION)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FilterChipRow(uiState: SystemMapUiState, onEvent: (SystemMapEvent) -> Unit) {
    val typeFilters = listOf(WaypointType.PLANET, WaypointType.GAS_GIANT, WaypointType.ASTEROID_FIELD, WaypointType.JUMP_GATE)
    val traitFilters = listOf(WaypointTraitSymbol.MARKETPLACE, WaypointTraitSymbol.SHIPYARD, WaypointTraitSymbol.UNCHARTED)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        typeFilters.forEach { type ->
            val active = type in uiState.activeTypeFilters
            TerminalButton(
                text = if (active) "[${type.name}]" else type.name,
                onClick = { onEvent(SystemMapEvent.TypeFilterToggled(type)) }
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        traitFilters.forEach { trait ->
            val active = trait in uiState.activeTraitFilters
            TerminalButton(
                text = if (active) "[${trait.name}]" else trait.name,
                onClick = { onEvent(SystemMapEvent.TraitFilterToggled(trait)) }
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

@Composable
private fun WaypointRow(
    node: WaypointNode,
    isCurrentLocation: Boolean,
    showNavigateButton: Boolean,
    isActionInProgress: Boolean,
    indentLevel: Int,
    onNavigate: (String) -> Unit
) {
    val wp = node.waypoint
    TerminalCard(
        title = wp.symbol,
        modifier = Modifier.padding(start = (indentLevel * 24).dp, bottom = 8.dp)
    ) {
        Text(
            text = "TYPE: ${wp.type.name} | DIST: ${"%.1f".format(node.distance)} AU",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "COORDS: (${wp.x}, ${wp.y})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        if (wp.hasMarketplace || wp.hasShipyard || wp.isUncharted) {
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                if (wp.hasMarketplace) {
                    Text(text = "[MARKET] ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                if (wp.hasShipyard) {
                    Text(text = "[SHIPYARD] ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                if (wp.isUncharted) {
                    Text(text = "[UNCHARTED] ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        if (isCurrentLocation) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "> YOU ARE HERE",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        if (showNavigateButton) {
            Spacer(modifier = Modifier.height(8.dp))
            TerminalButton(
                text = if (isActionInProgress) "..." else "Navigate",
                onClick = { onNavigate(wp.symbol) },
                enabled = !isActionInProgress,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    node.orbitals.forEach { orbital ->
        WaypointRow(
            node = orbital,
            isCurrentLocation = orbital.waypoint.symbol == node.waypoint.symbol,
            showNavigateButton = showNavigateButton,
            isActionInProgress = isActionInProgress,
            indentLevel = indentLevel + 1,
            onNavigate = onNavigate
        )
    }
}

@Composable
private fun ActionResultCard(result: SystemMapActionResult, onDismiss: () -> Unit) {
    val amberColor = MaterialTheme.colorScheme.tertiary
    TerminalCard(title = "Command Output", borderColor = amberColor) {
        when (result) {
            is SystemMapActionResult.NavigationStarted -> {
                Text(text = "> NAVIGATION INITIATED", style = MaterialTheme.typography.bodyMedium, color = amberColor)
                Text(text = "  DESTINATION: ${result.destinationSymbol}", style = MaterialTheme.typography.bodyMedium, color = amberColor)
                Text(text = "  FUEL CONSUMED: ${result.fuelConsumed}", style = MaterialTheme.typography.bodyMedium, color = amberColor)
                Text(text = "  FUEL REMAINING: ${result.fuelRemaining}", style = MaterialTheme.typography.bodyMedium, color = amberColor)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        TerminalButton(
            text = "Dismiss",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
```

- [ ] **Step 2: Wire SystemMapScreen into NavHost**

Update `SpaceTradersNavHost.kt`:

Add import:
```kotlin
import com.brokenhuskysledteam.spacetradersio.ui.systemmap.SystemMapScreen
```

Add new composable destination after the `ShipDetailRoute` composable:
```kotlin
composable<SystemMapRoute> {
    SystemMapScreen(onNavigateBack = { navController.popBackStack() })
}
```

Update the `ShipDetailRoute` composable to pass the navigation callback:
```kotlin
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

Add import:
```kotlin
import com.brokenhuskysledteam.spacetradersio.navigation.SystemMapRoute
```

- [ ] **Step 3: Compile check**

Run: `gradlew.bat :app:compileDebugSources`
Expected: BUILD SUCCESSFUL (the `ShipDetailScreen` callback will fail until Task 11 — if it does, temporarily keep the existing `ShipDetailScreen()` call and update it in Task 11)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/systemmap/SystemMapScreen.kt app/src/main/java/com/brokenhuskysledteam/spacetradersio/navigation/SpaceTradersNavHost.kt
git commit -m "feat(app): add SystemMapScreen with sort, filter, and waypoint hierarchy"
```

---

## Task 11: ShipDetailScreen "View System" Button

**Files:**
- Modify: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/ships/ShipDetailUiState.kt`
- Modify: `app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/ships/ShipDetailScreen.kt`

- [ ] **Step 1: Add ViewSystemClicked event to ShipDetailUiState**

Add to `ShipDetailEvent` sealed interface in `ShipDetailUiState.kt`:

```kotlin
data class ViewSystemClicked(
    val systemSymbol: String,
    val waypointSymbol: String,
    val shipSymbol: String
) : ShipDetailEvent
```

- [ ] **Step 2: Update ShipDetailScreen to accept navigation callback**

Update the stateful `ShipDetailScreen` composable signature:

```kotlin
@Composable
fun ShipDetailScreen(
    onNavigateToSystemMap: (systemSymbol: String, waypointSymbol: String, shipSymbol: String) -> Unit,
    viewModel: ShipDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShipDetailScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateToSystemMap = onNavigateToSystemMap
    )
}
```

Update `ShipDetailScreenContent` to accept and use the callback:

```kotlin
@Composable
fun ShipDetailScreenContent(
    uiState: ShipDetailUiState,
    onEvent: (ShipDetailEvent) -> Unit,
    onNavigateToSystemMap: (systemSymbol: String, waypointSymbol: String, shipSymbol: String) -> Unit
)
```

In the `NavigationCard` composable, add a "View System" button after the location rows:

```kotlin
Spacer(modifier = Modifier.height(8.dp))
TerminalButton(
    text = "View System",
    onClick = {
        onNavigateToSystemMap(ship.systemSymbol, ship.waypointSymbol, ship.symbol)
    },
    modifier = Modifier.fillMaxWidth()
)
```

Note: The `NavigationCard` is a private composable. You'll need to pass the `onNavigateToSystemMap` callback down through the composable hierarchy, or restructure by moving the button into the main `ShipDetailScreenContent` body next to the `NavigationCard` call.

- [ ] **Step 3: Compile check**

Run: `gradlew.bat :app:compileDebugSources`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run all existing app tests to confirm no breakage**

Run: `gradlew.bat :app:testDebugUnitTest`
Expected: PASS — the existing `ShipDetailViewModelTest` tests don't exercise `ViewSystemClicked` but shouldn't break.

Note: If existing tests fail because `ShipDetailScreen` now requires `onNavigateToSystemMap`, verify they're testing `ShipDetailViewModel` directly (not the composable). The composable signature change doesn't affect ViewModel tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/ships/ShipDetailUiState.kt app/src/main/java/com/brokenhuskysledteam/spacetradersio/ui/ships/ShipDetailScreen.kt
git commit -m "feat(app): add View System button to ship detail screen"
```

---

## Task 12: Final Integration & Verification

- [ ] **Step 1: Run all SDK tests**

Run: `gradlew.bat :spacetradersiosdk:allTests`
Expected: PASS

- [ ] **Step 2: Run all app tests**

Run: `gradlew.bat :app:testDebugUnitTest`
Expected: PASS

- [ ] **Step 3: Build debug APK**

Run: `gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Manual verification on emulator**

1. Launch app on emulator
2. Navigate to Ship Detail screen
3. Tap "View System" button — should open SystemMapScreen
4. Verify waypoints are listed with correct types and distances
5. Test sort by Name and Distance toggles
6. Test filter chips (PLANET, MARKETPLACE, etc.)
7. Verify moon waypoints nest under their parent planet
8. Tap "Navigate" on a waypoint — verify navigation action result appears
9. Verify "YOU ARE HERE" indicator on the ship's current waypoint

- [ ] **Step 5: Commit any fixes from manual testing, if needed**

---

## Verification Summary

| What | Command | Expected |
|---|---|---|
| SDK unit tests | `gradlew.bat :spacetradersiosdk:allTests` | PASS |
| App unit tests | `gradlew.bat :app:testDebugUnitTest` | PASS |
| Debug APK build | `gradlew.bat :app:assembleDebug` | BUILD SUCCESSFUL |
| Manual emulator test | See Task 12 Step 4 | All UI flows work |
