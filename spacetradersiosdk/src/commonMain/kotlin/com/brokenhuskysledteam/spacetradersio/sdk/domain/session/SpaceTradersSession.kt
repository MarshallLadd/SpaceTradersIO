package com.brokenhuskysledteam.spacetradersio.sdk.domain.session

import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.AgentStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.ContractStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

class SpaceTradersSession(private val scope: CoroutineScope) {

    val refreshScheduler = RefreshScheduler(scope)
    val fleetStateStore = FleetStateStore()
    val agentStateStore = AgentStateStore()
    val contractStateStore = ContractStateStore()

    fun onResume() {
        refreshScheduler.onResume()
    }

    fun destroy() {
        scope.cancel()
    }
}
