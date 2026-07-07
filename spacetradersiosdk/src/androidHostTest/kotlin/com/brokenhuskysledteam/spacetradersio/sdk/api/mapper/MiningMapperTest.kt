package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.CooldownDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.CreateSurveyResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ExtractResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ExtractionDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ExtractionYieldDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipCargoDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.SurveyDepositDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.SurveyDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class MiningMapperTest {

    @Test
    fun extract_toDomain_mapsYieldCooldownAndCargo() {
        val dto = ExtractResponseDto(
            extraction = ExtractionDto("LADD-1", ExtractionYieldDto("IRON_ORE", 3)),
            cooldown = CooldownDto("LADD-1", 70, 70, "2026-07-06T00:01:10Z"),
            cargo = ShipCargoDto(capacity = 40, units = 3)
        )
        val result = dto.toDomain()
        assertEquals("LADD-1", result.shipSymbol)
        assertEquals("IRON_ORE", result.yieldSymbol)
        assertEquals(3, result.yieldUnits)
        assertEquals(70, result.cooldown.totalSeconds)
        assertEquals(Instant.parse("2026-07-06T00:01:10Z"), result.cooldown.expiration)
        assertEquals(3, result.cargo.units)
    }

    @Test
    fun createSurvey_toDomain_mapsCooldownAndSurveys() {
        val dto = CreateSurveyResponseDto(
            cooldown = CooldownDto("LADD-1", 60, 60, "2026-07-06T00:01:00Z"),
            surveys = listOf(
                SurveyDto("sig-1", "X1-DM91-B7", listOf(SurveyDepositDto("IRON_ORE"), SurveyDepositDto("COPPER_ORE")),
                    "2026-07-06T01:00:00Z", "MODERATE")
            )
        )
        val result = dto.toDomain()
        assertEquals(60, result.cooldown.totalSeconds)
        assertEquals(1, result.surveys.size)
        assertEquals("sig-1", result.surveys[0].signature)
        assertEquals(listOf("IRON_ORE", "COPPER_ORE"), result.surveys[0].deposits)
        assertEquals("MODERATE", result.surveys[0].size)
    }

    @Test
    fun survey_toDto_roundTripsForExtractWithSurvey() {
        val dto = SurveyDto("sig-1", "X1-DM91-B7", listOf(SurveyDepositDto("IRON_ORE")), "2026-07-06T01:00:00Z", "LARGE")
        val roundTripped = dto.toDomain().toDto()
        assertEquals(dto, roundTripped)
    }
}
