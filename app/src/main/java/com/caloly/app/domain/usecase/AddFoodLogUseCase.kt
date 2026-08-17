package com.caloly.app.domain.usecase

import com.caloly.app.domain.model.Food
import com.caloly.app.domain.model.FoodUnit
import com.caloly.app.domain.model.MealType
import com.caloly.app.domain.repository.NutritionRepository
import javax.inject.Inject

class AddFoodLogUseCase @Inject constructor(
    private val repository: NutritionRepository,
) {
    suspend operator fun invoke(dateKey: String, mealType: MealType, food: Food, amount: Double, unit: FoodUnit) =
        repository.addFood(dateKey, mealType, food, amount, unit)
}
