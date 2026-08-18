package com.caloly.app.domain.repository

import com.caloly.app.domain.model.DailySummary
import com.caloly.app.domain.model.Food
import com.caloly.app.domain.model.FoodSearchPage
import com.caloly.app.domain.model.FoodUnit
import com.caloly.app.domain.model.MealType
import com.caloly.app.domain.model.NutritionTemplate
import com.caloly.app.domain.model.FoodLog
import com.caloly.app.domain.model.TemplateKind
import kotlinx.coroutines.flow.Flow

interface NutritionRepository {
    val localCatalogSize: Int
    val favoriteFoodIds: Set<String>
    fun observeDailySummary(dateKey: String): Flow<DailySummary>
    fun searchLocalFoods(query: String): List<Food>
    suspend fun searchRemoteFoods(query: String, page: Int = 1): Result<FoodSearchPage>
    suspend fun findFoodByBarcode(barcode: String): Result<Food?>
    fun saveCustomFood(food: Food)
    fun toggleFavorite(food: Food): Boolean
    suspend fun addFood(
        dateKey: String,
        mealType: MealType,
        food: Food,
        amount: Double,
        unit: FoodUnit,
    )
    suspend fun deleteFoodLog(id: String)
    suspend fun deleteFoodLogs(ids: List<String>)
    suspend fun restoreFoodLogs(dateKey: String, logs: List<FoodLog>)
    suspend fun updateFoodLog(log: FoodLog, mealType: MealType, amount: Double)
    fun observeLoggedDates(): Flow<Set<String>>
    fun observeTemplates(): Flow<List<NutritionTemplate>>
    suspend fun saveTemplate(name: String, kind: TemplateKind, logs: List<FoodLog>, sourceOwnerName: String? = null): NutritionTemplate
    suspend fun saveImportedTemplate(template: NutritionTemplate): NutritionTemplate
    suspend fun applyTemplate(templateId: String, dateKey: String)
    suspend fun deleteTemplate(id: String)
}
