package com.brokenhuskysledteam.spacetradersio.sdk.domain.session

import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.AgentStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.ContractStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive

class SpaceTradersSessionImpl(private val scope: CoroutineScope) : SpaceTradersSession {

    override val refreshScheduler = RefreshScheduler(scope)
    override val fleetStateStore = FleetStateStore()
    override val agentStateStore = AgentStateStore()
    override val contractStateStore = ContractStateStore()

    override val isActive: Boolean get() = scope.isActive

    override fun onResume() {
        refreshScheduler.onResume()
    }

    override fun destroy() {
        scope.cancel()
    }
}
