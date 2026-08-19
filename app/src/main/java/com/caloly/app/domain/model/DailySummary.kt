package com.caloly.app.domain.model

data class DailySummary(
    val consumedCalories: Int = 0,
    val calorieGoal: Int = 0,
    val proteinGrams: Int = 0,
    val proteinGoal: Int = 0,
    val carbsGrams: Int = 0,
    val carbsGoal: Int = 0,
    val fatGrams: Int = 0,
    val fatGoal: Int = 0,
    val steps: Int = 0,
    val activeCalories: Int = 0,
    val totalCaloriesBurned: Int = 0,
    val logs: List<FoodLog> = emptyList(),
)
