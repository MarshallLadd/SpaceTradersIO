package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class DistanceTest {

    @Test
    fun samePoint_returnsZero() {
        assertEquals(0.0, euclideanDistance(5, 5, 5, 5))
    }

    @Test
    fun knownTriangle_3_4_5() {
        assertEquals(5.0, euclideanDistance(0, 0, 3, 4))
    }

    @Test
    fun horizontalDistance() {
        assertEquals(10.0, euclideanDistance(0, 0, 10, 0))
    }

    @Test
    fun verticalDistance() {
        assertEquals(7.0, euclideanDistance(0, 0, 0, 7))
    }

    @Test
    fun negativeCoordinates() {
        assertEquals(5.0, euclideanDistance(-3, -4, 0, 0))
    }

    @Test
    fun symmetricDistance() {
        val d1 = euclideanDistance(1, 2, 4, 6)
        val d2 = euclideanDistance(4, 6, 1, 2)
        assertEquals(d1, d2)
    }
}
