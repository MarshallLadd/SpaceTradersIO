package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import kotlinx.serialization.json.JsonObject

sealed class SpaceTradersError {
    abstract val code: Int
    abstract val message: String
    abstract val data: JsonObject?

    // 3000-3200: Serialization, validation, maintenance, reset
    sealed class GeneralError : SpaceTradersError() {
        data class ResponseSerialization(override val code: Int, override val message: String, override val data: JsonObject? = null) : GeneralError()
        data class UnprocessableInput(override val code: Int, override val message: String, override val data: JsonObject? = null) : GeneralError()
        data class AllErrorHandlersFailed(override val code: Int, override val message: String, override val data: JsonObject? = null) : GeneralError()
        data class SystemStatusMaintenance(override val code: Int, override val message: String, override val data: JsonObject? = null) : GeneralError()
        data class Reset(override val code: Int, override val message: String, override val data: JsonObject? = null) : GeneralError()
    }

    // 4000: Cooldown conflict
    data class CooldownConflict(override val code: Int, override val message: String, override val data: JsonObject? = null) : SpaceTradersError()

    // 4001: Waypoint access
    data class WaypointNoAccess(override val code: Int, override val message: String, override val data: JsonObject? = null) : SpaceTradersError()

    // 4100-4116: Token and account errors
    sealed class AuthError : SpaceTradersError() {
        data class TokenEmpty(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()
        data class TokenMissingSubject(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()
        data class TokenInvalidSubject(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()
        data class MissingTokenRequest(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()
        data class InvalidTokenRequest(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()
        data class InvalidTokenSubject(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()
        data class AccountNotExists(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()
        data class AgentNotExists(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()
        data class AccountHasNoAgent(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()
        data class TokenInvalidVersion(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()
        data class RegisterAgentSymbolReserved(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()
        data class RegisterAgentConflictSymbol(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()
        data class RegisterAgentNoStartingLocations(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()
        data class TokenResetDateMismatch(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()
        data class InvalidAccountRole(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()
        data class InvalidToken(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()
        data class MissingAccountTokenRequest(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()
    }

    // 4200-4204: Navigation errors
    sealed class NavigationError : SpaceTradersError() {
        data class InTransit(override val code: Int, override val message: String, override val data: JsonObject? = null) : NavigationError()
        data class InvalidDestination(override val code: Int, override val message: String, override val data: JsonObject? = null) : NavigationError()
        data class OutsideSystem(override val code: Int, override val message: String, override val data: JsonObject? = null) : NavigationError()
        data class InsufficientFuel(override val code: Int, override val message: String, override val data: JsonObject? = null) : NavigationError()
        data class SameDestination(override val code: Int, override val message: String, override val data: JsonObject? = null) : NavigationError()
    }

    // 4205-4271: Ship operation errors (extraction, cargo, survey, modules, mounts, etc.)
    sealed class ShipOperationError : SpaceTradersError() {
        data class ExtractInvalidWaypoint(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class ExtractPermission(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class InTransit(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class MissingSensorArrays(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class PurchaseCredits(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class CargoExceedsLimit(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class CargoMissing(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class CargoUnitCount(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class SurveyVerification(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class SurveyExpiration(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class SurveyWaypointType(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class SurveyOrbit(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class SurveyExhausted(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class CargoFull(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class WaypointCharted(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class TransferShipNotFound(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class TransferAgentConflict(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class TransferSameShipConflict(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class TransferLocationConflict(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class WarpInsideSystem(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class NotInOrbit(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class InvalidRefineryGood(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class InvalidRefineryType(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class MissingRefinery(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class MissingSurveyor(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class MissingWarpDrive(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class MissingMineralProcessor(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class MissingMiningLasers(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class NotDocked(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class PurchaseNotPresent(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class MountNoShipyard(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class MissingMount(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class MountInsufficientCredits(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class MissingPower(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class MissingSlots(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class MissingMounts(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class MissingCrew(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class ExtractDestabilized(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class JumpInvalidOrigin(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class JumpInvalidWaypoint(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class JumpOriginUnderConstruction(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class MissingGasProcessor(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class MissingGasSiphons(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class SiphonInvalidWaypoint(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class SiphonPermission(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class WaypointNoYield(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class JumpDestinationUnderConstruction(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class ScrapInvalidTrait(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class RepairInvalidTrait(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class AgentInsufficientCredits(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class ModuleNoShipyard(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class ModuleNotInstalled(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class ModuleInsufficientCredits(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class CantSlowDownWhileInTransit(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class ExtractInvalidSurveyLocation(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
        data class TransferDockedOrbitConflict(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
    }

    // 4500-4511: Contract errors
    sealed class ContractError : SpaceTradersError() {
        data class AcceptNotAuthorized(override val code: Int, override val message: String, override val data: JsonObject? = null) : ContractError()
        data class AcceptConflict(override val code: Int, override val message: String, override val data: JsonObject? = null) : ContractError()
        data class FulfillDelivery(override val code: Int, override val message: String, override val data: JsonObject? = null) : ContractError()
        data class Deadline(override val code: Int, override val message: String, override val data: JsonObject? = null) : ContractError()
        data class Fulfilled(override val code: Int, override val message: String, override val data: JsonObject? = null) : ContractError()
        data class NotAccepted(override val code: Int, override val message: String, override val data: JsonObject? = null) : ContractError()
        data class NotAuthorized(override val code: Int, override val message: String, override val data: JsonObject? = null) : ContractError()
        data class ShipDeliverTerms(override val code: Int, override val message: String, override val data: JsonObject? = null) : ContractError()
        data class ShipDeliverFulfilled(override val code: Int, override val message: String, override val data: JsonObject? = null) : ContractError()
        data class ShipDeliverInvalidLocation(override val code: Int, override val message: String, override val data: JsonObject? = null) : ContractError()
        data class ExistingContract(override val code: Int, override val message: String, override val data: JsonObject? = null) : ContractError()
    }

    // 4600-4605: Market/trade errors
    sealed class MarketError : SpaceTradersError() {
        data class TradeInsufficientCredits(override val code: Int, override val message: String, override val data: JsonObject? = null) : MarketError()
        data class TradeNoPurchase(override val code: Int, override val message: String, override val data: JsonObject? = null) : MarketError()
        data class TradeNotSold(override val code: Int, override val message: String, override val data: JsonObject? = null) : MarketError()
        data class TradeUnitLimit(override val code: Int, override val message: String, override val data: JsonObject? = null) : MarketError()
        data class ShipNotAvailableForPurchase(override val code: Int, override val message: String, override val data: JsonObject? = null) : MarketError()
    }

    // 4700: Waypoint faction
    data class WaypointNoFaction(override val code: Int, override val message: String, override val data: JsonObject? = null) : SpaceTradersError()

    // 4800-4802: Construction errors
    sealed class ConstructionError : SpaceTradersError() {
        data class MaterialNotRequired(override val code: Int, override val message: String, override val data: JsonObject? = null) : ConstructionError()
        data class MaterialFulfilled(override val code: Int, override val message: String, override val data: JsonObject? = null) : ConstructionError()
        data class ShipInvalidLocation(override val code: Int, override val message: String, override val data: JsonObject? = null) : ConstructionError()
    }

    // 5000: Unsupported media type
    data class UnsupportedMediaType(override val code: Int, override val message: String, override val data: JsonObject? = null) : SpaceTradersError()

    // Fallback for unrecognized error codes
    data class Unknown(override val code: Int, override val message: String, override val data: JsonObject? = null) : SpaceTradersError()
}
