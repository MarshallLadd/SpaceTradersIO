package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

class SpaceTradersApiException(
    val error: SpaceTradersError,
    val httpStatus: Int
) : Exception(error.message)
