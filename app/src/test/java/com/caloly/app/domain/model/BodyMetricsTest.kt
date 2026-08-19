package com.caloly.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

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

    @Test
    fun `estimates basal metabolism with Mifflin St Jeor inputs`() {
        val estimate = calculateBasalMetabolism(
            birthDate = "1996-08-18",
            heightCm = 170,
            weightKg = 65.0,
            gender = "FEMALE",
            today = LocalDate.of(2026, 8, 18),
        )
        assertEquals(1402, estimate?.minimumKcal)
        assertEquals(1402, estimate?.maximumKcal)
    }

    @Test
    fun `returns an honest range when gender is undisclosed`() {
        val estimate = calculateBasalMetabolism(
            birthDate = "1996-08-18",
            heightCm = 170,
            weightKg = 65.0,
            gender = "UNDISCLOSED",
            today = LocalDate.of(2026, 8, 18),
        )
        assertEquals(1402, estimate?.minimumKcal)
        assertEquals(1568, estimate?.maximumKcal)
    }
}
