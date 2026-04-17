package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository

/**
 * Transitions a ship to DOCKED status and keeps the local ship cache consistent.
 *
 * **Pattern:** Callable use case with interface + Impl split. The interface allows
 * ViewModels that depend on this use case to be tested with a hand-written fake —
 * no mocking library required. `operator fun invoke()` means callers write
 * `dockShipUseCase(shipSymbol)` rather than the more verbose `.invoke(shipSymbol)`.
 * In a new project, use this interface + Impl pattern for any use case that is
 * directly injected into a ViewModel you need to unit-test.
 *
 * **Cross-layer update pattern (adds real value):** The SpaceTraders API returns the
 * updated [ShipNav] in the dock response but does not push that change to observers
 * automatically. This use case handles the two-step sequence that every caller would
 * otherwise have to duplicate:
 * 1. Call `fleetApi.dockShip()` to perform the state change on the server.
 * 2. Call `fleetRepository.updateShipNav()` to persist the new nav state locally so the
 *    in-memory [FleetRepository] (backed by [EntityStateStore]) reflects reality without
 *    a full re-fetch.
 *
 * Because [FleetRepository] exposes the ship list as a `StateFlow`, any ViewModel that
 * observes ship state will automatically receive the updated nav status as soon as step 2
 * completes — no manual UI refresh required.
 *
 * **In this project:** Called from `ShipDetailViewModel` when the user taps "Dock".
 * The ViewModel calls the use case and trusts that the reactive flow will update the UI.
 *
 * @see OrbitShipUseCase for the symmetric operation that transitions a ship to IN_ORBIT.
 */
interface DockShipUseCase {
    /**
     * Docks the specified ship and updates the local nav cache.
     *
     * @param shipSymbol The unique identifier of the ship to dock (e.g. `"AGENT-1"`).
     * @return The updated [ShipNav] reflecting the DOCKED status and current waypoint.
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.api.exception.SpaceTradersApiException
     *   If the ship is already docked, is in transit, or the API returns any other error.
     */
    suspend operator fun invoke(shipSymbol: String): ShipNav
}

/**
 * Production implementation of [DockShipUseCase].
 *
 * @param fleetApi Live Ktor-backed API client for fleet operations.
 * @param fleetRepository In-memory ship store; updated after a successful API call so that
 *   reactive observers receive the new nav state without a separate fetch.
 */
class DockShipUseCaseImpl(
    private val fleetApi: FleetApi,
    private val fleetRepository: FleetRepository
) : DockShipUseCase {
    override suspend operator fun invoke(shipSymbol: String): ShipNav {
        val nav = fleetApi.dockShip(shipSymbol).toDomain()
        // Write the new nav state to the local cache so that StateFlow observers (e.g.
        // ShipDetailViewModel) receive the updated status reactively without a full re-fetch.
        fleetRepository.updateShipNav(shipSymbol, nav)
        return nav
    }
}
