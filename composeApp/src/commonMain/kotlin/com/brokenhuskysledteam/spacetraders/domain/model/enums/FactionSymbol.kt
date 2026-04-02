package com.brokenhuskysledteam.spacetraders.domain.model.enums

// All playable factions in the SpaceTraders universe.
// COSMIC is recommended for new agents — well connected to the rest of the universe.
enum class FactionSymbol {
    COSMIC, VOID, GALACTIC, QUANTUM, DOMINION,
    ASTRO, CORSAIRS, OBSIDIAN, AEGIS, UNITED,
    SOLITARY, COBALT, OMEGA, ECHO, LORDS,
    CULT, ANCIENTS, SHADOW, ETHEREAL;

    companion object {
        // Safe fallback for unknown values returned by future API versions.
        fun fromString(value: String): FactionSymbol =
            entries.firstOrNull { it.name == value } ?: COSMIC
    }
}
