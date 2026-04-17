package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

/**
 * Shared wire-format response for both `POST /my/ships/{symbol}/orbit` and
 * `POST /my/ships/{symbol}/dock`.
 *
 * **Pattern:** Shared response DTO. When two endpoints return structurally identical JSON, a
 * single DTO covers both. In a new project, resist the temptation to duplicate the class just
 * to give it a per-endpoint name — one DTO with a descriptive name is cleaner. If the shapes
 * diverge later, split them then.
 *
 * **In this project:** Both orbit and dock only change the ship's navigation state (`status`
 * flips between `"IN_ORBIT"` and `"DOCKED"`), so the API returns the same `{ "data": { "nav":
 * ShipNav } }` envelope for both. The same `OrbitDockResponseDto` is decoded for both calls,
 * and `ShipNavDto.toDomain()` converts the result to the `ShipNav` domain model.
 *
 * **Gotcha:** Although the API spec shows no request body for these endpoints, both require
 * `setBody("{}")` in the Ktor call. The `SpaceTradersClient` sets `Content-Type: application/json`
 * globally via `defaultRequest`; sending that header with an empty body causes a 422 from the
 * API. An explicit `"{}"` satisfies the server's JSON body requirement.
 *
 * @property nav The ship's updated navigation state after the orbit or dock command was accepted.
 * The `status` field will be `"IN_ORBIT"` after orbit and `"DOCKED"` after dock.
 */
@Serializable
data class OrbitDockResponseDto(
    val nav: ShipNavDto
)
