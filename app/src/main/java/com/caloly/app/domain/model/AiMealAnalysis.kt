package com.caloly.app.domain.model

import kotlin.math.roundToInt

data class AiMealAnalysis(
    val confirmationQuestion: String,
    val foods: List<DetectedFood>,
    val overallConfidence: Double,
    val analysisNotes: String? = null,
    val remainingPhotoScans: Int? = null,
)

data class DetectedFood(
    val name: String,
    val brand: String? = null,
    val estimatedGrams: Double,
    val gramsMin: Double = estimatedGrams,
    val gramsMax: Double = estimatedGrams,
    val calories: Double,
    val caloriesMin: Double = calories,
    val caloriesMax: Double = calories,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val confidence: Double,
    val nutritionSource: NutritionSource = NutritionSource.AI_ESTIMATE,
) {
    fun toFood(id: String): Food {
        val grams = estimatedGrams.coerceAtLeast(1.0)
        val per100 = 100.0 / grams
        return Food(
            id = id,
            name = name.trim(),
            brand = brand?.trim()?.takeIf(String::isNotBlank),
            caloriesPer100g = calories.coerceAtLeast(0.0) * per100,
            proteinPer100g = proteinGrams.coerceAtLeast(0.0) * per100,
            carbsPer100g = carbsGrams.coerceAtLeast(0.0) * per100,
            fatPer100g = fatGrams.coerceAtLeast(0.0) * per100,
            defaultUnit = FoodUnit.SERVING,
            gramsPerUnit = grams,
            source = FoodSource.AI_ESTIMATE,
        )
    }

    val displayGrams: String
        get() = if ((gramsMax - gramsMin) >= 10) {
            "${gramsMin.roundToInt()}–${gramsMax.roundToInt()} g"
        } else {
            "~${estimatedGrams.roundToInt()} g"
        }

    val displayCalories: String
        get() = if ((caloriesMax - caloriesMin) >= 20) {
            "${caloriesMin.roundToInt()}–${caloriesMax.roundToInt()} kcal"
        } else {
            "~${calories.roundToInt()} kcal"
        }
}

enum class NutritionSource {
    VERIFIED_DATABASE,
    AI_ESTIMATE,
    USER_EDITED,
}
