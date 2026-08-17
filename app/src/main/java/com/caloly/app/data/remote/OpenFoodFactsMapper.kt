package com.caloly.app.data.remote

import com.caloly.app.domain.model.Food
import com.caloly.app.domain.model.FoodSource
import com.caloly.app.domain.model.FoodUnit

fun OffProduct.toDomainOrNull(): Food? {
    val displayName = productNameTr?.takeIf { it.isNotBlank() }
        ?: productName?.takeIf { it.isNotBlank() }
        ?: return null
    val nutrients = nutriments ?: return null
    val calories = nutrients.calories100g
        ?: if (displayName.lowercase().let { it.contains(" su") || it.startsWith("su ") || it == "su" || it.contains("water") }) 0.0 else return null
    if (calories < 0) return null

    val packageGrams = parseGrams(quantity) ?: parseGrams(servingSize)
    return Food(
        id = "off:${code ?: displayName.hashCode()}",
        name = displayName.trim(),
        brand = brands?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() },
        caloriesPer100g = calories,
        proteinPer100g = nutrients.protein100g ?: 0.0,
        carbsPer100g = nutrients.carbs100g ?: 0.0,
        fatPer100g = nutrients.fat100g ?: 0.0,
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
