package com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums

/**
 * Describes a characteristic or feature of a waypoint that affects what activities are
 * available there.
 *
 * **Pattern:** Safe enum parsing. All enums sourced from API string values define a
 * [fromString] companion function that returns a sensible default instead of throwing on
 * unrecognized values. This prevents crashes when the API adds new trait symbols in future.
 *
 * **In this project:** Each waypoint can carry multiple traits. The trait list is used in
 * the system map and waypoint detail UI to surface icons and labels that guide player decisions
 * (e.g., "does this waypoint have a market?", "can I buy a ship here?").
 *
 * **Gameplay-critical traits to check before navigating:**
 * - [MARKETPLACE] — trade goods here.
 * - [SHIPYARD] — buy new ships here.
 * - [JUMP_GATE] — fast travel to another system.
 * - [FUEL_STATION] — refuel without docking at a full station.
 * - [MINERAL_DEPOSITS] / [COMMON_METAL_DEPOSITS] / [PRECIOUS_METAL_DEPOSITS] — mineable resources.
 * - [STRIPPED] — resources already depleted; mining here is unproductive.
 * - [UNCHARTED] — waypoint details not yet surveyed; traits and resources are unknown.
 *
 * **Fallback:** Unrecognized API strings resolve to [UNCHARTED].
 *
 * The remaining 50+ constants are environmental or atmospheric descriptors (e.g., [VOLCANIC],
 * [FROZEN], [BREATHABLE_ATMOSPHERE]) that provide lore flavor and may affect future gameplay
 * mechanics but do not currently gate any player action.
 */
enum class WaypointTraitSymbol {
    /** Waypoint details have not yet been surveyed — traits and resources are unknown. */
    UNCHARTED,

    UNDER_CONSTRUCTION,

    /** This waypoint has a market where goods can be bought and sold. */
    MARKETPLACE,

    /** This waypoint has a shipyard where new ships can be purchased. */
    SHIPYARD,

    OUTPOST,
    SCATTERED_SETTLEMENTS,
    SPRAWLING_CITIES,
    MEGA_STRUCTURES,
    PIRATE_BASE,
    OVERCROWDED,
    HIGH_TECH,
    CORRUPT,
    BUREAUCRATIC,
    TRADING_HUB,
    INDUSTRIAL,
    BLACK_MARKET,
    RESEARCH_FACILITY,
    MILITARY_BASE,
    SURVEILLANCE_OUTPOST,
    EXPLORATION_OUTPOST,

    /** The waypoint contains mineable mineral deposits (general). */
    MINERAL_DEPOSITS,

    /** The waypoint contains deposits of common metals (e.g., iron, copper). */
    COMMON_METAL_DEPOSITS,

    /** The waypoint contains deposits of precious metals (e.g., gold, silver). */
    PRECIOUS_METAL_DEPOSITS,

    RARE_METAL_DEPOSITS,
    METHANE_POOLS,
    ICE_CRYSTALS,
    EXPLOSIVE_GASES,
    STRONG_MAGNETOSPHERE,
    VIBRANT_AURORAS,
    SALT_FLATS,
    CANYONS,
    PERPETUAL_DAYLIGHT,
    PERPETUAL_OVERCAST,
    DRY_SEABEDS,
    MAGMA_SEAS,
    SUPERVOLCANOES,
    ASH_CLOUDS,
    VAST_RUINS,
    MUTATED_FLORA,
    TERRAFORMED,
    EXTREME_TEMPERATURES,
    EXTREME_PRESSURE,
    DIVERSE_LIFE,
    SCARCE_LIFE,
    FOSSILS,
    WEAK_GRAVITY,
    STRONG_GRAVITY,
    CRUSHING_GRAVITY,
    TOXIC_ATMOSPHERE,
    CORROSIVE_ATMOSPHERE,
    BREATHABLE_ATMOSPHERE,
    THIN_ATMOSPHERE,
    JOVIAN,
    ROCKY,
    VOLCANIC,
    FROZEN,
    SWAMP,
    BARREN,
    TEMPERATE,
    JUNGLE,
    OCEAN,
    RADIOACTIVE,
    MICRO_GRAVITY_ANOMALIES,
    DEBRIS_CLUSTER,
    DEEP_CRATERS,
    SHALLOW_CRATERS,
    UNSTABLE_COMPOSITION,
    HOLLOWED_INTERIOR,

    /** Resources at this waypoint have been fully depleted; mining here yields nothing. */
    STRIPPED,

    /** This waypoint has a jump gate enabling fast travel to connected systems. */
    JUMP_GATE,

    /** This waypoint has a fuel station where ships can refuel. */
    FUEL_STATION;

    companion object {
        /**
         * Parses [value] into the enum, returning [UNCHARTED] for unrecognized strings.
         *
         * Prefer this over [enumValueOf] for API-sourced strings — [enumValueOf] throws
         * [IllegalArgumentException] on unknown values, which would crash on new API additions.
         */
        fun fromString(value: String): WaypointTraitSymbol =
            entries.firstOrNull { it.name == value } ?: UNCHARTED
    }
}
