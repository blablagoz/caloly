package com.caloly.app.data.ai

import com.caloly.app.domain.model.FoodSource
import com.caloly.app.domain.model.FoodUnit
import com.caloly.app.domain.model.NutritionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiMealResponseTest {
    @Test
    fun response_maps_multiple_foods_and_photo_quota() {
        val response = AiMealResponse(
            confirmationQuestion = "Önündeki yemek pilav ve yoğurt mu?",
            foods = listOf(
                DetectedFoodResponse(
                    name = "Pirinç pilavı", estimatedGrams = 180.0, gramsMin = 160.0, gramsMax = 210.0,
                    calories = 300.0, caloriesMin = 270.0, caloriesMax = 340.0,
                    proteinGrams = 5.0, carbsGrams = 58.0, fatGrams = 6.0, confidence = .86,
                ),
                DetectedFoodResponse(
                    name = "Yoğurt", estimatedGrams = 150.0, calories = 92.0,
                    proteinGrams = 5.2, carbsGrams = 7.0, fatGrams = 4.5, confidence = .92,
                ),
            ),
            overallConfidence = .89,
            remainingPhotoScans = 2,
        )

        val result = response.toDomain()

        assertEquals(2, result.foods.size)
        assertEquals(2, result.remainingPhotoScans)
        assertEquals("160–210 g", result.foods.first().displayGrams)
        assertEquals(NutritionSource.AI_ESTIMATE, result.foods.first().nutritionSource)
    }

    @Test
    fun detected_food_becomes_one_editable_serving() {
        val detected = DetectedFoodResponse(
            name = "Kuru fasulye", estimatedGrams = 250.0, calories = 350.0,
            proteinGrams = 18.0, carbsGrams = 48.0, fatGrams = 9.0, confidence = .8,
        ).toDomain()!!

        val food = detected.toFood("ai:test")

        assertEquals(FoodUnit.SERVING, food.defaultUnit)
        assertEquals(FoodSource.AI_ESTIMATE, food.source)
        assertEquals(250.0, food.gramsPerUnit, .001)
        assertEquals(140.0, food.caloriesPer100g, .001)
    }

    @Test
    fun invalid_food_is_discarded_without_crash() {
        val result = DetectedFoodResponse(name = "", estimatedGrams = 100.0, calories = 20.0).toDomain()
        assertTrue(result == null)
    }
}
