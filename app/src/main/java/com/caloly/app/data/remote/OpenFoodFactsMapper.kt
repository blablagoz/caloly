package com.caloly.app.data.remote

import com.caloly.app.domain.model.Food
import com.caloly.app.domain.model.FoodSource
import com.caloly.app.domain.model.FoodUnit

fun OffProduct.toDomainOrNull(preferredLanguage: String = "tr"): Food? {
    val localizedName = when (preferredLanguage) {
        "tr" -> productNameTr
        "en" -> productNameEn
        "fr" -> productNameFr
        "de" -> productNameDe
        else -> null
    }
    val displayName = localizedName?.takeIf { it.isNotBlank() }
        ?: productNameTr?.takeIf { preferredLanguage == "tr" && it.isNotBlank() }
        ?: productName?.takeIf { it.isNotBlank() }
        ?: productNameEn?.takeIf { it.isNotBlank() }
        ?: productNameTr?.takeIf { it.isNotBlank() }
        ?: return null
    val nutrients = nutriments ?: return null
    val calories = nutrients.calories100g
        ?: if (displayName.lowercase().let { it.contains(" su") || it.startsWith("su ") || it == "su" || it.contains("water") }) 0.0 else return null
    if (calories !in 0.0..1000.0) return null

    val protein = nutrients.protein100g ?: 0.0
    val carbs = nutrients.carbs100g ?: 0.0
    val fat = nutrients.fat100g ?: 0.0
    if (listOf(protein, carbs, fat).any { it !in 0.0..100.0 } || protein + carbs + fat > 105.0) return null

    val packageGrams = parseGrams(quantity) ?: parseGrams(servingSize)
    return Food(
        id = "off:${code ?: displayName.hashCode()}",
        name = displayName.trim(),
        brand = brands?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() },
        caloriesPer100g = calories,
        proteinPer100g = protein,
        carbsPer100g = carbs,
        fatPer100g = fat,
        defaultUnit = if (packageGrams != null) FoodUnit.PACKAGE else FoodUnit.GRAM,
        gramsPerUnit = packageGrams ?: 1.0,
        barcode = code,
        imageUrl = imageUrl,
        source = FoodSource.OPEN_FOOD_FACTS,
    )
}

private fun parseGrams(text: String?): Double? {
    if (text.isNullOrBlank()) return null
    val normalized = text.lowercase().replace(',', '.')
    val match = Regex("(\\d+(?:\\.\\d+)?)\\s*(kg|g|ml|cl|l)\\b").find(normalized) ?: return null
    val amount = match.groupValues[1].toDoubleOrNull() ?: return null
    return when (match.groupValues[2]) {
        "kg" -> amount * 1000.0
        "g", "ml" -> amount
        "cl" -> amount * 10.0
        "l" -> amount * 1000.0
        else -> null
    }
}
