package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import kotlin.math.pow
import kotlin.math.sqrt

fun euclideanDistance(x1: Int, y1: Int, x2: Int, y2: Int): Double =
    sqrt((x2.toDouble() - x1).pow(2) + (y2.toDouble() - y1).pow(2))
