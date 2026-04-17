package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository

/**
 * Transitions a ship to IN_ORBIT status and keeps the local ship cache consistent.
 *
 * **Pattern:** Callable use case with interface + Impl split. The interface allows
 * ViewModels that depend on this use case to be tested with a hand-written fake — no
 * mocking library required. `operator fun invoke()` means callers write
 * `orbitShipUseCase(shipSymbol)` rather than `.invoke(shipSymbol)`. In a new project,
 * apply this interface + Impl pattern for any use case that is directly injected into a
 * class you need to unit-test.
 *
 * **Cross-layer update pattern (adds real value):** Like [DockShipUseCase], the orbit
 * operation follows a two-step sequence that every caller would otherwise duplicate:
 * 1. Call `fleetApi.orbitShip()` to move the ship into orbit on the server.
 * 2. Call `fleetRepository.updateShipNav()` to persist the new nav state locally so that
 *    in-memory observers reflect the change immediately.
 *
 * Because [FleetRepository] exposes ship state as a `StateFlow`, any ViewModel observing
 * the ship list receives the updated nav status reactively — no manual re-fetch needed.
 *
 * **Role in multi-step orchestration:** [OrbitShipUseCase] is injected as a dependency
 * into [NavigateShipUseCaseImpl]. Navigate requires the ship to be in orbit; the navigate
 * use case calls this use case automatically when it detects a DOCKED ship. This
 * composability (use cases calling use cases) keeps the orbit precondition in one place.
 *
 * **In this project:** Called from `ShipDetailViewModel` when the user taps "Orbit", and
 * called internally by [NavigateShipUseCase] as a precondition step before navigation.
 *
 * @see DockShipUseCase for the symmetric operation that transitions a ship to DOCKED.
 * @see NavigateShipUseCase for the use case that depends on this one as a precondition.
 */
interface OrbitShipUseCase {
    /**
     * Puts the specified ship into orbit and updates the local nav cache.
     *
     * @param shipSymbol The unique identifier of the ship to orbit (e.g. `"AGENT-1"`).
     * @return The updated [ShipNav] reflecting the IN_ORBIT status and current waypoint.
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.api.exception.SpaceTradersApiException
     *   If the ship is already in orbit, is in transit, or the API returns any other error.
     */
    suspend operator fun invoke(shipSymbol: String): ShipNav
}

/**
 * Production implementation of [OrbitShipUseCase].
 *
 * @param fleetApi Live Ktor-backed API client for fleet operations.
 * @param fleetRepository In-memory ship store; updated after a successful API call so that
 *   reactive observers receive the new nav state without a separate fetch.
 */
class OrbitShipUseCaseImpl(
    private val fleetApi: FleetApi,
    private val fleetRepository: FleetRepository
) : OrbitShipUseCase {
    override suspend operator fun invoke(shipSymbol: String): ShipNav {
        val nav = fleetApi.orbitShip(shipSymbol).toDomain()
        // Write the new nav state to the local cache so that StateFlow observers (e.g.
        // ShipDetailViewModel) receive the updated status reactively without a full re-fetch.
        // NavigateShipUseCaseImpl also relies on this update to avoid stale nav state after
        // the implicit orbit step it triggers before calling navigateShip.
        fleetRepository.updateShipNav(shipSymbol, nav)
        return nav
    }
}
