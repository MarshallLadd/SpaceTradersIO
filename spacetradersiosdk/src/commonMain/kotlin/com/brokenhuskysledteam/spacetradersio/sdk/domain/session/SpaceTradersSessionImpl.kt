package com.brokenhuskysledteam.spacetradersio.sdk.domain.session

import com.brokenhuskysledteam.spacetradersio.sdk.data.db.SpaceTradersDatabase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.ContractStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.WaypointStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive

class SpaceTradersSessionImpl(
    private val scope: CoroutineScope,
    override val database: SpaceTradersDatabase
) : SpaceTradersSession {

    override val refreshScheduler = RefreshScheduler(scope)
    override val contractStateStore = ContractStateStore()
    override val waypointStateStore = WaypointStateStore()

    override val isActive: Boolean get() = scope.isActive

    override fun onResume() {
        refreshScheduler.onResume()
    }

    override fun destroy() {
        scope.cancel()
        database.shipQueries.deleteAllShips()
        database.agentQueries.deleteAll()
    }
}
