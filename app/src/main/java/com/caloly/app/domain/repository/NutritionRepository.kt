package com.caloly.app.domain.repository

import com.caloly.app.domain.model.DailySummary
import com.caloly.app.domain.model.Food
import com.caloly.app.domain.model.FoodUnit
import com.caloly.app.domain.model.MealType
import kotlinx.coroutines.flow.Flow

interface NutritionRepository {
    fun observeDailySummary(dateKey: String): Flow<DailySummary>
    fun searchLocalFoods(query: String): List<Food>
    suspend fun searchRemoteFoods(query: String): Result<List<Food>>
    suspend fun findFoodByBarcode(barcode: String): Result<Food?>
    suspend fun addFood(
        dateKey: String,
        mealType: MealType,
        food: Food,
        amount: Double,
        unit: FoodUnit,
    )
    suspend fun deleteFoodLog(id: String)
}
