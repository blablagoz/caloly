package com.caloly.app.domain.model

import kotlin.math.round
import kotlin.math.roundToInt
import java.time.LocalDate
import java.time.Period

fun calculateBmi(heightCm: Int?, weightKg: Double?): Double? {
    if (heightCm == null || weightKg == null || heightCm <= 0 || weightKg <= 0) return null
    val heightMeters = heightCm / 100.0
    return round((weightKg / (heightMeters * heightMeters)) * 10.0) / 10.0
}

data class BasalMetabolismEstimate(val minimumKcal: Int, val maximumKcal: Int = minimumKcal) {
    val displayValue: String get() = if (minimumKcal == maximumKcal) "$minimumKcal kcal" else "$minimumKcal–$maximumKcal kcal"
    val isRange: Boolean get() = minimumKcal != maximumKcal
}

fun calculateBasalMetabolism(
    birthDate: String?,
    heightCm: Int?,
    weightKg: Double?,
    gender: String?,
    today: LocalDate = LocalDate.now(),
): BasalMetabolismEstimate? {
    val birth = birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    if (birth.isAfter(today) || heightCm == null || heightCm !in 100..250 || weightKg == null || weightKg !in 30.0..350.0) return null
    val age = Period.between(birth, today).years
    if (age !in 13..120) return null
    val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age
    val female = (base - 161.0).roundToInt()
    val male = (base + 5.0).roundToInt()
    return when (gender) {
        "FEMALE" -> BasalMetabolismEstimate(female)
        "MALE" -> BasalMetabolismEstimate(male)
        else -> BasalMetabolismEstimate(minOf(female, male), maxOf(female, male))
    }
}
