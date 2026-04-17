package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

// Mapper: ErrorBodyDto → SpaceTradersError
// Converts a numeric API error code into the matching leaf of the sealed SpaceTradersError
// hierarchy. Called exclusively by SpaceTradersClient's HttpCallValidator.

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ErrorBodyDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersError

/**
 * Maps this [ErrorBodyDto] to the appropriate [SpaceTradersError] subtype.
 *
 * **Pattern:** Exhaustive code-dispatch mapper. The SpaceTraders API communicates failure
 * details through a numeric error [code] embedded in the JSON error body. This function is
 * the single point that converts that numeric code into a typed, sealed-hierarchy value that
 * the rest of the codebase can `when`-switch on without touching raw integers.
 *
 * In a new project: survey your API's error code catalogue, group codes into logical
 * categories (auth, navigation, resource operations, billing, etc.), create a sealed class
 * per category in the domain layer, then build this dispatch function as the single `when`
 * expression mapping each code to its leaf type. Adding an `else -> Unknown(...)` fallback
 * future-proofs the client against new codes the API introduces between releases.
 *
 * **In this project:** [ErrorBodyDto] is deserialized inside `SpaceTradersClient`'s
 * `HttpCallValidator` on any non-2xx response, and the result is wrapped in a
 * [com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersApiException] before
 * being thrown to callers. ViewModels catch `SpaceTradersApiException` and `when`-switch on
 * `exception.error` to decide how to surface the problem to the user.
 *
 * **Code ranges covered by this mapper:**
 * - 3000–3200: General / infrastructure errors (server serialization, maintenance, reset)
 * - 4000–4001: Standalone errors (cooldown conflict, waypoint access)
 * - 4100–4116: Authentication and token errors (17 subtypes)
 * - 4200–4204: Navigation errors (5 subtypes)
 * - 4205–4271: Ship operation errors (50 subtypes — the largest category)
 * - 4500–4511: Contract lifecycle errors (11 subtypes)
 * - 4600–4605: Market and trade errors (5 subtypes)
 * - 4700:       Standalone waypoint-faction error
 * - 4800–4802: Construction delivery errors (3 subtypes)
 * - 5000:       Unsupported media type (HTTP 415)
 *
 * **The `data` field:** [ErrorBodyDto.data] is a nullable [kotlinx.serialization.json.JsonObject]
 * that the API populates for some errors to carry supplementary context — for example, a
 * cooldown conflict may include the remaining cooldown duration, and a transit error may
 * include the expected arrival time. Every leaf constructor receives `data` as-is; callers
 * that need specific supplementary fields extract them from the `JsonObject` themselves.
 *
 * **Exhaustiveness guarantee:** The `else` branch maps any unrecognized code to
 * [SpaceTradersError.Unknown], carrying the original [code] and [message] for logging.
 * This prevents a `NoWhenBranchMatchedException` crash if the API introduces a new error
 * code before a new client version ships.
 *
 * @return The [SpaceTradersError] leaf that matches [ErrorBodyDto.code], or
 *   [SpaceTradersError.Unknown] if the code is not recognized.
 */
fun ErrorBodyDto.toDomain(): SpaceTradersError {
    return when (code) {
        // -------------------------------------------------------------------------
        // General errors (3000–3200)
        // Cross-cutting infrastructure errors that can surface on any endpoint.
        // -------------------------------------------------------------------------
        3000 -> SpaceTradersError.GeneralError.ResponseSerialization(code, message, data)
        3001 -> SpaceTradersError.GeneralError.UnprocessableInput(code, message, data)
        3002 -> SpaceTradersError.GeneralError.AllErrorHandlersFailed(code, message, data)
        3100 -> SpaceTradersError.GeneralError.SystemStatusMaintenance(code, message, data)
        // Code 3200 signals a universe reset — all stored agent tokens are invalidated.
        // The client should clear its saved token and prompt re-registration.
        3200 -> SpaceTradersError.GeneralError.Reset(code, message, data)

        // -------------------------------------------------------------------------
        // Standalone errors (4000–4001)
        // Not part of any category sealed class — each is a direct SpaceTradersError subtype.
        // -------------------------------------------------------------------------

        // 4000: An action was attempted while a ship's cooldown is still active.
        // The `data` field typically includes the remaining cooldown duration in seconds.
        4000 -> SpaceTradersError.CooldownConflict(code, message, data)
        4001 -> SpaceTradersError.WaypointNoAccess(code, message, data)

        // -------------------------------------------------------------------------
        // Auth errors (4100–4116)
        // Token and account authentication/authorization errors, 17 subtypes.
        // Callers handling `is AuthError` at the category level should clear the stored
        // token and redirect to the login/registration screen.
        // -------------------------------------------------------------------------
        4100 -> SpaceTradersError.AuthError.TokenEmpty(code, message, data)
        4101 -> SpaceTradersError.AuthError.TokenMissingSubject(code, message, data)
        4102 -> SpaceTradersError.AuthError.TokenInvalidSubject(code, message, data)
        4103 -> SpaceTradersError.AuthError.MissingTokenRequest(code, message, data)
        4104 -> SpaceTradersError.AuthError.InvalidTokenRequest(code, message, data)
        4105 -> SpaceTradersError.AuthError.InvalidTokenSubject(code, message, data)
        4106 -> SpaceTradersError.AuthError.AccountNotExists(code, message, data)
        4107 -> SpaceTradersError.AuthError.AgentNotExists(code, message, data)
        4108 -> SpaceTradersError.AuthError.AccountHasNoAgent(code, message, data)
        // 4109: Token was valid in a previous universe version but is now stale after a reset.
        4109 -> SpaceTradersError.AuthError.TokenInvalidVersion(code, message, data)
        4110 -> SpaceTradersError.AuthError.RegisterAgentSymbolReserved(code, message, data)
        4111 -> SpaceTradersError.AuthError.RegisterAgentConflictSymbol(code, message, data)
        4112 -> SpaceTradersError.AuthError.RegisterAgentNoStartingLocations(code, message, data)
        // 4113: Like 4109 but triggered specifically by the reset date embedded in the token.
        4113 -> SpaceTradersError.AuthError.TokenResetDateMismatch(code, message, data)
        // 4114: Wrong token type — e.g. POST /register requires AccountToken, not AgentToken.
        4114 -> SpaceTradersError.AuthError.InvalidAccountRole(code, message, data)
        4115 -> SpaceTradersError.AuthError.InvalidToken(code, message, data)
        4116 -> SpaceTradersError.AuthError.MissingAccountTokenRequest(code, message, data)

        // -------------------------------------------------------------------------
        // Navigation errors (4200–4204)
        // Errors specific to ship movement and routing, 5 subtypes.
        // -------------------------------------------------------------------------
        4200 -> SpaceTradersError.NavigationError.InTransit(code, message, data)
        4201 -> SpaceTradersError.NavigationError.InvalidDestination(code, message, data)
        4202 -> SpaceTradersError.NavigationError.OutsideSystem(code, message, data)
        4203 -> SpaceTradersError.NavigationError.InsufficientFuel(code, message, data)
        4204 -> SpaceTradersError.NavigationError.SameDestination(code, message, data)

        // -------------------------------------------------------------------------
        // Ship operation errors (4205–4271)
        // The largest category — 50 subtypes covering extraction, cargo, surveys, jumps,
        // warps, module/mount installation, refining, charting, scrapping, and transfers.
        // Note: codes are not strictly sequential (gaps at 4207–4213, 4225–4227, 4229).
        // -------------------------------------------------------------------------
        4205 -> SpaceTradersError.ShipOperationError.ExtractInvalidWaypoint(code, message, data)
        4206 -> SpaceTradersError.ShipOperationError.ExtractPermission(code, message, data)
        4214 -> SpaceTradersError.ShipOperationError.InTransit(code, message, data)
        4215 -> SpaceTradersError.ShipOperationError.MissingSensorArrays(code, message, data)
        4216 -> SpaceTradersError.ShipOperationError.PurchaseCredits(code, message, data)
        4217 -> SpaceTradersError.ShipOperationError.CargoExceedsLimit(code, message, data)
        4218 -> SpaceTradersError.ShipOperationError.CargoMissing(code, message, data)
        4219 -> SpaceTradersError.ShipOperationError.CargoUnitCount(code, message, data)
        4220 -> SpaceTradersError.ShipOperationError.SurveyVerification(code, message, data)
        4221 -> SpaceTradersError.ShipOperationError.SurveyExpiration(code, message, data)
        4222 -> SpaceTradersError.ShipOperationError.SurveyWaypointType(code, message, data)
        4223 -> SpaceTradersError.ShipOperationError.SurveyOrbit(code, message, data)
        4224 -> SpaceTradersError.ShipOperationError.SurveyExhausted(code, message, data)
        4228 -> SpaceTradersError.ShipOperationError.CargoFull(code, message, data)
        4230 -> SpaceTradersError.ShipOperationError.WaypointCharted(code, message, data)
        4231 -> SpaceTradersError.ShipOperationError.TransferShipNotFound(code, message, data)
        4232 -> SpaceTradersError.ShipOperationError.TransferAgentConflict(code, message, data)
        4233 -> SpaceTradersError.ShipOperationError.TransferSameShipConflict(code, message, data)
        4234 -> SpaceTradersError.ShipOperationError.TransferLocationConflict(code, message, data)
        4235 -> SpaceTradersError.ShipOperationError.WarpInsideSystem(code, message, data)
        4236 -> SpaceTradersError.ShipOperationError.NotInOrbit(code, message, data)
        4237 -> SpaceTradersError.ShipOperationError.InvalidRefineryGood(code, message, data)
        4238 -> SpaceTradersError.ShipOperationError.InvalidRefineryType(code, message, data)
        4239 -> SpaceTradersError.ShipOperationError.MissingRefinery(code, message, data)
        4240 -> SpaceTradersError.ShipOperationError.MissingSurveyor(code, message, data)
        4241 -> SpaceTradersError.ShipOperationError.MissingWarpDrive(code, message, data)
        4242 -> SpaceTradersError.ShipOperationError.MissingMineralProcessor(code, message, data)
        4243 -> SpaceTradersError.ShipOperationError.MissingMiningLasers(code, message, data)
        4244 -> SpaceTradersError.ShipOperationError.NotDocked(code, message, data)
        4245 -> SpaceTradersError.ShipOperationError.PurchaseNotPresent(code, message, data)
        4246 -> SpaceTradersError.ShipOperationError.MountNoShipyard(code, message, data)
        4247 -> SpaceTradersError.ShipOperationError.MissingMount(code, message, data)
        4248 -> SpaceTradersError.ShipOperationError.MountInsufficientCredits(code, message, data)
        4249 -> SpaceTradersError.ShipOperationError.MissingPower(code, message, data)
        4250 -> SpaceTradersError.ShipOperationError.MissingSlots(code, message, data)
        4251 -> SpaceTradersError.ShipOperationError.MissingMounts(code, message, data)
        4252 -> SpaceTradersError.ShipOperationError.MissingCrew(code, message, data)
        4253 -> SpaceTradersError.ShipOperationError.ExtractDestabilized(code, message, data)
        4254 -> SpaceTradersError.ShipOperationError.JumpInvalidOrigin(code, message, data)
        4255 -> SpaceTradersError.ShipOperationError.JumpInvalidWaypoint(code, message, data)
        4256 -> SpaceTradersError.ShipOperationError.JumpOriginUnderConstruction(code, message, data)
        4257 -> SpaceTradersError.ShipOperationError.MissingGasProcessor(code, message, data)
        4258 -> SpaceTradersError.ShipOperationError.MissingGasSiphons(code, message, data)
        4259 -> SpaceTradersError.ShipOperationError.SiphonInvalidWaypoint(code, message, data)
        4260 -> SpaceTradersError.ShipOperationError.SiphonPermission(code, message, data)
        4261 -> SpaceTradersError.ShipOperationError.WaypointNoYield(code, message, data)
        4262 -> SpaceTradersError.ShipOperationError.JumpDestinationUnderConstruction(code, message, data)
        4263 -> SpaceTradersError.ShipOperationError.ScrapInvalidTrait(code, message, data)
        4264 -> SpaceTradersError.ShipOperationError.RepairInvalidTrait(code, message, data)
        4265 -> SpaceTradersError.ShipOperationError.AgentInsufficientCredits(code, message, data)
        4266 -> SpaceTradersError.ShipOperationError.ModuleNoShipyard(code, message, data)
        4267 -> SpaceTradersError.ShipOperationError.ModuleNotInstalled(code, message, data)
        4268 -> SpaceTradersError.ShipOperationError.ModuleInsufficientCredits(code, message, data)
        4269 -> SpaceTradersError.ShipOperationError.CantSlowDownWhileInTransit(code, message, data)
        4270 -> SpaceTradersError.ShipOperationError.ExtractInvalidSurveyLocation(code, message, data)
        4271 -> SpaceTradersError.ShipOperationError.TransferDockedOrbitConflict(code, message, data)

        // -------------------------------------------------------------------------
        // Contract errors (4500–4511)
        // Contract lifecycle errors, 11 subtypes.
        // Note: code 4507 is not listed in the API error catalogue and has no mapping here.
        // -------------------------------------------------------------------------
        4500 -> SpaceTradersError.ContractError.AcceptNotAuthorized(code, message, data)
        4501 -> SpaceTradersError.ContractError.AcceptConflict(code, message, data)
        4502 -> SpaceTradersError.ContractError.FulfillDelivery(code, message, data)
        4503 -> SpaceTradersError.ContractError.Deadline(code, message, data)
        4504 -> SpaceTradersError.ContractError.Fulfilled(code, message, data)
        4505 -> SpaceTradersError.ContractError.NotAccepted(code, message, data)
        4506 -> SpaceTradersError.ContractError.NotAuthorized(code, message, data)
        4508 -> SpaceTradersError.ContractError.ShipDeliverTerms(code, message, data)
        4509 -> SpaceTradersError.ContractError.ShipDeliverFulfilled(code, message, data)
        4510 -> SpaceTradersError.ContractError.ShipDeliverInvalidLocation(code, message, data)
        4511 -> SpaceTradersError.ContractError.ExistingContract(code, message, data)

        // -------------------------------------------------------------------------
        // Market errors (4600–4605)
        // Marketplace trade errors, 5 subtypes.
        // Note: code 4603 is not listed in the API error catalogue and has no mapping here.
        // -------------------------------------------------------------------------
        4600 -> SpaceTradersError.MarketError.TradeInsufficientCredits(code, message, data)
        4601 -> SpaceTradersError.MarketError.TradeNoPurchase(code, message, data)
        4602 -> SpaceTradersError.MarketError.TradeNotSold(code, message, data)
        4604 -> SpaceTradersError.MarketError.TradeUnitLimit(code, message, data)
        4605 -> SpaceTradersError.MarketError.ShipNotAvailableForPurchase(code, message, data)

        // -------------------------------------------------------------------------
        // Standalone errors (continued)
        // -------------------------------------------------------------------------
        4700 -> SpaceTradersError.WaypointNoFaction(code, message, data)

        // -------------------------------------------------------------------------
        // Construction errors (4800–4802)
        // Errors when delivering materials to structures under construction, 3 subtypes.
        // -------------------------------------------------------------------------
        4800 -> SpaceTradersError.ConstructionError.MaterialNotRequired(code, message, data)
        4801 -> SpaceTradersError.ConstructionError.MaterialFulfilled(code, message, data)
        4802 -> SpaceTradersError.ConstructionError.ShipInvalidLocation(code, message, data)

        // -------------------------------------------------------------------------
        // HTTP-level error
        // -------------------------------------------------------------------------
        // 5000: HTTP 415 Unsupported Media Type. Should never occur when using
        // SpaceTradersClient since it sets Content-Type: application/json globally.
        5000 -> SpaceTradersError.UnsupportedMediaType(code, message, data)

        // -------------------------------------------------------------------------
        // Fallback: absorbs any error code the API adds after this client was compiled.
        // Prevents NoWhenBranchMatchedException at runtime; callers treat Unknown as a
        // generic "something went wrong" case and log the raw code and message.
        // -------------------------------------------------------------------------
        else -> SpaceTradersError.Unknown(code, message, data)
    }
}
