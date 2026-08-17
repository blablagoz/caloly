package com.caloly.app.domain.model

data class HealthSummary(
    val steps: Long = 0,
    val activeCalories: Int = 0,
    val totalCaloriesBurned: Int = 0,
)

enum class HealthConnectAvailability {
    AVAILABLE,
    UPDATE_REQUIRED,
    UNAVAILABLE,
}
