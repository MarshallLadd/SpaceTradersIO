package com.brokenhuskysledteam.spacetradersio.sdk.domain.session

import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.AgentStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.ContractStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.WaypointStateStore

interface SpaceTradersSession {
    val refreshScheduler: RefreshScheduler
    val fleetStateStore: FleetStateStore
    val agentStateStore: AgentStateStore
    val contractStateStore: ContractStateStore
    val waypointStateStore: WaypointStateStore
    val isActive: Boolean
    fun onResume()
    fun destroy()
}
