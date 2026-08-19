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
    val searchAliases: List<String> = emptyList(),
)

data class FoodSearchPage(
    val items: List<Food>,
    val currentPage: Int,
    val totalPages: Int,
    val totalResults: Int,
)

enum class FoodSource(val label: String) {
    CALOLY("Caloly tahmini"),
    CAFE_MENU("Kafe menüsü · tahmini"),
    OPEN_FOOD_FACTS("Open Food Facts"),
    OPEN_NUTRITION("Açık besin verisi (ODbL)"),
    USER("Benim ürünüm"),
    AI_ESTIMATE("Yapay zekâ tahmini"),
}

enum class FoodUnit(val label: String) {
    GRAM("g"),
    MILLILITER("ml"),
    SERVING("porsiyon"),
    PIECE("adet"),
    SLICE("dilim"),
    PACKAGE("paket"),
}
