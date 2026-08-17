package com.caloly.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodyMetricsTest {
    @Test
    fun `calculates bmi rounded to one decimal`() {
        assertEquals(22.9, calculateBmi(heightCm = 175, weightKg = 70.0)!!, 0.0)
    }

    @Test
    fun `returns null when body information is incomplete or invalid`() {
        assertNull(calculateBmi(null, 70.0))
        assertNull(calculateBmi(175, null))
        assertNull(calculateBmi(0, 70.0))
        assertNull(calculateBmi(175, 0.0))
    }
}
