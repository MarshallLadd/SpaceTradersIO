package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

// Mapper: NavigateResponseDto → NavigateResult
// All DTO-to-domain conversions for the NavigateResult entity live here.
// Sub-object mapping is delegated to ShipMapper (ShipNavDto and ShipFuelDto).

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.NavigateResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.NavigateResult

/**
 * Maps this [NavigateResponseDto] to a [NavigateResult] domain model.
 *
 * **Pattern:** Delegating extension function mapper. When a response DTO is composed of
 * sub-objects that are also mapped independently elsewhere, the top-level mapper delegates
 * to the sub-mappers rather than duplicating the field assignments. This keeps each mapper
 * focused on its own level of the object tree and ensures that [ShipNav] and [ShipFuel]
 * are always constructed the same way, whether they originate from a full ship response
 * or a navigation response. In a new project, use delegation whenever sub-DTOs are shared
 * across multiple response shapes.
 *
 * **In this project:** `POST /my/ships/{symbol}/navigate` returns only the fields that
 * change during transit — the updated nav state and the remaining fuel — rather than the
 * full ship. [NavigateResponseDto] wraps [ShipNavDto] and [ShipFuelDto], and this mapper
 * delegates their conversion to [ShipMapper.toDomain] extensions already defined for
 * those types. The [ShipNavDto] conversion chain in particular is multi-level:
 * `nav.toDomain()` → `route.toDomain()` → `origin.toDomain()` / `destination.toDomain()`,
 * including [Instant.parse] for departure and arrival timestamps.
 *
 * @return The domain model built from this DTO's data, with nav and fuel sub-objects fully
 *   mapped to their domain equivalents.
 */
fun NavigateResponseDto.toDomain(): NavigateResult = NavigateResult(
    // Delegates to ShipNavDto.toDomain() defined in ShipMapper.kt, which in turn
    // delegates to ShipNavRouteDto.toDomain() and ShipNavRouteWaypointDto.toDomain().
    nav = nav.toDomain(),
    // Delegates to ShipFuelDto.toDomain() defined in ShipMapper.kt.
    fuel = fuel.toDomain()
)
