package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

/**
 * Wire-format representation of the SpaceTraders API `Ship` schema.
 *
 * **Pattern:** Nested DTO tree. The SpaceTraders `Ship` object is itself composed of
 * several sub-objects (registration, frame, nav, cargo, fuel, cooldown). Each sub-object
 * is modelled as its own `@Serializable` data class that mirrors the JSON nesting exactly.
 * The top-level [ShipDto] holds references to those sub-DTOs, forming a tree that the
 * JSON deserializer reconstructs in one pass. The mapper (`ShipMapper.kt`) then walks the
 * tree and produces the flat or restructured domain model. In a new project, mirror the
 * JSON nesting faithfully in the DTO layer — let the mapper handle any restructuring into
 * a shape that is convenient for business logic.
 *
 * **In this project:** `ShipDto` is returned by `GET /my/ships` (inside
 * [PaginatedResponse]), `GET /my/ships/{symbol}`, and embedded in [RegisterResponseDto].
 * Many sub-DTOs (e.g. [ShipNavDto], [ShipFuelDto], [ShipCargoDto]) are also reused
 * individually in action-response DTOs (navigate, orbit, dock, refuel). The mapper lives
 * at `api/mapper/ShipMapper.kt`.
 *
 * **Ignored fields:** The SpaceTraders `Ship` JSON contains additional top-level fields
 * (e.g. `engine`, `reactor`, `modules`, `mounts`, `crew`). These are not modelled here
 * because the current app has no UI or domain need for them. The `SpaceTradersClient`
 * configures the JSON deserializer with `ignoreUnknownKeys = true`, so their presence in
 * the response does not cause errors.
 *
 * @property symbol The ship's unique identifier (e.g. `"COSMIC_HAULER-1"`). Used as the
 *   path parameter in all per-ship endpoints (`/orbit`, `/dock`, `/navigate`, etc.).
 * @property registration Static information about the ship's registration, including its
 *   display name, owning faction, and assigned role. See [ShipRegistrationDto].
 * @property frame The ship's hull type and display name. See [ShipFrameSummaryDto].
 * @property nav The ship's current navigation state: which system/waypoint it is at (or
 *   heading to), its status, flight mode, and active route. See [ShipNavDto].
 * @property cargo The ship's current cargo hold summary: capacity and occupied units.
 *   See [ShipCargoDto].
 * @property fuel The ship's current fuel levels. See [ShipFuelDto].
 * @property cooldown The ship's cooldown state after an action (e.g. scanning, extracting).
 *   See [CooldownDto].
 */
@Serializable
data class ShipDto(
    val symbol: String,
    val registration: ShipRegistrationDto,
    val frame: ShipFrameSummaryDto,
    val nav: ShipNavDto,
    val cargo: ShipCargoDto,
    val fuel: ShipFuelDto,
    val cooldown: CooldownDto
)

/**
 * Static registration information for a ship.
 *
 * **Pattern:** Nested value DTO. Groups logically related static fields that describe
 * the ship's registered identity rather than its dynamic game state.
 *
 * **In this project:** Mapped to `ShipRegistration` in the domain layer. [role] is a raw
 * string (e.g. `"COMMAND"`, `"HAULER"`) — the mapper converts it to the typed `ShipRole`
 * enum.
 *
 * @property name The ship's display name as registered (e.g. `"Cosmic Hauler"`). Different
 *   from [ShipDto.symbol] which is the machine-readable identifier.
 * @property factionSymbol The faction this ship is registered under. Typically matches the
 *   agent's [AgentDto.startingFaction].
 * @property role The ship's operational role as a raw string (e.g. `"COMMAND"`,
 *   `"HAULER"`, `"SATELLITE"`). The mapper converts this to the typed `ShipRole` enum so
 *   the domain layer never works with magic strings.
 */
@Serializable
data class ShipRegistrationDto(
    val name: String,
    val factionSymbol: String,
    // Raw string enum: e.g. "COMMAND", "HAULER". Mapper converts to ShipRole enum.
    val role: String
)

/**
 * A summary of the ship's hull (frame) type.
 *
 * **Pattern:** Partial DTO (ignored-fields variant). The SpaceTraders API returns many more
 * fields inside the `frame` object (condition, integrity, description, module slots, etc.).
 * Only the fields needed by the current app UI are modelled here; the rest are silently
 * dropped by the JSON deserializer's `ignoreUnknownKeys = true` setting. In a new project,
 * start with only the fields you need and expand later — adding a field to a DTO is a
 * backward-compatible change.
 *
 * **In this project:** [symbol] and [name] are sufficient to label the ship in the list
 * and detail screens.
 *
 * @property symbol The frame's stable identifier (e.g. `"FRAME_MINER"`, `"FRAME_FRIGATE"`).
 * @property name The frame's human-readable display name (e.g. `"Mining Frame"`).
 */
@Serializable
data class ShipFrameSummaryDto(
    val symbol: String,
    val name: String
)

// The API also returns condition, integrity, description, moduleSlots,
// mountingPoints, fuelCapacity, requirements, and quality inside the frame
// object — all ignored here via ignoreUnknownKeys = true on the JSON config.

/**
 * The ship's current navigation state.
 *
 * **Pattern:** Nested state DTO. Navigation state is a coherent sub-object in the JSON and
 * in domain terms — grouping it into its own DTO means action-response types
 * ([OrbitDockResponseDto], [NavigateResponseDto]) can reference [ShipNavDto] directly
 * without duplicating the fields.
 *
 * **In this project:** Reused across [ShipDto], [OrbitDockResponseDto], and
 * [NavigateResponseDto]. The mapper converts [status] and [flightMode] from raw strings to
 * their typed enum equivalents (`ShipNavStatus`, `ShipFlightMode`).
 *
 * @property systemSymbol The star system the ship is currently in or departing from
 *   (e.g. `"X1-OE"`).
 * @property waypointSymbol The waypoint the ship is currently at or heading to
 *   (e.g. `"X1-OE-PM"`). During transit, this is the **destination** waypoint.
 * @property route The active route including origin, destination, departure time, and
 *   arrival time. See [ShipNavRouteDto].
 * @property status The ship's current status as a raw string: `"IN_TRANSIT"`,
 *   `"IN_ORBIT"`, or `"DOCKED"`. The mapper converts this to the typed `ShipNavStatus`
 *   enum.
 * @property flightMode The ship's current flight mode as a raw string: `"DRIFT"`,
 *   `"STEALTH"`, `"CRUISE"`, or `"BURN"`. The mapper converts this to the typed
 *   `ShipFlightMode` enum.
 */
@Serializable
data class ShipNavDto(
    val systemSymbol: String,
    val waypointSymbol: String,
    val route: ShipNavRouteDto,
    // Raw string enum: "IN_TRANSIT", "IN_ORBIT", "DOCKED". Mapper converts to ShipNavStatus.
    val status: String,
    // Raw string enum: "DRIFT", "STEALTH", "CRUISE", "BURN". Mapper converts to ShipFlightMode.
    val flightMode: String
)

/**
 * The ship's active navigation route from origin to destination.
 *
 * **Pattern:** Nested value DTO. Route data is a cohesive sub-object covering the two
 * endpoints of a trip and its timing. Grouping it here makes the parent [ShipNavDto] less
 * cluttered and lets mappers reference the route as a unit.
 *
 * **In this project:** Both timestamp fields are ISO-8601 strings in the wire format; the
 * mapper converts them to `kotlinx.datetime.Instant` for the domain model. The domain
 * model exposes `arrivalTime` (not `arrival`) as the property name — the `@SerialName`
 * annotation (implicitly handled by the field name match) keeps the DTO aligned to the JSON
 * while the mapper renames it for domain clarity. Note that the API field is literally
 * `"arrival"`, not `"arrivalTime"` — this field name asymmetry between the DTO and the
 * domain model is intentional and documented on the property below.
 *
 * @property destination The waypoint the ship is navigating to. See [ShipNavRouteWaypointDto].
 * @property origin The waypoint the ship departed from. See [ShipNavRouteWaypointDto].
 * @property departureTime The moment the ship began this route, as an ISO-8601 string.
 *   The mapper converts this to `kotlinx.datetime.Instant`.
 * @property arrival The moment the ship will arrive at [destination], as an ISO-8601 string.
 *   The API field is named `"arrival"` (not `"arrivalTime"`). The mapper converts this to
 *   `kotlinx.datetime.Instant` and exposes it as `arrivalTime` in the domain model.
 *   `RefreshScheduler` uses this value to schedule the transit-complete state refresh.
 */
@Serializable
data class ShipNavRouteDto(
    val destination: ShipNavRouteWaypointDto,
    val origin: ShipNavRouteWaypointDto,
    val departureTime: String,
    // The API field is "arrival", not "arrivalTime". The mapper exposes it as arrivalTime.
    val arrival: String
)

/**
 * A waypoint reference embedded in a navigation route.
 *
 * **Pattern:** Inline waypoint summary DTO. Rather than returning only a symbol and
 * requiring a follow-up `GET /systems/{system}/waypoints/{waypoint}` call, the API embeds
 * the waypoint's type and coordinates directly in the route. This is enough for the UI to
 * display route information without an extra round trip.
 *
 * **In this project:** Used for both [ShipNavRouteDto.origin] and
 * [ShipNavRouteDto.destination]. The [type] field is a raw string — the mapper converts it
 * to the typed `WaypointType` enum.
 *
 * @property symbol The waypoint's unique identifier (e.g. `"X1-OE-PM"`).
 * @property type The waypoint's classification as a raw string (e.g. `"PLANET"`,
 *   `"ASTEROID_FIELD"`). The mapper converts this to the typed `WaypointType` enum.
 * @property systemSymbol The star system this waypoint belongs to (e.g. `"X1-OE"`).
 * @property x The waypoint's X coordinate within its star system. Used for distance
 *   calculations to estimate travel time and fuel cost.
 * @property y The waypoint's Y coordinate within its star system.
 */
@Serializable
data class ShipNavRouteWaypointDto(
    val symbol: String,
    // Raw string enum: e.g. "PLANET", "ASTEROID_FIELD". Mapper converts to WaypointType.
    val type: String,
    val systemSymbol: String,
    val x: Int,
    val y: Int
)

/**
 * The ship's cargo hold summary.
 *
 * **Pattern:** Partial DTO (ignored-fields variant). The SpaceTraders API returns an
 * `inventory` array inside the cargo object listing each distinct item in the hold. This
 * project does not yet model inventory items, so only the aggregate [capacity] and [units]
 * are captured here. The deserializer silently drops the `inventory` array.
 *
 * **In this project:** Reused across [ShipDto] and [DeliverCargoResponseDto]. The UI uses
 * these two fields to render a cargo capacity bar on the ship detail screen.
 *
 * @property capacity The maximum number of cargo units this ship's hold can carry.
 * @property units The number of cargo units currently occupying the hold. The hold is full
 *   when `units == capacity`.
 */
@Serializable
data class ShipCargoDto(
    val capacity: Int,
    val units: Int
)

// The API also returns an "inventory" array inside cargo — ignored via ignoreUnknownKeys.

/**
 * The ship's current fuel levels.
 *
 * **Pattern:** Partial DTO (ignored-fields variant). The SpaceTraders API also returns a
 * `consumed` object inside fuel that details how much fuel the last manoeuvre used. This
 * project does not yet surface that data, so only [current] and [capacity] are captured.
 *
 * **In this project:** Reused across [ShipDto], [NavigateResponseDto], and
 * [RefuelResponseDto]. The UI uses [current] and [capacity] to render a fuel gauge on the
 * ship detail screen.
 *
 * @property current The ship's current fuel level in fuel units. Decreases with each
 *   navigation action; restored by the refuel endpoint.
 * @property capacity The maximum fuel the ship's tank can hold. Determined by the ship's
 *   frame and reactor. The tank is full when `current == capacity`.
 */
@Serializable
data class ShipFuelDto(
    val current: Int,
    val capacity: Int
)

// The API optionally returns a "consumed" object inside fuel — ignored via ignoreUnknownKeys.

/**
 * The ship's cooldown state after an action that triggers a cooldown (e.g. scan, extract).
 *
 * **Pattern:** Nullable-field DTO for optional server state. `expiration` is `null` when
 * there is no active cooldown (i.e. `remainingSeconds == 0`). Modelling it as a nullable
 * default means a ship with no active cooldown deserializes correctly without special-casing.
 *
 * **In this project:** The ship detail screen displays the remaining cooldown seconds and
 * a timer that counts down to zero. When `remainingSeconds == 0` and `expiration == null`,
 * the ship is ready for its next action.
 *
 * @property shipSymbol The symbol of the ship this cooldown applies to. Matches
 *   [ShipDto.symbol].
 * @property totalSeconds The total duration (in seconds) of the cooldown that was
 *   originally applied. Used to render a progress bar (`remainingSeconds / totalSeconds`).
 * @property remainingSeconds The number of seconds until the cooldown expires and the
 *   ship can act again. `0` when there is no active cooldown.
 * @property expiration The ISO-8601 timestamp at which the cooldown ends. `null` when
 *   `remainingSeconds == 0` (no active cooldown). The mapper converts this to
 *   `kotlinx.datetime.Instant` when present.
 */
@Serializable
data class CooldownDto(
    val shipSymbol: String,
    val totalSeconds: Int,
    val remainingSeconds: Int,
    // Null when remainingSeconds == 0 (no active cooldown). ISO-8601 string when present;
    // mapper converts to Instant.
    val expiration: String? = null
)
