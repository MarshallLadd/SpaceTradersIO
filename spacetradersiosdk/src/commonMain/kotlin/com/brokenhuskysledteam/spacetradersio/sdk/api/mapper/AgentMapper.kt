package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

// Mapper: AgentDto → Agent
// All DTO-to-domain conversions for the Agent entity live here.

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.AgentDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent

/**
 * Maps this [AgentDto] to an [Agent] domain model.
 *
 * **Pattern:** Extension function mapper. Define a `DtoType.toDomain(): DomainType` extension
 * in the `api/mapper/` package. The extension sits on the DTO type so callers read naturally
 * (`dto.toDomain()`), and the conversion logic has exactly one home — never scattered across
 * repositories, ViewModels, or response handlers. In a new project, create one mapper file per
 * domain entity; small, focused files are easy to locate and update independently.
 *
 * **In this project:** [AgentDto] appears in every response that includes agent data — the
 * authenticated-agent lookup, the public-agent lookup, and registration. This single mapper
 * ensures all call sites produce identical [Agent] values with no scattered conversion logic.
 *
 * **Null-safety note:** [AgentDto.accountId] is `null` when the API response describes another
 * player's agent (the `/agents/{symbol}` public endpoint omits this field). Propagating the
 * nullability into the domain model lets callers distinguish "my agent" from "someone else's
 * agent" without a separate boolean flag.
 *
 * @return The domain model built from this DTO's data.
 * @see AgentDto for the wire-format field documentation.
 */
fun AgentDto.toDomain(): Agent = Agent(
    // accountId is nullable in the DTO: the API only returns it for the authenticated
    // player's own agent. Passing it through as-is preserves that distinction in the
    // domain layer so ViewModels can branch on whether this is the current user.
    accountId = accountId,
    symbol = symbol,
    headquarters = headquarters,
    credits = credits,
    startingFaction = startingFaction,
    shipCount = shipCount
)
