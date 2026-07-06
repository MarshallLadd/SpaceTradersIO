package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

// Mapper: ShipDto → Ship (and all nested ship sub-types)
// This file is the root of the ship mapper tree. Each extension function handles one level
// of the Ship domain model hierarchy, delegating downward to sub-mappers as needed.
//
// Mapper tree (each arrow = "calls toDomain() on"):
//
//   ShipDto
//     ├─ ShipRegistrationDto
//     ├─ ShipNavDto
//     │    └─ ShipNavRouteDto
//     │         ├─ ShipNavRouteWaypointDto  (origin)
//     │         └─ ShipNavRouteWaypointDto  (destination)
//     ├─ ShipCargoDto
//     ├─ ShipFuelDto
//     └─ CooldownDto
//
// NavigateMapper.kt reuses ShipNavDto.toDomain() and ShipFuelDto.toDomain() from here,
// keeping navigation-response conversion consistent with full-ship-response conversion.

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.CargoItemDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.CooldownDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipCargoDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipFuelDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavRouteDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavRouteWaypointDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipRegistrationDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.CargoItem
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

/**
 * Maps this [ShipDto] to a [Ship] domain model.
 *
 * **Pattern:** Recursive mapper tree. The ship domain model is a multi-level hierarchy
 * (ship → nav → route → waypoints; ship → cargo; ship → fuel; ship → cooldown). Rather than
 * implementing the entire conversion in one function, each sub-object has its own `toDomain()`
 * extension. The top-level mapper calls sub-mappers for each field, each sub-mapper does the
 * same for its own nested objects, and so on. In a new project, apply this decomposition
 * whenever the domain model has more than two levels of nesting — it keeps each function
 * focused on exactly one level and makes individual sub-objects reusable across multiple
 * response shapes (see [NavigateMapper]).
 *
 * **In this project:** [ShipDto] is returned by `GET /my/ships` (paginated list) and
 * `GET /my/ships/{symbol}` (single ship). The `frameName` field is extracted from the nested
 * `frame` sub-object because the domain model only needs the frame's display name, not the
 * entire frame spec.
 *
 * @return The fully mapped [Ship] domain model, with all nested sub-objects converted.
 */
fun ShipDto.toDomain(): Ship = Ship(
    symbol = symbol,
    registration = registration.toDomain(),
    nav = nav.toDomain(),
    cargo = cargo.toDomain(),
    fuel = fuel.toDomain(),
    // Only the frame name is needed in the domain model; the rest of the frame spec
    // (power output, module slots, mount points, etc.) is not used by current UI.
    frameName = frame.name,
    cooldown = cooldown.toDomain()
)

/**
 * Maps this [ShipRegistrationDto] to a [ShipRegistration] domain model.
 *
 * **Pattern:** Sub-mapper. Each nested DTO type in the ship hierarchy gets its own mapper
 * function, called by the parent mapper. This mirrors the domain model tree in a 1:1 mapping
 * of function depth to object depth.
 *
 * **In this project:** Registration captures the ship's role (e.g., `HAULER`, `COMMAND`)
 * and the faction that registered it. [ShipRole.fromString] converts the raw API string to
 * the typed enum, preventing magic strings from reaching the domain layer.
 *
 * @return The domain model built from this DTO's registration data.
 */
fun ShipRegistrationDto.toDomain(): ShipRegistration = ShipRegistration(
    // ShipRole.fromString uses safe parsing — unknown role strings become ShipRole.UNKNOWN
    // rather than throwing, so new roles added by the API don't crash the client.
    role = ShipRole.fromString(role),
    factionSymbol = factionSymbol
)

/**
 * Maps this [ShipNavDto] to a [ShipNav] domain model.
 *
 * **Pattern:** Sub-mapper with further delegation. [ShipNavDto] itself contains a route
 * sub-object ([ShipNavRouteDto]), so this mapper delegates to [ShipNavRouteDto.toDomain]
 * for that field, continuing the recursive mapper tree.
 *
 * **In this project:** [ShipNavDto] is shared across two response types — the full ship
 * response and the navigate response — so this mapper is called by both [ShipDto.toDomain]
 * and [NavigateResponseDto.toDomain]. Keeping it as an independent extension ensures both
 * paths produce identical [ShipNav] values.
 *
 * [ShipNavStatus.fromString] and [ShipNavFlightMode.fromString] use safe parsing so new
 * status/mode strings from the API don't throw at runtime.
 *
 * @return The domain model built from this DTO's nav data.
 */
fun ShipNavDto.toDomain(): ShipNav = ShipNav(
    systemSymbol = systemSymbol,
    waypointSymbol = waypointSymbol,
    status = ShipNavStatus.fromString(status),
    flightMode = ShipNavFlightMode.fromString(flightMode),
    route = route.toDomain()
)

/**
 * Maps this [ShipNavRouteDto] to a [ShipNavRoute] domain model.
 *
 * **Pattern:** Sub-mapper with timestamp parsing. This is the level of the ship mapper tree
 * where ISO-8601 timestamp strings are converted to [kotlin.time.Instant]. All timestamp
 * parsing for ship nav data happens here — not in the DTO (which keeps them as raw strings),
 * not in the domain model (which only sees Instant), and not in repositories or ViewModels.
 *
 * **In this project:** The route records both the departure and expected arrival of a transit.
 * The [arrivalTime] is used by `RefreshScheduler` to schedule a timer that fires when the
 * ship completes transit, triggering a state refresh.
 *
 * **Field rename:** The API field is named `arrival`; the domain model exposes it as
 * `arrivalTime` for clarity. The rename happens here, at the boundary between wire format
 * and domain model.
 *
 * @return The domain model built from this DTO's route data, with timestamps as [Instant].
 */
fun ShipNavRouteDto.toDomain(): ShipNavRoute = ShipNavRoute(
    origin = origin.toDomain(),
    destination = destination.toDomain(),
    // Timestamp conversion: the DTO holds raw ISO-8601 strings; the domain model holds Instant.
    departureTime = Instant.parse(departureTime),
    // The API field is "arrival"; the domain model exposes it as "arrivalTime" for clarity.
    arrivalTime = Instant.parse(arrival)
)

/**
 * Maps this [ShipNavRouteWaypointDto] to a [ShipNavRouteWaypoint] domain model.
 *
 * **Pattern:** Leaf mapper. This is the deepest level of the ship mapper tree — it has no
 * further nested sub-objects to delegate to. Leaf mappers are typically straightforward
 * property assignments, with only enum parsing as a non-trivial step.
 *
 * **In this project:** The route waypoint describes a point in space (x, y, system symbol)
 * along with its waypoint type. [WaypointType.fromString] converts the raw API string
 * (e.g. `"ASTEROID_FIELD"`) to the typed enum. This same DTO type is used for both the
 * route's `origin` and `destination` fields.
 *
 * @return The domain model built from this DTO's waypoint data.
 */
fun ShipNavRouteWaypointDto.toDomain(): ShipNavRouteWaypoint = ShipNavRouteWaypoint(
    symbol = symbol,
    // WaypointType.fromString uses safe parsing — unrecognized type strings produce
    // WaypointType.UNKNOWN rather than throwing.
    type = WaypointType.fromString(type),
    systemSymbol = systemSymbol,
    x = x,
    y = y
)

/**
 * Maps this [ShipFuelDto] to a [ShipFuel] domain model.
 *
 * **Pattern:** Leaf mapper. Simple field pass-through — no type conversions needed.
 *
 * **In this project:** [ShipFuelDto] is shared across two response types — the full ship
 * response and the navigate response — so this mapper is reused by both [ShipDto.toDomain]
 * and [NavigateResponseDto.toDomain].
 *
 * @return The domain model built from this DTO's fuel data.
 */
fun ShipFuelDto.toDomain(): ShipFuel = ShipFuel(
    current = current,
    capacity = capacity
)

/**
 * Maps this [ShipCargoDto] to a [ShipCargo] domain model.
 *
 * **Pattern:** Leaf mapper. Simple field pass-through — no type conversions needed.
 *
 * **In this project:** Cargo state drives both a fill-level bar (`units / capacity`) and an
 * itemised manifest in the UI. The aggregate counts map directly; each [inventory] entry is
 * delegated to [CargoItemDto.toDomain].
 *
 * @return The domain model built from this DTO's cargo data.
 */
fun ShipCargoDto.toDomain(): ShipCargo = ShipCargo(
    units = units,
    capacity = capacity,
    inventory = inventory.map { it.toDomain() }
)

/**
 * Maps this [CargoItemDto] to a [CargoItem] domain model.
 *
 * **Pattern:** Leaf mapper. Simple field pass-through — no type conversions needed. Called
 * by [ShipCargoDto.toDomain] for each entry in the cargo inventory array.
 *
 * @return The domain model built from this DTO's cargo-item data.
 */
fun CargoItemDto.toDomain(): CargoItem = CargoItem(
    symbol = symbol,
    name = name,
    description = description,
    units = units
)

/**
 * Maps this [CooldownDto] to a [Cooldown] domain model.
 *
 * **Pattern:** Leaf mapper with nullable timestamp handling. The cooldown expiration is
 * `null` when there is no active cooldown (the field is absent in the JSON). Propagating
 * the nullability through to the domain model lets callers check `cooldown.expiration != null`
 * to determine whether the ship is currently on cooldown.
 *
 * **In this project:** [CooldownDto] is included in the full ship response. The [expiration]
 * field, when present, is an ISO-8601 timestamp string that is parsed to [Instant] here —
 * consistent with the timestamp handling pattern used throughout the ship mapper tree.
 *
 * @return The domain model built from this DTO's cooldown data.
 */
fun CooldownDto.toDomain(): Cooldown = Cooldown(
    shipSymbol = shipSymbol,
    totalSeconds = totalSeconds,
    remainingSeconds = remainingSeconds,
    // expiration is null when the ship has no active cooldown (the API omits the field).
    // The safe-call + let pattern avoids parsing a null string while preserving nullability.
    expiration = expiration?.let { Instant.parse(it) }
)
