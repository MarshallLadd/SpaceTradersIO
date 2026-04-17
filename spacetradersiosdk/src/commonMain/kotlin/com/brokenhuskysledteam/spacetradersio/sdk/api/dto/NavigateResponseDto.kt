package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

/**
 * Wire-format response from `POST /my/ships/{symbol}/navigate`.
 *
 * **Pattern:** Response DTO. A dedicated `@Serializable` data class is used to represent the
 * JSON shape returned by one specific endpoint. In a new project, create one response DTO per
 * endpoint (or per reused response shape) and never share them with domain models.
 *
 * **In this project:** The navigate endpoint returns two updated resources: the ship's new nav
 * state (system, waypoint, status, flight mode, and route) and the fuel consumed on the trip.
 * Both sub-DTOs are mapped to domain models by `NavigateMapper.toDomain()`.
 *
 * @property nav The ship's updated navigation state after the navigate command was accepted,
 * including the in-progress route and an `arrivalTime` the client can use to schedule a
 * transit-complete refresh.
 * @property fuel The ship's updated fuel levels after subtracting the fuel cost of the route.
 */
@Serializable
data class NavigateResponseDto(
    val nav: ShipNavDto,
    val fuel: ShipFuelDto
)

/**
 * Wire-format request body for `POST /my/ships/{symbol}/navigate`.
 *
 * **Pattern:** Request body DTO. Rather than building a raw JSON string in the call site, a
 * typed DTO is passed to Ktor's `setBody(dto)`. Ktor serializes it automatically when the
 * `ContentNegotiation` plugin is configured with `kotlinx.serialization`. In a new project,
 * create a matching request DTO for every endpoint that accepts a JSON body — it keeps the
 * call-site code readable and ensures the shape is validated at compile time.
 *
 * **In this project:** Navigation requires only the destination waypoint symbol. Using a
 * one-field DTO (instead of `setBody("""{"waypointSymbol":"$symbol"}""")`) keeps the API
 * function readable and lets the serializer handle escaping and encoding.
 *
 * @property waypointSymbol The symbol of the destination waypoint (e.g. `"X1-AB12-CC34X"`).
 */
@Serializable
data class NavigateRequestDto(val waypointSymbol: String)
