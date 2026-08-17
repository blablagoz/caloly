package com.caloly.app.domain.model

data class FoodLog(
    val id: String,
    val mealType: MealType,
    val foodName: String,
    val brand: String?,
    val amount: Double,
    val unit: FoodUnit,
    val grams: Double,
    val calories: Int,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val createdAt: Long,
)
