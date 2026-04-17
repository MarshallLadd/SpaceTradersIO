package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import kotlinx.serialization.json.JsonObject

/**
 * Sealed hierarchy of all structured errors the SpaceTraders API can return.
 *
 * **Pattern:** Sealed error hierarchy with category-level organization. In a new project, model
 * your API's error taxonomy as nested sealed classes that mirror the API's own error categories.
 * Each category is itself a sealed class so callers can either handle an entire category at once
 * (`is AuthError`) or drill into a specific subtype (`is AuthError.TokenEmpty`) — whichever level
 * of granularity is useful. Every leaf type carries [code] and [message] from the raw API
 * response so that logging is never lossy.
 *
 * **Benefits of this approach:**
 * 1. **Compiler-enforced exhaustiveness** — adding a new category to the sealed hierarchy
 *    produces a compile-time warning on every `when` expression that doesn't cover it.
 * 2. **Manageable `when` branches** — a ViewModel handling five categories needs at most five
 *    branches, not one per leaf type.
 * 3. **Structured data in errors** — `code` and `message` travel with the type rather than
 *    being encoded in a string that must be parsed back out.
 *
 * **In this project:** `SpaceTradersError` is deserialized from the API error body by
 * `SpaceTradersClient`'s `HttpCallValidator` and wrapped in a [SpaceTradersApiException] before
 * being thrown to callers. ViewModels catch `SpaceTradersApiException` and `when`-switch on
 * this type.
 *
 * **How to apply in a new project:** Survey your API's error codes list, group them into
 * logical categories (auth, navigation, resource operations, billing, etc.), create a sealed
 * subclass per category, and create a `data class` leaf per code. Add an `Unknown` fallback
 * as the last branch to absorb codes the API introduces after your client ships.
 *
 * @property code Numeric error code returned by the API. Matches the values in the OpenAPI spec's
 *   error codes list and is used by the deserialization layer to dispatch to the correct subtype.
 * @property message Human-readable description of the error from the API. Forwarded as the
 *   `Exception` message so standard logging captures it automatically.
 * @property data Optional additional JSON context provided by the API for some errors (e.g.,
 *   retry-after timing, conflicting field names). `null` when the API sends no extra data.
 */
sealed class SpaceTradersError {
    abstract val code: Int
    abstract val message: String
    abstract val data: JsonObject?

    // -------------------------------------------------------------------------
    // General errors — codes 3000-3200
    // -------------------------------------------------------------------------

    /**
     * Cross-cutting infrastructure and server-state errors that are not specific to any single
     * game domain. These five subtypes cover serialization failures, input validation rejections,
     * server maintenance windows, and universe resets. They can surface on virtually any endpoint
     * and typically require different handling than game-logic errors: maintenance and reset errors
     * should prompt the client to refresh its cached state, while serialization and unprocessable
     * errors usually indicate a client bug.
     */
    sealed class GeneralError : SpaceTradersError() {
        /** The server failed to serialize its own response; indicates a server-side bug. */
        data class ResponseSerialization(override val code: Int, override val message: String, override val data: JsonObject? = null) : GeneralError()

        /** The request body was syntactically valid JSON but semantically invalid for the endpoint. */
        data class UnprocessableInput(override val code: Int, override val message: String, override val data: JsonObject? = null) : GeneralError()

        /** An internal server error occurred that no specific handler could process. */
        data class AllErrorHandlersFailed(override val code: Int, override val message: String, override val data: JsonObject? = null) : GeneralError()

        /** The API is in a scheduled maintenance window; retry after the window closes. */
        data class SystemStatusMaintenance(override val code: Int, override val message: String, override val data: JsonObject? = null) : GeneralError()

        /**
         * The SpaceTraders universe has been reset; all saved agent tokens and game state are
         * now invalid. The client must prompt the user to re-register.
         */
        data class Reset(override val code: Int, override val message: String, override val data: JsonObject? = null) : GeneralError()
    }

    // -------------------------------------------------------------------------
    // Standalone errors — not part of any category sealed class
    // -------------------------------------------------------------------------

    /**
     * An action was attempted while a ship's cooldown timer was still active (e.g., trying to
     * extract resources before the extraction cooldown expires). The [data] field typically
     * includes the remaining cooldown duration.
     */
    data class CooldownConflict(override val code: Int, override val message: String, override val data: JsonObject? = null) : SpaceTradersError()

    /**
     * The agent does not have permission to access or interact with the requested waypoint
     * (e.g., it belongs to a faction the agent has no standing with).
     */
    data class WaypointNoAccess(override val code: Int, override val message: String, override val data: JsonObject? = null) : SpaceTradersError()

    // -------------------------------------------------------------------------
    // Auth errors — codes 4100-4116
    // -------------------------------------------------------------------------

    /**
     * Token and account authentication/authorization errors, covering codes 4100–4116.
     * These 17 subtypes span the full lifecycle of agent tokens: missing tokens, malformed JWTs,
     * wrong token types (AgentToken vs. AccountToken), version mismatches after a universe reset,
     * and registration conflicts. Callers handling `is AuthError` as a category should generally
     * redirect to the login/registration screen and clear any cached token.
     */
    sealed class AuthError : SpaceTradersError() {
        /** No bearer token was provided in the request. */
        data class TokenEmpty(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()

        /** The JWT is structurally valid but is missing the required `sub` claim. */
        data class TokenMissingSubject(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()

        /** The `sub` claim in the JWT does not match a known agent or account. */
        data class TokenInvalidSubject(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()

        /** A request that requires a token was made without including one. */
        data class MissingTokenRequest(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()

        /** The token included in the request is structurally malformed. */
        data class InvalidTokenRequest(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()

        /** The token's subject field references an entity type that is invalid for this endpoint. */
        data class InvalidTokenSubject(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()

        /** The account referenced by the token does not exist. */
        data class AccountNotExists(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()

        /** The agent referenced by the token does not exist. */
        data class AgentNotExists(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()

        /** The account exists but has no associated agent yet. */
        data class AccountHasNoAgent(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()

        /**
         * The token was issued for a previous universe version and is no longer valid after a
         * universe reset. The user must re-register to obtain a new token.
         */
        data class TokenInvalidVersion(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()

        /** The requested agent call sign is reserved and cannot be used for new registrations. */
        data class RegisterAgentSymbolReserved(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()

        /** An agent with the requested call sign already exists; call signs must be unique. */
        data class RegisterAgentConflictSymbol(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()

        /** No valid starting locations are currently available for new agent registration. */
        data class RegisterAgentNoStartingLocations(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()

        /**
         * The token was created before the most recent universe reset date; treat this like
         * [TokenInvalidVersion] — clear the stored token and prompt re-registration.
         */
        data class TokenResetDateMismatch(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()

        /**
         * An AccountToken was used on an endpoint that requires a different role, or vice versa.
         * Note: `POST /register` requires an AccountToken — sending an AgentToken returns this
         * error.
         */
        data class InvalidAccountRole(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()

        /** The bearer token is invalid, expired, or has been revoked. */
        data class InvalidToken(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()

        /** An operation that requires an AccountToken was attempted without one. */
        data class MissingAccountTokenRequest(override val code: Int, override val message: String, override val data: JsonObject? = null) : AuthError()
    }

    // -------------------------------------------------------------------------
    // Navigation errors — codes 4200-4204
    // -------------------------------------------------------------------------

    /**
     * Errors specific to ship movement and routing, covering codes 4200–4204.
     * These 5 subtypes represent the most common failure modes when navigating: the ship is
     * already en route ([InTransit]), the destination symbol is malformed ([InvalidDestination]),
     * the destination is in a different star system ([OutsideSystem]), the ship lacks enough
     * fuel ([InsufficientFuel]), or the ship is already at the requested waypoint
     * ([SameDestination]). Most respond with HTTP 400.
     */
    sealed class NavigationError : SpaceTradersError() {
        /** The ship is currently in transit and cannot accept new navigation commands. */
        data class InTransit(override val code: Int, override val message: String, override val data: JsonObject? = null) : NavigationError()

        /** The destination waypoint symbol is invalid or does not exist. */
        data class InvalidDestination(override val code: Int, override val message: String, override val data: JsonObject? = null) : NavigationError()

        /** The destination waypoint is in a different star system than the ship's current system. */
        data class OutsideSystem(override val code: Int, override val message: String, override val data: JsonObject? = null) : NavigationError()

        /** The ship does not have enough fuel to reach the destination. */
        data class InsufficientFuel(override val code: Int, override val message: String, override val data: JsonObject? = null) : NavigationError()

        /** The ship is already located at the requested destination waypoint. */
        data class SameDestination(override val code: Int, override val message: String, override val data: JsonObject? = null) : NavigationError()
    }

    // -------------------------------------------------------------------------
    // Ship operation errors — codes 4205-4271
    // -------------------------------------------------------------------------

    /**
     * Errors arising from ship actions other than navigation, covering codes 4205–4271.
     * This is the largest category with 50 subtypes spanning: resource extraction and siphoning,
     * cargo management, surveys, jump/warp travel, module and mount installation, refining,
     * charting, scrapping, repairing, and cargo transfers. Typical HTTP status is 400 for
     * precondition failures and 409 for state conflicts (e.g., ship not in the required orbit
     * or dock state). Use category-level handling (`is ShipOperationError`) for generic ship
     * error UI, and subtype handling for cases where specific recovery actions exist (e.g.,
     * prompting the player to jettison cargo on [CargoFull]).
     */
    sealed class ShipOperationError : SpaceTradersError() {
        /** Extraction was attempted at a waypoint type that does not support it. */
        data class ExtractInvalidWaypoint(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The ship does not have permission to extract resources at this waypoint. */
        data class ExtractPermission(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The ship is currently in transit and cannot perform this operation. */
        data class InTransit(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The ship lacks a sensor array mount required for the requested scan operation. */
        data class MissingSensorArrays(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The agent has insufficient credits to complete the requested ship purchase. */
        data class PurchaseCredits(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** Adding the cargo would exceed the ship's maximum cargo capacity. */
        data class CargoExceedsLimit(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The requested cargo item is not present in the ship's hold. */
        data class CargoMissing(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The requested cargo unit count is invalid (e.g., zero or negative). */
        data class CargoUnitCount(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The survey signature could not be verified (malformed or tampered survey data). */
        data class SurveyVerification(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The survey has expired and can no longer be used for targeted extraction. */
        data class SurveyExpiration(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The survey was conducted at a different waypoint type than the current extraction target. */
        data class SurveyWaypointType(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The ship must be in orbit to conduct a survey. */
        data class SurveyOrbit(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** All deposits identified in this survey have been fully extracted. */
        data class SurveyExhausted(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The ship's cargo hold is full; cargo must be jettisoned or sold before adding more. */
        data class CargoFull(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** This waypoint has already been charted and cannot be charted again. */
        data class WaypointCharted(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The target ship for a cargo transfer could not be found. */
        data class TransferShipNotFound(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The target ship for a transfer belongs to a different agent. */
        data class TransferAgentConflict(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** A cargo transfer cannot be made to or from the same ship. */
        data class TransferSameShipConflict(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The two ships involved in a transfer are not at the same waypoint. */
        data class TransferLocationConflict(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** Warp travel was attempted to a waypoint within the same star system; use navigate instead. */
        data class WarpInsideSystem(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The ship must be in orbit to perform this operation but is currently docked. */
        data class NotInOrbit(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The requested good cannot be produced by the ship's refinery. */
        data class InvalidRefineryGood(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The ship's installed module is not the correct type of refinery for the requested output. */
        data class InvalidRefineryType(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The ship does not have a refinery module installed. */
        data class MissingRefinery(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The ship does not have a surveyor mount required for conducting surveys. */
        data class MissingSurveyor(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The ship does not have a warp drive module required for inter-system warp travel. */
        data class MissingWarpDrive(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The ship does not have a mineral processor module required for refining mined ore. */
        data class MissingMineralProcessor(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The ship does not have mining laser mounts required for resource extraction. */
        data class MissingMiningLasers(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The ship must be docked to perform this operation but is currently in orbit. */
        data class NotDocked(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The requested ship is not currently available for purchase at this shipyard. */
        data class PurchaseNotPresent(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** Mount installation or removal requires a shipyard but the ship is not at one. */
        data class MountNoShipyard(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The specified mount is not installed on this ship. */
        data class MissingMount(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The agent does not have enough credits to pay for mount installation or removal. */
        data class MountInsufficientCredits(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** Installing the component would exceed the ship's available power capacity. */
        data class MissingPower(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The ship does not have enough open module or mount slots to install the component. */
        data class MissingSlots(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The ship does not have the required mount type needed for this operation. */
        data class MissingMounts(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The ship does not have enough crew to operate the requested equipment or perform the action. */
        data class MissingCrew(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** Extraction has destabilized the deposit; continued extraction is no longer possible here. */
        data class ExtractDestabilized(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The jump drive origin waypoint is not a valid jump gate. */
        data class JumpInvalidOrigin(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The jump drive destination waypoint is not a valid jump gate. */
        data class JumpInvalidWaypoint(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The jump gate at the ship's current location is still under construction and cannot be used. */
        data class JumpOriginUnderConstruction(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The ship does not have a gas processor module required for siphoning gas giants. */
        data class MissingGasProcessor(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The ship does not have gas siphon mounts required for siphoning gas giants. */
        data class MissingGasSiphons(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** Gas siphoning was attempted at a waypoint type that is not a gas giant. */
        data class SiphonInvalidWaypoint(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The ship does not have permission to siphon resources at this waypoint. */
        data class SiphonPermission(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The waypoint has no extractable or siphonables resources to yield. */
        data class WaypointNoYield(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The jump gate at the destination is still under construction and cannot accept arrivals. */
        data class JumpDestinationUnderConstruction(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The waypoint does not have the salvage trait required for the scrap operation. */
        data class ScrapInvalidTrait(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The waypoint does not have the repair dock trait required for the repair operation. */
        data class RepairInvalidTrait(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The agent does not have enough credits to perform this ship operation. */
        data class AgentInsufficientCredits(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** Module installation or removal requires a shipyard but the ship is not at one. */
        data class ModuleNoShipyard(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The specified module is not installed on this ship. */
        data class ModuleNotInstalled(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The agent does not have enough credits to pay for module installation or removal. */
        data class ModuleInsufficientCredits(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The ship's flight mode cannot be changed while it is currently in transit. */
        data class CantSlowDownWhileInTransit(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /** The survey was conducted at a different waypoint than the ship's current extraction location. */
        data class ExtractInvalidSurveyLocation(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()

        /**
         * A cargo transfer was attempted between two ships where one is docked and the other is in
         * orbit; both ships must be in the same nav state.
         */
        data class TransferDockedOrbitConflict(override val code: Int, override val message: String, override val data: JsonObject? = null) : ShipOperationError()
    }

    // -------------------------------------------------------------------------
    // Contract errors — codes 4500-4511
    // -------------------------------------------------------------------------

    /**
     * Errors arising from contract lifecycle operations, covering codes 4500–4511.
     * These 11 subtypes cover every stage of contract management: accepting, delivering cargo,
     * fulfilling, and authorization. Common HTTP status codes are 400 (precondition not met) and
     * 409 (state conflict, e.g., trying to accept an already-accepted contract). Handle
     * `is ContractError` at the category level to show a generic contract error message, and
     * handle specific subtypes (e.g., [ContractError.Deadline]) when expiry-specific recovery
     * UI is needed.
     */
    sealed class ContractError : SpaceTradersError() {
        /** The agent is not authorized to accept this contract (it belongs to another agent). */
        data class AcceptNotAuthorized(override val code: Int, override val message: String, override val data: JsonObject? = null) : ContractError()

        /** The contract has already been accepted and cannot be accepted again. */
        data class AcceptConflict(override val code: Int, override val message: String, override val data: JsonObject? = null) : ContractError()

        /** The delivery requirements for contract fulfillment have not been fully met. */
        data class FulfillDelivery(override val code: Int, override val message: String, override val data: JsonObject? = null) : ContractError()

        /** The contract deadline has passed; it can no longer be fulfilled. */
        data class Deadline(override val code: Int, override val message: String, override val data: JsonObject? = null) : ContractError()

        /** The contract has already been fulfilled and no further deliveries are accepted. */
        data class Fulfilled(override val code: Int, override val message: String, override val data: JsonObject? = null) : ContractError()

        /** An operation requiring an accepted contract was attempted on one that has not been accepted yet. */
        data class NotAccepted(override val code: Int, override val message: String, override val data: JsonObject? = null) : ContractError()

        /** The agent is not authorized to perform this operation on the contract. */
        data class NotAuthorized(override val code: Int, override val message: String, override val data: JsonObject? = null) : ContractError()

        /** The delivery does not match the goods or quantities specified in the contract terms. */
        data class ShipDeliverTerms(override val code: Int, override val message: String, override val data: JsonObject? = null) : ContractError()

        /** A delivery was attempted on a contract that is already fulfilled. */
        data class ShipDeliverFulfilled(override val code: Int, override val message: String, override val data: JsonObject? = null) : ContractError()

        /** The ship attempted a delivery at a waypoint other than the one specified in the contract. */
        data class ShipDeliverInvalidLocation(override val code: Int, override val message: String, override val data: JsonObject? = null) : ContractError()

        /** The agent already has an active contract and cannot accept another until it is resolved. */
        data class ExistingContract(override val code: Int, override val message: String, override val data: JsonObject? = null) : ContractError()
    }

    // -------------------------------------------------------------------------
    // Market errors — codes 4600-4605
    // -------------------------------------------------------------------------

    /**
     * Errors arising from marketplace trade operations, covering codes 4600–4605.
     * These 5 subtypes represent the common failure modes when buying or selling goods and ships:
     * insufficient credits, the good not being traded at this market, unit limits, and ship
     * availability. These typically arrive with HTTP 400. Handle `is MarketError` for generic
     * trading UI, and drill into [MarketError.TradeInsufficientCredits] when you want to prompt
     * the player to earn more credits.
     */
    sealed class MarketError : SpaceTradersError() {
        /** The agent does not have enough credits to complete the purchase. */
        data class TradeInsufficientCredits(override val code: Int, override val message: String, override val data: JsonObject? = null) : MarketError()

        /** The requested good is not available for purchase at this market. */
        data class TradeNoPurchase(override val code: Int, override val message: String, override val data: JsonObject? = null) : MarketError()

        /** The market does not buy the offered good. */
        data class TradeNotSold(override val code: Int, override val message: String, override val data: JsonObject? = null) : MarketError()

        /** The trade quantity exceeds the market's per-transaction unit limit. */
        data class TradeUnitLimit(override val code: Int, override val message: String, override val data: JsonObject? = null) : MarketError()

        /** The requested ship type is not currently available for purchase at this shipyard. */
        data class ShipNotAvailableForPurchase(override val code: Int, override val message: String, override val data: JsonObject? = null) : MarketError()
    }

    // -------------------------------------------------------------------------
    // Standalone errors (continued)
    // -------------------------------------------------------------------------

    /**
     * The waypoint does not belong to any faction, so faction-gated operations (e.g., obtaining
     * certain contracts) are not available here.
     */
    data class WaypointNoFaction(override val code: Int, override val message: String, override val data: JsonObject? = null) : SpaceTradersError()

    // -------------------------------------------------------------------------
    // Construction errors — codes 4800-4802
    // -------------------------------------------------------------------------

    /**
     * Errors arising from delivering construction materials to jump gates or other structures
     * under construction, covering codes 4800–4802. These 3 subtypes capture the core failure
     * modes: the material is not on the required list, the required amount has already been
     * delivered, and the delivering ship is not at the construction site. Handle `is
     * ConstructionError` for a generic "construction delivery failed" message.
     */
    sealed class ConstructionError : SpaceTradersError() {
        /** The delivered material is not one of the resources required to complete construction. */
        data class MaterialNotRequired(override val code: Int, override val message: String, override val data: JsonObject? = null) : ConstructionError()

        /** The required quantity of this material has already been delivered; no more is needed. */
        data class MaterialFulfilled(override val code: Int, override val message: String, override val data: JsonObject? = null) : ConstructionError()

        /** The ship is not at the waypoint that is under construction. */
        data class ShipInvalidLocation(override val code: Int, override val message: String, override val data: JsonObject? = null) : ConstructionError()
    }

    // -------------------------------------------------------------------------
    // HTTP-level error
    // -------------------------------------------------------------------------

    /**
     * The request was sent with a `Content-Type` the server does not support (HTTP 415).
     * In practice this should never occur when using `SpaceTradersClient` because it sets
     * `Content-Type: application/json` globally via `defaultRequest`.
     */
    data class UnsupportedMediaType(override val code: Int, override val message: String, override val data: JsonObject? = null) : SpaceTradersError()

    // -------------------------------------------------------------------------
    // Fallback
    // -------------------------------------------------------------------------

    /**
     * Fallback for any API error code that is not mapped to a known subtype.
     *
     * The SpaceTraders API is actively developed and introduces new error codes between client
     * releases. Without this fallback, a `when` expression on [SpaceTradersError] would require
     * a recompile every time the API added a new code — and more importantly, an unhandled code
     * at runtime would throw a `NoWhenBranchMatchedException`. `Unknown` absorbs unrecognized
     * codes gracefully so clients can treat them as generic "something went wrong" cases.
     *
     * Always include `is SpaceTradersError.Unknown -> showGenericError(e.error.message)` as the
     * final branch in any `when` expression that handles [SpaceTradersError].
     */
    data class Unknown(override val code: Int, override val message: String, override val data: JsonObject? = null) : SpaceTradersError()
}
