package com.brokenhuskysledteam.spacetraders.domain.model.enums

enum class ContractType {
    PROCUREMENT,  // Deliver specific goods to a waypoint
    TRANSPORT,    // Move cargo between waypoints
    SHUTTLE;      // Transport passengers or units

    companion object {
        fun fromString(value: String): ContractType =
            entries.firstOrNull { it.name == value } ?: PROCUREMENT
    }
}
