package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ErrorBodyDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ErrorMapperTest {

    private fun mapCode(code: Int): SpaceTradersError =
        ErrorBodyDto(code = code, message = "test message").toDomain()

    // -- General errors --

    @Test fun code3000_mapsToResponseSerialization() { assertIs<SpaceTradersError.GeneralError.ResponseSerialization>(mapCode(3000)) }
    @Test fun code3001_mapsToUnprocessableInput() { assertIs<SpaceTradersError.GeneralError.UnprocessableInput>(mapCode(3001)) }
    @Test fun code3002_mapsToAllErrorHandlersFailed() { assertIs<SpaceTradersError.GeneralError.AllErrorHandlersFailed>(mapCode(3002)) }
    @Test fun code3100_mapsToSystemStatusMaintenance() { assertIs<SpaceTradersError.GeneralError.SystemStatusMaintenance>(mapCode(3100)) }
    @Test fun code3200_mapsToReset() { assertIs<SpaceTradersError.GeneralError.Reset>(mapCode(3200)) }

    // -- Cooldown --

    @Test fun code4000_mapsToCooldownConflict() { assertIs<SpaceTradersError.CooldownConflict>(mapCode(4000)) }

    // -- Waypoint access --

    @Test fun code4001_mapsToWaypointNoAccess() { assertIs<SpaceTradersError.WaypointNoAccess>(mapCode(4001)) }

    // -- Auth errors --

    @Test fun code4100_mapsToTokenEmpty() { assertIs<SpaceTradersError.AuthError.TokenEmpty>(mapCode(4100)) }
    @Test fun code4101_mapsToTokenMissingSubject() { assertIs<SpaceTradersError.AuthError.TokenMissingSubject>(mapCode(4101)) }
    @Test fun code4102_mapsToTokenInvalidSubject() { assertIs<SpaceTradersError.AuthError.TokenInvalidSubject>(mapCode(4102)) }
    @Test fun code4103_mapsToMissingTokenRequest() { assertIs<SpaceTradersError.AuthError.MissingTokenRequest>(mapCode(4103)) }
    @Test fun code4104_mapsToInvalidTokenRequest() { assertIs<SpaceTradersError.AuthError.InvalidTokenRequest>(mapCode(4104)) }
    @Test fun code4105_mapsToInvalidTokenSubject() { assertIs<SpaceTradersError.AuthError.InvalidTokenSubject>(mapCode(4105)) }
    @Test fun code4106_mapsToAccountNotExists() { assertIs<SpaceTradersError.AuthError.AccountNotExists>(mapCode(4106)) }
    @Test fun code4107_mapsToAgentNotExists() { assertIs<SpaceTradersError.AuthError.AgentNotExists>(mapCode(4107)) }
    @Test fun code4108_mapsToAccountHasNoAgent() { assertIs<SpaceTradersError.AuthError.AccountHasNoAgent>(mapCode(4108)) }
    @Test fun code4109_mapsToTokenInvalidVersion() { assertIs<SpaceTradersError.AuthError.TokenInvalidVersion>(mapCode(4109)) }
    @Test fun code4110_mapsToRegisterAgentSymbolReserved() { assertIs<SpaceTradersError.AuthError.RegisterAgentSymbolReserved>(mapCode(4110)) }
    @Test fun code4111_mapsToRegisterAgentConflictSymbol() { assertIs<SpaceTradersError.AuthError.RegisterAgentConflictSymbol>(mapCode(4111)) }
    @Test fun code4112_mapsToRegisterAgentNoStartingLocations() { assertIs<SpaceTradersError.AuthError.RegisterAgentNoStartingLocations>(mapCode(4112)) }
    @Test fun code4113_mapsToTokenResetDateMismatch() { assertIs<SpaceTradersError.AuthError.TokenResetDateMismatch>(mapCode(4113)) }
    @Test fun code4114_mapsToInvalidAccountRole() { assertIs<SpaceTradersError.AuthError.InvalidAccountRole>(mapCode(4114)) }
    @Test fun code4115_mapsToInvalidToken() { assertIs<SpaceTradersError.AuthError.InvalidToken>(mapCode(4115)) }
    @Test fun code4116_mapsToMissingAccountTokenRequest() { assertIs<SpaceTradersError.AuthError.MissingAccountTokenRequest>(mapCode(4116)) }

    // -- Navigation errors --

    @Test fun code4200_mapsToInTransit() { assertIs<SpaceTradersError.NavigationError.InTransit>(mapCode(4200)) }
    @Test fun code4201_mapsToInvalidDestination() { assertIs<SpaceTradersError.NavigationError.InvalidDestination>(mapCode(4201)) }
    @Test fun code4202_mapsToOutsideSystem() { assertIs<SpaceTradersError.NavigationError.OutsideSystem>(mapCode(4202)) }
    @Test fun code4203_mapsToInsufficientFuel() { assertIs<SpaceTradersError.NavigationError.InsufficientFuel>(mapCode(4203)) }
    @Test fun code4204_mapsToSameDestination() { assertIs<SpaceTradersError.NavigationError.SameDestination>(mapCode(4204)) }

    // -- Ship operation errors --

    @Test fun code4205_mapsToExtractInvalidWaypoint() { assertIs<SpaceTradersError.ShipOperationError.ExtractInvalidWaypoint>(mapCode(4205)) }
    @Test fun code4206_mapsToExtractPermission() { assertIs<SpaceTradersError.ShipOperationError.ExtractPermission>(mapCode(4206)) }
    @Test fun code4214_mapsToShipInTransit() { assertIs<SpaceTradersError.ShipOperationError.InTransit>(mapCode(4214)) }
    @Test fun code4215_mapsToMissingSensorArrays() { assertIs<SpaceTradersError.ShipOperationError.MissingSensorArrays>(mapCode(4215)) }
    @Test fun code4216_mapsToPurchaseCredits() { assertIs<SpaceTradersError.ShipOperationError.PurchaseCredits>(mapCode(4216)) }
    @Test fun code4217_mapsToCargoExceedsLimit() { assertIs<SpaceTradersError.ShipOperationError.CargoExceedsLimit>(mapCode(4217)) }
    @Test fun code4218_mapsToCargoMissing() { assertIs<SpaceTradersError.ShipOperationError.CargoMissing>(mapCode(4218)) }
    @Test fun code4219_mapsToCargoUnitCount() { assertIs<SpaceTradersError.ShipOperationError.CargoUnitCount>(mapCode(4219)) }
    @Test fun code4220_mapsToSurveyVerification() { assertIs<SpaceTradersError.ShipOperationError.SurveyVerification>(mapCode(4220)) }
    @Test fun code4221_mapsToSurveyExpiration() { assertIs<SpaceTradersError.ShipOperationError.SurveyExpiration>(mapCode(4221)) }
    @Test fun code4222_mapsToSurveyWaypointType() { assertIs<SpaceTradersError.ShipOperationError.SurveyWaypointType>(mapCode(4222)) }
    @Test fun code4223_mapsToSurveyOrbit() { assertIs<SpaceTradersError.ShipOperationError.SurveyOrbit>(mapCode(4223)) }
    @Test fun code4224_mapsToSurveyExhausted() { assertIs<SpaceTradersError.ShipOperationError.SurveyExhausted>(mapCode(4224)) }
    @Test fun code4228_mapsToCargoFull() { assertIs<SpaceTradersError.ShipOperationError.CargoFull>(mapCode(4228)) }
    @Test fun code4230_mapsToWaypointCharted() { assertIs<SpaceTradersError.ShipOperationError.WaypointCharted>(mapCode(4230)) }
    @Test fun code4231_mapsToTransferShipNotFound() { assertIs<SpaceTradersError.ShipOperationError.TransferShipNotFound>(mapCode(4231)) }
    @Test fun code4232_mapsToTransferAgentConflict() { assertIs<SpaceTradersError.ShipOperationError.TransferAgentConflict>(mapCode(4232)) }
    @Test fun code4233_mapsToTransferSameShipConflict() { assertIs<SpaceTradersError.ShipOperationError.TransferSameShipConflict>(mapCode(4233)) }
    @Test fun code4234_mapsToTransferLocationConflict() { assertIs<SpaceTradersError.ShipOperationError.TransferLocationConflict>(mapCode(4234)) }
    @Test fun code4235_mapsToWarpInsideSystem() { assertIs<SpaceTradersError.ShipOperationError.WarpInsideSystem>(mapCode(4235)) }
    @Test fun code4236_mapsToNotInOrbit() { assertIs<SpaceTradersError.ShipOperationError.NotInOrbit>(mapCode(4236)) }
    @Test fun code4237_mapsToInvalidRefineryGood() { assertIs<SpaceTradersError.ShipOperationError.InvalidRefineryGood>(mapCode(4237)) }
    @Test fun code4238_mapsToInvalidRefineryType() { assertIs<SpaceTradersError.ShipOperationError.InvalidRefineryType>(mapCode(4238)) }
    @Test fun code4239_mapsToMissingRefinery() { assertIs<SpaceTradersError.ShipOperationError.MissingRefinery>(mapCode(4239)) }
    @Test fun code4240_mapsToMissingSurveyor() { assertIs<SpaceTradersError.ShipOperationError.MissingSurveyor>(mapCode(4240)) }
    @Test fun code4241_mapsToMissingWarpDrive() { assertIs<SpaceTradersError.ShipOperationError.MissingWarpDrive>(mapCode(4241)) }
    @Test fun code4242_mapsToMissingMineralProcessor() { assertIs<SpaceTradersError.ShipOperationError.MissingMineralProcessor>(mapCode(4242)) }
    @Test fun code4243_mapsToMissingMiningLasers() { assertIs<SpaceTradersError.ShipOperationError.MissingMiningLasers>(mapCode(4243)) }
    @Test fun code4244_mapsToNotDocked() { assertIs<SpaceTradersError.ShipOperationError.NotDocked>(mapCode(4244)) }
    @Test fun code4245_mapsToPurchaseNotPresent() { assertIs<SpaceTradersError.ShipOperationError.PurchaseNotPresent>(mapCode(4245)) }
    @Test fun code4246_mapsToMountNoShipyard() { assertIs<SpaceTradersError.ShipOperationError.MountNoShipyard>(mapCode(4246)) }
    @Test fun code4247_mapsToMissingMount() { assertIs<SpaceTradersError.ShipOperationError.MissingMount>(mapCode(4247)) }
    @Test fun code4248_mapsToMountInsufficientCredits() { assertIs<SpaceTradersError.ShipOperationError.MountInsufficientCredits>(mapCode(4248)) }
    @Test fun code4249_mapsToMissingPower() { assertIs<SpaceTradersError.ShipOperationError.MissingPower>(mapCode(4249)) }
    @Test fun code4250_mapsToMissingSlots() { assertIs<SpaceTradersError.ShipOperationError.MissingSlots>(mapCode(4250)) }
    @Test fun code4251_mapsToMissingMounts() { assertIs<SpaceTradersError.ShipOperationError.MissingMounts>(mapCode(4251)) }
    @Test fun code4252_mapsToMissingCrew() { assertIs<SpaceTradersError.ShipOperationError.MissingCrew>(mapCode(4252)) }
    @Test fun code4253_mapsToExtractDestabilized() { assertIs<SpaceTradersError.ShipOperationError.ExtractDestabilized>(mapCode(4253)) }
    @Test fun code4254_mapsToJumpInvalidOrigin() { assertIs<SpaceTradersError.ShipOperationError.JumpInvalidOrigin>(mapCode(4254)) }
    @Test fun code4255_mapsToJumpInvalidWaypoint() { assertIs<SpaceTradersError.ShipOperationError.JumpInvalidWaypoint>(mapCode(4255)) }
    @Test fun code4256_mapsToJumpOriginUnderConstruction() { assertIs<SpaceTradersError.ShipOperationError.JumpOriginUnderConstruction>(mapCode(4256)) }
    @Test fun code4257_mapsToMissingGasProcessor() { assertIs<SpaceTradersError.ShipOperationError.MissingGasProcessor>(mapCode(4257)) }
    @Test fun code4258_mapsToMissingGasSiphons() { assertIs<SpaceTradersError.ShipOperationError.MissingGasSiphons>(mapCode(4258)) }
    @Test fun code4259_mapsToSiphonInvalidWaypoint() { assertIs<SpaceTradersError.ShipOperationError.SiphonInvalidWaypoint>(mapCode(4259)) }
    @Test fun code4260_mapsToSiphonPermission() { assertIs<SpaceTradersError.ShipOperationError.SiphonPermission>(mapCode(4260)) }
    @Test fun code4261_mapsToWaypointNoYield() { assertIs<SpaceTradersError.ShipOperationError.WaypointNoYield>(mapCode(4261)) }
    @Test fun code4262_mapsToJumpDestinationUnderConstruction() { assertIs<SpaceTradersError.ShipOperationError.JumpDestinationUnderConstruction>(mapCode(4262)) }
    @Test fun code4263_mapsToScrapInvalidTrait() { assertIs<SpaceTradersError.ShipOperationError.ScrapInvalidTrait>(mapCode(4263)) }
    @Test fun code4264_mapsToRepairInvalidTrait() { assertIs<SpaceTradersError.ShipOperationError.RepairInvalidTrait>(mapCode(4264)) }
    @Test fun code4265_mapsToAgentInsufficientCredits() { assertIs<SpaceTradersError.ShipOperationError.AgentInsufficientCredits>(mapCode(4265)) }
    @Test fun code4266_mapsToModuleNoShipyard() { assertIs<SpaceTradersError.ShipOperationError.ModuleNoShipyard>(mapCode(4266)) }
    @Test fun code4267_mapsToModuleNotInstalled() { assertIs<SpaceTradersError.ShipOperationError.ModuleNotInstalled>(mapCode(4267)) }
    @Test fun code4268_mapsToModuleInsufficientCredits() { assertIs<SpaceTradersError.ShipOperationError.ModuleInsufficientCredits>(mapCode(4268)) }
    @Test fun code4269_mapsToCantSlowDownWhileInTransit() { assertIs<SpaceTradersError.ShipOperationError.CantSlowDownWhileInTransit>(mapCode(4269)) }
    @Test fun code4270_mapsToExtractInvalidSurveyLocation() { assertIs<SpaceTradersError.ShipOperationError.ExtractInvalidSurveyLocation>(mapCode(4270)) }
    @Test fun code4271_mapsToTransferDockedOrbitConflict() { assertIs<SpaceTradersError.ShipOperationError.TransferDockedOrbitConflict>(mapCode(4271)) }

    // -- Contract errors --

    @Test fun code4500_mapsToAcceptNotAuthorized() { assertIs<SpaceTradersError.ContractError.AcceptNotAuthorized>(mapCode(4500)) }
    @Test fun code4501_mapsToAcceptConflict() { assertIs<SpaceTradersError.ContractError.AcceptConflict>(mapCode(4501)) }
    @Test fun code4502_mapsToFulfillDelivery() { assertIs<SpaceTradersError.ContractError.FulfillDelivery>(mapCode(4502)) }
    @Test fun code4503_mapsToDeadline() { assertIs<SpaceTradersError.ContractError.Deadline>(mapCode(4503)) }
    @Test fun code4504_mapsToFulfilled() { assertIs<SpaceTradersError.ContractError.Fulfilled>(mapCode(4504)) }
    @Test fun code4505_mapsToNotAccepted() { assertIs<SpaceTradersError.ContractError.NotAccepted>(mapCode(4505)) }
    @Test fun code4506_mapsToNotAuthorized() { assertIs<SpaceTradersError.ContractError.NotAuthorized>(mapCode(4506)) }
    @Test fun code4508_mapsToShipDeliverTerms() { assertIs<SpaceTradersError.ContractError.ShipDeliverTerms>(mapCode(4508)) }
    @Test fun code4509_mapsToShipDeliverFulfilled() { assertIs<SpaceTradersError.ContractError.ShipDeliverFulfilled>(mapCode(4509)) }
    @Test fun code4510_mapsToShipDeliverInvalidLocation() { assertIs<SpaceTradersError.ContractError.ShipDeliverInvalidLocation>(mapCode(4510)) }
    @Test fun code4511_mapsToExistingContract() { assertIs<SpaceTradersError.ContractError.ExistingContract>(mapCode(4511)) }

    // -- Market errors --

    @Test fun code4600_mapsToTradeInsufficientCredits() { assertIs<SpaceTradersError.MarketError.TradeInsufficientCredits>(mapCode(4600)) }
    @Test fun code4601_mapsToTradeNoPurchase() { assertIs<SpaceTradersError.MarketError.TradeNoPurchase>(mapCode(4601)) }
    @Test fun code4602_mapsToTradeNotSold() { assertIs<SpaceTradersError.MarketError.TradeNotSold>(mapCode(4602)) }
    @Test fun code4604_mapsToTradeUnitLimit() { assertIs<SpaceTradersError.MarketError.TradeUnitLimit>(mapCode(4604)) }
    @Test fun code4605_mapsToShipNotAvailableForPurchase() { assertIs<SpaceTradersError.MarketError.ShipNotAvailableForPurchase>(mapCode(4605)) }

    // -- Waypoint faction --

    @Test fun code4700_mapsToWaypointNoFaction() { assertIs<SpaceTradersError.WaypointNoFaction>(mapCode(4700)) }

    // -- Construction errors --

    @Test fun code4800_mapsToMaterialNotRequired() { assertIs<SpaceTradersError.ConstructionError.MaterialNotRequired>(mapCode(4800)) }
    @Test fun code4801_mapsToMaterialFulfilled() { assertIs<SpaceTradersError.ConstructionError.MaterialFulfilled>(mapCode(4801)) }
    @Test fun code4802_mapsToShipInvalidLocation() { assertIs<SpaceTradersError.ConstructionError.ShipInvalidLocation>(mapCode(4802)) }

    // -- Media type --

    @Test fun code5000_mapsToUnsupportedMediaType() { assertIs<SpaceTradersError.UnsupportedMediaType>(mapCode(5000)) }

    // -- Unknown fallback --

    @Test fun unknownCode_mapsToUnknown() {
        val result = mapCode(9999)
        assertIs<SpaceTradersError.Unknown>(result)
        assertEquals(9999, result.code)
        assertEquals("test message", result.message)
    }

    // -- Property propagation --

    @Test fun mapper_preservesMessageAndCode() {
        val dto = ErrorBodyDto(code = 4100, message = "Token is empty.")
        val result = dto.toDomain()
        assertEquals(4100, result.code)
        assertEquals("Token is empty.", result.message)
    }

    @Test fun mapper_preservesData() {
        val data = kotlinx.serialization.json.buildJsonObject {
            put("remainingSeconds", kotlinx.serialization.json.JsonPrimitive(30))
        }
        val dto = ErrorBodyDto(code = 4000, message = "Cooldown active.", data = data)
        val result = dto.toDomain()
        assertIs<SpaceTradersError.CooldownConflict>(result)
        assertEquals(data, result.data)
    }
}
