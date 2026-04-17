package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.RefuelResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.AgentRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository

/**
 * Refuels a ship and atomically updates both the ship's fuel state and the agent's
 * credit balance in their respective local repositories.
 *
 * **Pattern:** Callable use case with interface + Impl split. `operator fun invoke()` lets
 * callers write `refuelShipUseCase(shipSymbol)` naturally. The interface enables ViewModel
 * tests to substitute a hand-written fake without a mocking library or a live network. In
 * a new project, use this pattern whenever the use case touches more than one repository —
 * those are the cases where faking at the use-case boundary is most valuable.
 *
 * **Multi-repository update pattern (the primary value of this use case):** A single
 * `POST /my/ships/{shipSymbol}/refuel` call on the SpaceTraders API returns three pieces
 * of data that belong to two different domain repositories:
 * - Updated `agent` (credit balance deducted) → [AgentRepository]
 * - Updated `fuel` (tank filled) → [FleetRepository]
 * - `transaction` (marketplace record of the purchase) → returned in [RefuelResult]
 *
 * Without this use case, a ViewModel performing a refuel would need to:
 * 1. Know about [FleetRepository] to update fuel.
 * 2. Know about [AgentRepository] to update credits.
 * 3. Coordinate the two updates itself, duplicating that logic in every caller.
 *
 * The use case collapses all three steps into one call, keeping the ViewModel ignorant of
 * the cross-repository coordination. This is exactly the orchestration that earns a use
 * case its place in the architecture.
 *
 * **Reactive propagation:** Both [FleetRepository] and [AgentRepository] expose their
 * state as `StateFlow`. After this use case updates both, any ViewModel observing ship
 * fuel or agent credits will receive the new values automatically — no manual refresh.
 *
 * **In this project:** Called from `ShipDetailViewModel` when the user taps "Refuel".
 * The ViewModel uses [RefuelResult] to display the transaction details and then lets the
 * reactive flows update the fuel gauge and credit display.
 *
 * @param fleetApi Live Ktor-backed API client. The refuel endpoint is authenticated and
 *   requires the ship to be docked at a fuel market.
 * @param fleetRepository In-memory ship store. Updated with new fuel levels after refuel.
 * @param agentRepository In-memory agent store. Updated with the new credit balance after
 *   the fuel purchase is deducted.
 */
interface RefuelShipUseCase {
    /**
     * Refuels the specified ship and updates both the ship fuel and agent credit caches.
     *
     * @param shipSymbol The unique identifier of the ship to refuel (e.g. `"AGENT-1"`).
     * @return A [RefuelResult] containing the updated [com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent],
     *   the new [ShipFuel] state, and a [com.brokenhuskysledteam.spacetradersio.sdk.domain.model.MarketTransaction]
     *   recording the purchase.
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.api.exception.SpaceTradersApiException
     *   If the ship is not docked, the waypoint has no fuel market, or the agent has
     *   insufficient credits.
     */
    suspend operator fun invoke(shipSymbol: String): RefuelResult
}

/**
 * Production implementation of [RefuelShipUseCase].
 *
 * @param fleetApi Live Ktor-backed API client for fleet operations.
 * @param fleetRepository Updated with the new fuel levels after a successful refuel.
 * @param agentRepository Updated with the new credit balance after the fuel cost is deducted.
 */
class RefuelShipUseCaseImpl(
    private val fleetApi: FleetApi,
    private val fleetRepository: FleetRepository,
    private val agentRepository: AgentRepository
) : RefuelShipUseCase {
    override suspend operator fun invoke(shipSymbol: String): RefuelResult {
        val response = fleetApi.refuelShip(shipSymbol)
        // Map all three API response fields before writing to any repository. If mapping
        // throws (e.g. an unknown enum value), neither repository is partially updated.
        val result = RefuelResult(
            agent = response.agent.toDomain(),
            fuel = ShipFuel(current = response.fuel.current, capacity = response.fuel.capacity),
            transaction = response.transaction.toDomain()
        )
        // Update both repositories so their StateFlow observers receive the new values
        // without a separate fetch. Order doesn't matter here — both writes are independent.
        fleetRepository.updateShipFuel(shipSymbol, result.fuel)
        agentRepository.saveAgent(result.agent)
        return result
    }
}
