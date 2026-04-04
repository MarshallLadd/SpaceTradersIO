package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.AgentDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentMapperTest {

    @Test
    fun toDomain_allFieldsMapCorrectly() {
        val dto = AgentDto(
            accountId = "acc-123",
            symbol = "COMMANDER",
            headquarters = "X1-DF55-20250Z",
            credits = 150000L,
            startingFaction = "COSMIC",
            shipCount = 2
        )

        val domain = dto.toDomain()

        assertEquals(
            Agent(
                accountId = "acc-123",
                symbol = "COMMANDER",
                headquarters = "X1-DF55-20250Z",
                credits = 150000L,
                startingFaction = "COSMIC",
                shipCount = 2
            ),
            domain
        )
    }

    @Test
    fun toDomain_nullAccountId_mapsToNull() {
        val dto = AgentDto(
            accountId = null,
            symbol = "OTHER_AGENT",
            headquarters = "X1-AB12-00000A",
            credits = 0L,
            startingFaction = "VOID",
            shipCount = 1
        )

        assertNull(dto.toDomain().accountId)
    }
}
