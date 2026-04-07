package com.brokenhuskysledteam.spacetradersio.sdk.domain.state

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AgentStateStore {

    private val _agent = MutableStateFlow<Agent?>(null)
    val agent: StateFlow<Agent?> = _agent.asStateFlow()

    fun update(agent: Agent) {
        _agent.value = agent
    }

    fun clear() {
        _agent.value = null
    }
}
