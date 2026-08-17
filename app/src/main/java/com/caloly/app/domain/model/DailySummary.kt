package com.caloly.app.domain.model

data class DailySummary(
    val consumedCalories: Int = 0,
    val calorieGoal: Int = 2100,
    val proteinGrams: Int = 0,
    val proteinGoal: Int = 140,
    val carbsGrams: Int = 0,
    val carbsGoal: Int = 220,
    val fatGrams: Int = 0,
    val fatGoal: Int = 70,
    val steps: Int = 0,
    val activeCalories: Int = 0,
    val totalCaloriesBurned: Int = 0,
    val logs: List<FoodLog> = emptyList(),
)
