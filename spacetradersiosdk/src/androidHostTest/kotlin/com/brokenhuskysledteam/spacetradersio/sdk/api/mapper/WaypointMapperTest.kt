package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.WaypointDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.WaypointOrbitalDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.WaypointTraitDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WaypointMapperTest {

    private fun fullDto() = WaypointDto(
        symbol = "X1-DF55-20250Z",
        type = "MOON",
        systemSymbol = "X1-DF55",
        x = -15,
        y = 12,
        orbits = "X1-DF55-17335A",
        orbitals = listOf(WaypointOrbitalDto("X1-DF55-20250Z-STATION")),
        traits = listOf(
            WaypointTraitDto(symbol = "MARKETPLACE", name = "Marketplace", description = "A marketplace")
        ),
        isUnderConstruction = false
    )

    @Test
    fun waypoint_symbolMapsCorrectly() {
        assertEquals("X1-DF55-20250Z", fullDto().toDomain().symbol)
    }

    @Test
    fun waypoint_typeMapsCorrectly() {
        assertEquals(WaypointType.MOON, fullDto().toDomain().type)
    }

    @Test
    fun waypoint_systemSymbolMapsCorrectly() {
        assertEquals("X1-DF55", fullDto().toDomain().systemSymbol)
    }

    @Test
    fun waypoint_coordinatesMapCorrectly() {
        val wp = fullDto().toDomain()
        assertEquals(-15, wp.x)
        assertEquals(12, wp.y)
    }

    @Test
    fun waypoint_orbitsMapsCorrectly() {
        assertEquals("X1-DF55-17335A", fullDto().toDomain().orbits)
    }

    @Test
    fun waypoint_nullOrbits_mapsToNull() {
        val dto = fullDto().copy(orbits = null)
        assertNull(dto.toDomain().orbits)
    }

    @Test
    fun waypoint_orbitalsFlattenedToSymbols() {
        assertEquals(listOf("X1-DF55-20250Z-STATION"), fullDto().toDomain().orbitals)
    }

    @Test
    fun waypoint_emptyOrbitals_mapsToEmptyList() {
        val dto = fullDto().copy(orbitals = emptyList())
        assertTrue(dto.toDomain().orbitals.isEmpty())
    }

    @Test
    fun waypoint_traitsMappedCorrectly() {
        val traits = fullDto().toDomain().traits
        assertEquals(1, traits.size)
        assertEquals(WaypointTraitSymbol.MARKETPLACE, traits[0].symbol)
    }

    @Test
    fun waypoint_emptyTraits_mapsToEmptyList() {
        val dto = fullDto().copy(traits = emptyList())
        assertTrue(dto.toDomain().traits.isEmpty())
    }

    @Test
    fun waypoint_isUnderConstructionMapsCorrectly() {
        assertEquals(false, fullDto().toDomain().isUnderConstruction)
    }

    @Test
    fun waypoint_isUnderConstructionTrue() {
        val dto = fullDto().copy(isUnderConstruction = true)
        assertEquals(true, dto.toDomain().isUnderConstruction)
    }

    @Test
    fun waypoint_unknownType_fallsBackToPlanet() {
        val dto = fullDto().copy(type = "FUTURE_TYPE")
        assertEquals(WaypointType.PLANET, dto.toDomain().type)
    }

    @Test
    fun trait_symbolMapsCorrectly() {
        val traitDto = WaypointTraitDto("SHIPYARD", "Shipyard", "A shipyard")
        assertEquals(WaypointTraitSymbol.SHIPYARD, traitDto.toDomain().symbol)
    }

    @Test
    fun trait_nameMapsCorrectly() {
        val traitDto = WaypointTraitDto("SHIPYARD", "Shipyard", "A shipyard")
        assertEquals("Shipyard", traitDto.toDomain().name)
    }

    @Test
    fun trait_descriptionMapsCorrectly() {
        val traitDto = WaypointTraitDto("SHIPYARD", "Shipyard", "A shipyard")
        assertEquals("A shipyard", traitDto.toDomain().description)
    }

    @Test
    fun trait_unknownSymbol_fallsBackToUncharted() {
        val traitDto = WaypointTraitDto("FUTURE_TRAIT", "Future", "Unknown")
        assertEquals(WaypointTraitSymbol.UNCHARTED, traitDto.toDomain().symbol)
    }
}
