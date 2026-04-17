package com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums

/**
 * Identifies one of the 19 playable factions in the SpaceTraders universe.
 *
 * **Pattern:** Safe enum parsing. All enums sourced from API string values define a
 * [fromString] companion function that returns a sensible default instead of throwing on
 * unrecognized values. This prevents crashes when the API adds new factions in future.
 *
 * **In this project:** Used when registering a new agent (the player picks a starting faction)
 * and in [com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent] to show which faction
 * the agent belongs to. The faction affects starting location and initial contract availability.
 *
 * **Fallback:** Unrecognized API strings resolve to [COSMIC] — the recommended starting faction
 * for new agents, as it is well connected to the rest of the universe.
 *
 * The 19 constants are faction proper nouns and are self-explanatory as identifiers. See the
 * SpaceTraders in-game faction lore for narrative descriptions of each.
 */
enum class FactionSymbol {
    COSMIC, VOID, GALACTIC, QUANTUM, DOMINION,
    ASTRO, CORSAIRS, OBSIDIAN, AEGIS, UNITED,
    SOLITARY, COBALT, OMEGA, ECHO, LORDS,
    CULT, ANCIENTS, SHADOW, ETHEREAL;

    companion object {
        /**
         * Parses [value] into the enum, returning [COSMIC] for unrecognized strings.
         *
         * Prefer this over [enumValueOf] for API-sourced strings — [enumValueOf] throws
         * [IllegalArgumentException] on unknown values, which would crash on new API additions.
         */
        fun fromString(value: String): FactionSymbol =
            entries.firstOrNull { it.name == value } ?: COSMIC
    }
}
