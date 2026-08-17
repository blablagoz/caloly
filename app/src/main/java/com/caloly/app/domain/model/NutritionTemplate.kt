package com.caloly.app.domain.model

enum class TemplateKind { MEAL, DAY }

data class NutritionTemplateItem(
    val mealType: MealType,
    val foodName: String,
    val brand: String? = null,
    val amount: Double,
    val unit: FoodUnit,
    val grams: Double,
    val calories: Int,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
)

data class NutritionTemplate(
    val id: String,
    val name: String,
    val kind: TemplateKind,
    val items: List<NutritionTemplateItem>,
    val sourceOwnerName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val totalCalories: Int get() = items.sumOf { it.calories }
}
