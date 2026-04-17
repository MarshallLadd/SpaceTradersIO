package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.NavigateResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import kotlinx.coroutines.flow.first

/**
 * Navigates a ship to a target waypoint, automatically satisfying the orbit precondition
 * and keeping both the nav and fuel state consistent in the local cache.
 *
 * **Pattern:** Callable use case with interface + Impl split. `operator fun invoke()` means
 * callers write `navigateShipUseCase(shipSymbol, waypointSymbol)` naturally. The interface
 * enables ViewModel tests that substitute a hand-written fake — the ViewModel's navigation
 * logic is exercised without a live network or real repositories. In a new project, always
 * use the interface + Impl split for use cases with multiple dependencies that callers need
 * to fake out in tests.
 *
 * **The most valuable use case in this project.** Navigation requires four steps that every
 * caller would otherwise need to know and repeat:
 *
 * 1. **Read current nav status** — observe the ship from [FleetRepository] to check whether
 *    it is DOCKED. The SpaceTraders API rejects navigate calls from a docked ship.
 * 2. **Auto-orbit if needed** — if the ship is DOCKED, call [OrbitShipUseCase] before
 *    proceeding. This encapsulates the orbit precondition: without this use case, every
 *    caller of `fleetApi.navigateShip()` would have to implement the same check-and-orbit
 *    sequence, duplicating the business rule across the codebase.
 * 3. **Call the navigate API** — `POST /my/ships/{shipSymbol}/navigate` with the target waypoint.
 * 4. **Update both caches** — the API response contains a new [com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav]
 *    (the ship is now IN_TRANSIT) and updated [com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel]
 *    (fuel consumed for the trip). Both are written to [FleetRepository] so that reactive
 *    StateFlow observers (e.g. `ShipDetailViewModel`) update without a separate fetch.
 *
 * **Why inject [OrbitShipUseCase] rather than [FleetApi].orbitShip directly?**
 * [OrbitShipUseCase] also updates [FleetRepository] with the post-orbit nav state. Re-using
 * the use case here means the intermediate orbit step is fully recorded in the local cache,
 * keeping the ship's nav history consistent even during the brief DOCKED → IN_ORBIT →
 * IN_TRANSIT transition. Calling the API directly would bypass that cache write.
 *
 * **In this project:** Called from `ShipDetailViewModel` when the user taps "Navigate".
 * The ViewModel passes the chosen waypoint symbol and awaits the [NavigateResult]. The
 * reactive fleet StateFlow then drives the UI transition to the in-transit state (including
 * the countdown timer and fuel bar update) without any explicit refresh in the ViewModel.
 *
 * @param fleetApi Live Ktor-backed API client. The navigate endpoint is authenticated and
 *   requires the ship to be in orbit (not docked, not already in transit).
 * @param fleetRepository In-memory ship store. Read to determine the ship's current nav
 *   status; written with updated nav and fuel after a successful navigation call.
 * @param orbitShipUseCase Used to automatically put the ship into orbit before navigating
 *   if it is currently docked. Injected as an interface so the navigate use case itself
 *   can be tested with fakes for both dependencies.
 */
interface NavigateShipUseCase {
    /**
     * Navigates the specified ship to the target waypoint.
     *
     * If the ship is currently DOCKED, it is automatically put into orbit first. If the ship
     * is already IN_TRANSIT, the API will return an error (no auto-wait is performed).
     *
     * @param shipSymbol The unique identifier of the ship to navigate (e.g. `"AGENT-1"`).
     * @param waypointSymbol The symbol of the destination waypoint
     *   (e.g. `"X1-DF55-20250Z"`). Must be within the ship's current system.
     * @return A [NavigateResult] containing the updated nav state (status IN_TRANSIT,
     *   arrival time) and the new fuel levels after consumption for the trip.
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.api.exception.SpaceTradersApiException
     *   If the ship is in transit, the waypoint is in a different system, the ship lacks
     *   sufficient fuel, or any other API-level error occurs (including errors from the
     *   implicit orbit call if the ship was docked).
     */
    suspend operator fun invoke(shipSymbol: String, waypointSymbol: String): NavigateResult
}

/**
 * Production implementation of [NavigateShipUseCase].
 *
 * @param fleetApi Live Ktor-backed API client for fleet operations.
 * @param fleetRepository Read to check nav status; written with new nav and fuel state
 *   after a successful navigate call.
 * @param orbitShipUseCase Called automatically when the ship is DOCKED before navigating.
 *   Injected as the interface so tests can provide a fake that records the call.
 */
class NavigateShipUseCaseImpl(
    private val fleetApi: FleetApi,
    private val fleetRepository: FleetRepository,
    private val orbitShipUseCase: OrbitShipUseCase
) : NavigateShipUseCase {

    override suspend operator fun invoke(
        shipSymbol: String,
        waypointSymbol: String
    ): NavigateResult {
        // Step 1: Read the ship's current nav status from the local cache. Using .first()
        // on the cold StateFlow gets the latest cached value without subscribing long-term.
        val ship = fleetRepository.observeShip(shipSymbol).first()

        // Step 2: The SpaceTraders navigate API requires IN_ORBIT status. Automatically
        // orbit the ship if it is docked so the caller never has to know this precondition.
        // Ships that are IN_TRANSIT will fail at step 3 with an API error (expected behavior).
        if (ship?.nav?.status == ShipNavStatus.DOCKED) {
            orbitShipUseCase(shipSymbol)
        }

        // Step 3: Issue the navigate command. The response includes the full updated nav
        // object (status = IN_TRANSIT, route with arrival time) and updated fuel state.
        val response = fleetApi.navigateShip(shipSymbol, waypointSymbol).toDomain()

        // Step 4: Write both updated states to the local cache. Nav reflects IN_TRANSIT;
        // fuel reflects the amount consumed for this trip. Both StateFlow observers
        // (fuel bar, nav status label, arrival countdown) will update automatically.
        fleetRepository.updateShipNav(shipSymbol, response.nav)
        fleetRepository.updateShipFuel(shipSymbol, response.fuel)

        return response
    }
}
