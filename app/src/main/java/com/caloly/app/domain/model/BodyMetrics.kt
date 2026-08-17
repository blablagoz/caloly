package com.caloly.app.domain.model

import kotlin.math.round

fun calculateBmi(heightCm: Int?, weightKg: Double?): Double? {
    if (heightCm == null || weightKg == null || heightCm <= 0 || weightKg <= 0) return null
    val heightMeters = heightCm / 100.0
    return round((weightKg / (heightMeters * heightMeters)) * 10.0) / 10.0
}
