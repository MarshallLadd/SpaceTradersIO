package com.brokenhuskysledteam.spacetradersio.ui.ships

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ContractDeliverGood
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ContractTab
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.ContractRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.SystemRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.DeliverCargoUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.DockShipUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.NegotiateContractUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.OrbitShipUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.RefuelShipUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the ship detail screen.
 *
 * **Pattern:** UDF (Unidirectional Data Flow) with local state separation. The ViewModel
 * exposes a single [uiState] derived by `combine()`-ing a repository-backed [Flow] (the
 * live ship record) with a [MutableStateFlow] of [LocalState] (purely ViewModel-local fields
 * like loading flags and transient action results). All user interactions enter through
 * [onEvent] and are dispatched to private helpers. In a new project, use this pattern whenever
 * a screen has both server-backed data and ViewModel-owned ephemeral state that must update
 * together atomically.
 *
 * **In this project:** Manages orbit, dock, and refuel commands for a single ship identified
 * by [shipSymbol] extracted from [SavedStateHandle]. The ship symbol is provided by the
 * navigation back-stack entry argument named `"shipSymbol"` and is never null; `checkNotNull`
 * enforces this invariant at startup rather than silently producing a broken screen.
 *
 * **Hilt wiring:** `@HiltViewModel` + `@Inject constructor` is the standard pattern for
 * ViewModels that receive [SavedStateHandle]. Hilt injects [SavedStateHandle] automatically
 * when the ViewModel is scoped to a NavBackStackEntry via `hiltViewModel()`.
 */
@HiltViewModel
class ShipDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val fleetRepository: FleetRepository,
    private val systemRepository: SystemRepository,
    private val orbitShipUseCase: OrbitShipUseCase,
    private val dockShipUseCase: DockShipUseCase,
    private val refuelShipUseCase: RefuelShipUseCase,
    private val contractRepository: ContractRepository,
    private val negotiateContractUseCase: NegotiateContractUseCase,
    private val deliverCargoUseCase: DeliverCargoUseCase
) : ViewModel() {

    /** Ship identifier extracted from the navigation back-stack; never null. */
    private val shipSymbol: String = checkNotNull(savedStateHandle["shipSymbol"])

    /**
     * ViewModel-local mutable state. See [LocalState] for the rationale behind the
     * separation from repository state.
     */
    private val _localState = MutableStateFlow(LocalState())

    /**
     * The single source of truth for the ship detail UI.
     *
     * Derived by combining [FleetRepository.observeShip] (the offline-first SQLDelight cache)
     * with [_localState]. Using `combine()` means the screen re-renders automatically whenever
     * either source changes — the repository stream updates when a background refresh writes
     * new data to the DB, and [_localState] updates after each user action.
     *
     * `SharingStarted.Eagerly` is used instead of `WhileSubscribed` so that
     * [StandardTestDispatcher]-based tests can read `.value` directly without an active
     * subscriber triggering the upstream `combine`. See CLAUDE.md Gotchas for the full
     * explanation.
     */
    val uiState: StateFlow<ShipDetailUiState> = combine(
        fleetRepository.observeShip(shipSymbol),
        _localState
    ) { ship, local ->
        ShipDetailUiState(
            ship = ship?.toDetail(),
            isLoading = local.isLoading,
            isActionInProgress = local.isActionInProgress,
            actionResult = local.actionResult,
            error = local.error,
            hasShipyard = local.hasShipyard,
            hasMarketplace = local.hasMarketplace,
            pendingNegotiate = local.pendingNegotiate,
            activeContracts = local.activeContracts,
            isDeliverDialogOpen = local.isDeliverDialogOpen,
            selectedDeliverContract = local.selectedDeliverContract,
            selectedDeliverGood = local.selectedDeliverGood,
            deliverUnits = local.deliverUnits
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ShipDetailUiState())

    init {
        loadShip()
    }

    /**
     * Dispatches a [ShipDetailEvent] to the appropriate handler.
     *
     * This is the single entry point for all UI interactions. The sealed `when` expression
     * is exhaustive by the compiler, so adding a new event subtype without handling it here
     * is a compile error.
     *
     * **Note on [ShipDetailEvent.ViewSystemClicked]:** The ViewModel handles this with
     * `-> Unit` (a no-op). Navigation is performed by the composable's `onNavigateToSystemMap`
     * callback, which is wired directly in the NavHost — it does not require ViewModel
     * involvement. The arm must still appear here because [ShipDetailEvent] is a sealed
     * interface and Kotlin requires all subtypes to be covered. This is the correct pattern
     * when the composable layer owns the navigation action rather than a ViewModel-owned
     * `Channel<NavigationTarget>`.
     *
     * @param event The event emitted by the composable.
     */
    fun onEvent(event: ShipDetailEvent) {
        when (event) {
            is ShipDetailEvent.OrbitClicked -> performAction {
                val nav = orbitShipUseCase(shipSymbol)
                _localState.update { it.copy(actionResult = ActionResult.Orbited(nav.waypointSymbol)) }
            }

            is ShipDetailEvent.DockClicked -> performAction {
                val nav = dockShipUseCase(shipSymbol)
                _localState.update { it.copy(actionResult = ActionResult.Docked(nav.waypointSymbol)) }
            }

            is ShipDetailEvent.RefuelClicked -> performAction {
                val result = refuelShipUseCase(shipSymbol)
                _localState.update {
                    it.copy(
                        actionResult = ActionResult.Refueled(
                            fuelAdded = result.transaction.units,
                            totalCost = result.transaction.totalPrice,
                            newCredits = result.agent.credits
                        )
                    )
                }
            }

            is ShipDetailEvent.ActionResultDismissed -> _localState.update { it.copy(actionResult = null) }

            is ShipDetailEvent.RetryClicked -> loadShip()

            // Navigation is handled by the composable via the onNavigateToSystemMap callback;
            // the ViewModel does not need to act on this event.
            is ShipDetailEvent.ViewSystemClicked -> Unit

            // Navigation to the shipyard is handled by the composable callback.
            is ShipDetailEvent.ViewShipyardClicked -> Unit

            // Navigation to the market is handled by the composable callback.
            is ShipDetailEvent.ViewMarketClicked -> Unit

            is ShipDetailEvent.NegotiateContractClicked ->
                _localState.update { it.copy(pendingNegotiate = true) }

            is ShipDetailEvent.NegotiateDismissed ->
                _localState.update { it.copy(pendingNegotiate = false) }

            is ShipDetailEvent.NegotiateConfirmed -> performAction {
                val contract = negotiateContractUseCase(shipSymbol)
                _localState.update {
                    it.copy(
                        pendingNegotiate = false,
                        actionResult = ActionResult.NegotiatedContract(
                            contractId = contract.id,
                            type = contract.type.name,
                            upfrontPayment = contract.terms.paymentOnAccepted
                        )
                    )
                }
            }

            is ShipDetailEvent.DeliverCargoClicked -> viewModelScope.launch {
                val contracts = contractRepository.observeContracts(
                    ContractTab.ACTIVE, limit = 20L, offset = 0L
                ).first()
                _localState.update {
                    it.copy(
                        isDeliverDialogOpen = true,
                        activeContracts = contracts,
                        selectedDeliverContract = null,
                        selectedDeliverGood = null,
                        deliverUnits = ""
                    )
                }
            }

            is ShipDetailEvent.DeliverContractSelected ->
                _localState.update {
                    it.copy(selectedDeliverContract = event.contract, selectedDeliverGood = null)
                }

            is ShipDetailEvent.DeliverGoodSelected ->
                _localState.update { it.copy(selectedDeliverGood = event.good) }

            is ShipDetailEvent.DeliverUnitsChanged ->
                _localState.update { it.copy(deliverUnits = event.units) }

            is ShipDetailEvent.DeliverDismissed ->
                _localState.update { it.copy(isDeliverDialogOpen = false) }

            is ShipDetailEvent.DeliverConfirmed -> {
                val contract = _localState.value.selectedDeliverContract ?: return
                val good = _localState.value.selectedDeliverGood ?: return
                val units = _localState.value.deliverUnits.toIntOrNull() ?: return
                performAction {
                    val updated = deliverCargoUseCase(contract.id, shipSymbol, good.tradeSymbol, units)
                    val updatedGood = updated.terms.deliverGoods.first { it.tradeSymbol == good.tradeSymbol }
                    _localState.update {
                        it.copy(
                            isDeliverDialogOpen = false,
                            actionResult = ActionResult.DeliveredCargo(
                                tradeSymbol = good.tradeSymbol,
                                unitsFulfilled = updatedGood.unitsFulfilled,
                                unitsRequired = updatedGood.unitsRequired
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Triggers an initial (or retry) network fetch for this ship and updates [_localState].
     *
     * Sets `isLoading = true` before the request and clears it in both the success and error
     * paths. On success, [FleetRepository.refreshMyShip] writes the updated ship to the
     * SQLDelight cache, which causes [FleetRepository.observeShip] to emit a new value and
     * automatically re-compose the screen via the `combine()` pipeline — no explicit state
     * assignment for the ship data is needed here.
     */
    private fun loadShip() {
        _localState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                fleetRepository.refreshMyShip(shipSymbol)
                val ship = fleetRepository.observeShip(shipSymbol).first()
                if (ship != null) {
                    runCatching {
                        val waypoint = systemRepository.getWaypoint(ship.nav.systemSymbol, ship.nav.waypointSymbol)
                        _localState.update {
                            it.copy(
                                hasShipyard = waypoint.traits.any { t -> t.symbol == WaypointTraitSymbol.SHIPYARD },
                                hasMarketplace = waypoint.traits.any { t -> t.symbol == WaypointTraitSymbol.MARKETPLACE }
                            )
                        }
                    }
                }
                _localState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _localState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load ship")
                }
            }
        }
    }

    /**
     * Generic wrapper for ship action coroutines (orbit, dock, refuel).
     *
     * **Pattern:** Action wrapper. This helper eliminates the boilerplate of "set loading,
     * run suspend work, capture result or error, always clear loading" that would otherwise
     * be duplicated in every action handler. To apply this in a new project, extract a
     * `performAction` that (1) pre-sets the loading/in-progress flag, (2) invokes the caller's
     * suspend [block], (3) captures the success state inside the block, and (4) always clears
     * the flag in `finally`. The `finally` clause is critical — it guarantees buttons are
     * re-enabled even if the coroutine is cancelled.
     *
     * **In this project:** Each action handler in [onEvent] passes a suspend lambda that calls
     * the appropriate use case and then calls `_localState.update { it.copy(actionResult = ...) }`
     * to set the transient feedback. The wrapper handles `isActionInProgress` and `error`
     * so individual handlers do not have to.
     *
     * @param block Suspend lambda containing the use-case call and success-state update.
     *   Any exception thrown from [block] is caught and stored in [LocalState.error].
     */
    private fun performAction(block: suspend () -> Unit) {
        // Clear any previous result/error so the UI doesn't show stale feedback.
        _localState.update { it.copy(isActionInProgress = true, actionResult = null, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _localState.update { it.copy(error = e.message ?: "Action failed") }
            } finally {
                // Always re-enable buttons, even on cancellation.
                _localState.update { it.copy(isActionInProgress = false) }
            }
        }
    }

    /**
     * ViewModel-local state that is not backed by a repository.
     *
     * **Pattern:** Local state separation. Rather than maintaining several independent
     * `MutableStateFlow` fields (`_isLoading`, `_error`, `_actionResult`, etc.) and updating
     * them individually — which introduces race conditions when two fields must change together
     * atomically — all ViewModel-owned ephemeral state is grouped into a single immutable data
     * class. A single `_localState.update { it.copy(...) }` call then changes multiple fields
     * atomically, and `combine()` merges this stream with the repository flow into [uiState].
     * In a new project, create a `LocalState` equivalent whenever you have two or more
     * ViewModel-owned fields that are always updated as a unit.
     *
     * **In this project:** Holds the loading, action-in-progress, result, and error flags that
     * are set/cleared inside [loadShip] and [performAction].
     *
     * @property isLoading `true` during the initial or retry network fetch.
     * @property isActionInProgress `true` while a ship command (orbit/dock/refuel) is running.
     * @property actionResult The most recent successful action result, or `null`.
     * @property error The most recent error message, or `null` if no error is active.
     */
    private data class LocalState(
        val isLoading: Boolean = false,
        val isActionInProgress: Boolean = false,
        val actionResult: ActionResult? = null,
        val error: String? = null,
        val hasShipyard: Boolean = false,
        val hasMarketplace: Boolean = false,
        val pendingNegotiate: Boolean = false,
        val activeContracts: List<Contract> = emptyList(),
        val isDeliverDialogOpen: Boolean = false,
        val selectedDeliverContract: Contract? = null,
        val selectedDeliverGood: ContractDeliverGood? = null,
        val deliverUnits: String = ""
    )
}

/**
 * Maps the SDK domain [Ship] to the screen's flattened [ShipDetail] UI model.
 *
 * **Pattern:** Private file-level extension mapping. Keeping this extension private to the
 * ViewModel file (rather than in a shared mapper or inside [ShipDetail]) means the mapping
 * logic is co-located with the screen that owns [ShipDetail], and neither the SDK domain model
 * nor the UI model leaks knowledge of the other. The same reasoning applies to `Ship.toSummary()`
 * in `ShipListViewModel.kt`. In a new project, place DTO→UI-model mappings as private extensions
 * in the ViewModel file that needs them.
 *
 * **In this project:** Called inside the `combine()` lambda in [ShipDetailViewModel.uiState]
 * on every emission from `FleetRepository.observeShip`. The `inTransit` check gates the
 * route-time fields so [ShipDetail.arrivalTime] and [ShipDetail.departureTime] are only
 * non-null when the ship is actively in transit — preventing stale times from a previous
 * leg from being displayed.
 */
private fun Ship.toDetail(): ShipDetail {
    // Only expose arrival/departure times when the ship is actually moving;
    // for docked/orbiting ships these values from the last route leg are misleading.
    val inTransit = nav.status == ShipNavStatus.IN_TRANSIT
    return ShipDetail(
        symbol = symbol,
        frameName = frameName,
        role = registration.role,
        navStatus = nav.status,
        flightMode = nav.flightMode,
        systemSymbol = nav.systemSymbol,
        waypointSymbol = nav.waypointSymbol,
        originSymbol = nav.route.origin.symbol,
        originType = nav.route.origin.type,
        destinationSymbol = nav.route.destination.symbol,
        destinationType = nav.route.destination.type,
        arrivalTime = if (inTransit) nav.route.arrivalTime else null,
        departureTime = if (inTransit) nav.route.departureTime else null,
        fuelCurrent = fuel.current,
        fuelCapacity = fuel.capacity,
        cargoUnits = cargo.units,
        cargoCapacity = cargo.capacity,
        cargoInventory = cargo.inventory
    )
}
