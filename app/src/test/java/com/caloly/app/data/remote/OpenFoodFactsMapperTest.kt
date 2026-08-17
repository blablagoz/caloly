package com.caloly.app.data.remote

import com.caloly.app.domain.model.FoodSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFoodFactsMapperTest {

    @Test
    fun `maps a complete packaged product`() {
        val food = OffProduct(
            code = "3017624010701",
            productName = "Nutella",
            brands = "Ferrero",
            quantity = "400 g",
            nutriments = OffNutriments(
                calories100g = 539.0,
                protein100g = 6.3,
                carbs100g = 57.5,
                fat100g = 30.9,
            ),
        ).toDomainOrNull()

        requireNotNull(food)
        assertEquals("Nutella", food.name)
        assertEquals(400.0, food.gramsPerUnit, 0.0)
        assertEquals(539.0, food.caloriesPer100g, 0.0)
        assertEquals(FoodSource.OPEN_FOOD_FACTS, food.source)
    }

    @Test
    fun `accepts water when energy data is absent`() {
        val food = OffProduct(
            code = "8691234567890",
            productNameTr = "Damla Su",
            nutriments = OffNutriments(),
        ).toDomainOrNull()

        requireNotNull(food)
        assertEquals(0.0, food.caloriesPer100g, 0.0)
        assertTrue(food.name.contains("Su"))
    }

    @Test
    fun `rejects non-water products without energy data`() {
        val food = OffProduct(
            productNameTr = "Bilinmeyen Çikolata",
            nutriments = OffNutriments(),
        ).toDomainOrNull()

        assertNull(food)
    }
}
