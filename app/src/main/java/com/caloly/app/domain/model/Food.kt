package com.caloly.app.domain.model

data class Food(
    val id: String,
    val name: String,
    val brand: String? = null,
    val caloriesPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val defaultUnit: FoodUnit = FoodUnit.GRAM,
    val gramsPerUnit: Double = 1.0,
    val barcode: String? = null,
    val imageUrl: String? = null,
    val source: FoodSource = FoodSource.CALOLY,
)

enum class FoodSource(val label: String) {
    CALOLY("Caloly"),
    OPEN_FOOD_FACTS("Open Food Facts"),
}

enum class FoodUnit(val label: String) {
    GRAM("g"),
    MILLILITER("ml"),
    PIECE("adet"),
    SLICE("dilim"),
    PACKAGE("paket"),
}
