package com.caloly.app.data.repository

import com.caloly.app.data.local.FoodLogDao
import com.caloly.app.data.local.FoodLogEntity
import com.caloly.app.data.remote.OpenFoodFactsApi
import com.caloly.app.data.remote.toDomainOrNull
import com.caloly.app.domain.model.DailySummary
import com.caloly.app.domain.model.Food
import com.caloly.app.domain.model.FoodLog
import com.caloly.app.domain.model.FoodUnit
import com.caloly.app.domain.model.MealType
import com.caloly.app.domain.repository.NutritionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt

class NutritionRepositoryImpl @Inject constructor(
    private val dao: FoodLogDao,
    private val openFoodFactsApi: OpenFoodFactsApi,
) : NutritionRepository {

    override fun observeDailySummary(dateKey: String): Flow<DailySummary> =
        dao.observeByDate(dateKey).map { entities ->
            val logs = entities.map { it.toDomain() }
            DailySummary(
                consumedCalories = logs.sumOf { it.calories },
                proteinGrams = logs.sumOf { it.proteinGrams }.roundToInt(),
                carbsGrams = logs.sumOf { it.carbsGrams }.roundToInt(),
                fatGrams = logs.sumOf { it.fatGrams }.roundToInt(),
                steps = 0,
                activeCalories = 0,
                logs = logs,
            )
        }

    override fun searchLocalFoods(query: String): List<Food> {
        if (query.isBlank()) return foodCatalog.take(12)
        val normalized = query.trim().lowercase()
        return foodCatalog.filter { food ->
            food.name.lowercase().contains(normalized) ||
                food.brand?.lowercase()?.contains(normalized) == true
        }.take(25)
    }

    override suspend fun searchRemoteFoods(query: String): Result<List<Food>> = runCatching {
        require(query.trim().length >= 2) { "En az 2 karakter gir." }
        openFoodFactsApi.search(query.trim())
            .products
            .mapNotNull { it.toDomainOrNull() }
            .distinctBy { it.barcode ?: it.id }
            .take(20)
    }

    override suspend fun findFoodByBarcode(barcode: String): Result<Food?> = runCatching {
        val clean = barcode.filter(Char::isDigit)
        require(clean.length in 8..14) { "Geçersiz barkod." }
        openFoodFactsApi.productByBarcode(clean).product?.toDomainOrNull()
    }

    override suspend fun addFood(
        dateKey: String,
        mealType: MealType,
        food: Food,
        amount: Double,
        unit: FoodUnit,
    ) {
        val grams = when (unit) {
            FoodUnit.GRAM -> amount
            FoodUnit.MILLILITER -> amount
            else -> amount * food.gramsPerUnit
        }
        val factor = grams / 100.0
        dao.insert(
            FoodLogEntity(
                id = UUID.randomUUID().toString(),
                dateKey = dateKey,
                mealType = mealType.name,
                foodName = food.name,
                brand = food.brand,
                amount = amount,
                unit = unit.name,
                grams = grams,
                calories = (food.caloriesPer100g * factor).roundToInt(),
                proteinGrams = food.proteinPer100g * factor,
                carbsGrams = food.carbsPer100g * factor,
                fatGrams = food.fatPer100g * factor,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun deleteFoodLog(id: String) = dao.deleteById(id)

    private fun FoodLogEntity.toDomain() = FoodLog(
        id = id,
        mealType = MealType.valueOf(mealType),
        foodName = foodName,
        brand = brand,
        amount = amount,
        unit = FoodUnit.valueOf(unit),
        grams = grams,
        calories = calories,
        proteinGrams = proteinGrams,
        carbsGrams = carbsGrams,
        fatGrams = fatGrams,
        createdAt = createdAt,
    )

    companion object {
        private val foodCatalog = listOf(
            Food("egg", "Yumurta", caloriesPer100g = 155.0, proteinPer100g = 13.0, carbsPer100g = 1.1, fatPer100g = 11.0, defaultUnit = FoodUnit.PIECE, gramsPerUnit = 50.0),
            Food("rice", "Pirinç pilavı", caloriesPer100g = 130.0, proteinPer100g = 2.7, carbsPer100g = 28.0, fatPer100g = 0.3),
            Food("chicken", "Tavuk göğsü", caloriesPer100g = 165.0, proteinPer100g = 31.0, carbsPer100g = 0.0, fatPer100g = 3.6),
            Food("bread", "Beyaz ekmek", caloriesPer100g = 265.0, proteinPer100g = 9.0, carbsPer100g = 49.0, fatPer100g = 3.2, defaultUnit = FoodUnit.SLICE, gramsPerUnit = 25.0),
            Food("oats", "Yulaf ezmesi", caloriesPer100g = 389.0, proteinPer100g = 16.9, carbsPer100g = 66.3, fatPer100g = 6.9),
            Food("banana", "Muz", caloriesPer100g = 89.0, proteinPer100g = 1.1, carbsPer100g = 22.8, fatPer100g = 0.3, defaultUnit = FoodUnit.PIECE, gramsPerUnit = 120.0),
            Food("apple", "Elma", caloriesPer100g = 52.0, proteinPer100g = 0.3, carbsPer100g = 13.8, fatPer100g = 0.2, defaultUnit = FoodUnit.PIECE, gramsPerUnit = 180.0),
            Food("turkish-coffee", "Türk kahvesi", caloriesPer100g = 2.0, proteinPer100g = 0.1, carbsPer100g = 0.0, fatPer100g = 0.0, defaultUnit = FoodUnit.MILLILITER),
            Food("milk", "Süt", caloriesPer100g = 61.0, proteinPer100g = 3.2, carbsPer100g = 4.8, fatPer100g = 3.3, defaultUnit = FoodUnit.MILLILITER),
            Food("yogurt", "Yoğurt", caloriesPer100g = 61.0, proteinPer100g = 3.5, carbsPer100g = 4.7, fatPer100g = 3.3),
            Food("lentil", "Mercimek yemeği", caloriesPer100g = 116.0, proteinPer100g = 9.0, carbsPer100g = 20.0, fatPer100g = 0.4),
            Food("tuna", "Ton balığı", caloriesPer100g = 132.0, proteinPer100g = 29.0, carbsPer100g = 0.0, fatPer100g = 1.0),
            Food("ulker-chocolate-wafer", "Çikolatalı Gofret", "Ülker", 535.0, 6.0, 59.0, 30.0, FoodUnit.PACKAGE, 36.0),
            Food("eti-burcak", "Burçak Klasik", "ETİ", 455.0, 7.0, 68.0, 17.0, FoodUnit.PACKAGE, 131.0),
            Food("albeni", "Albeni", "Ülker", 470.0, 5.5, 66.0, 20.0, FoodUnit.PACKAGE, 40.0),
            Food("doritos-taco", "Doritos Taco", "Doritos", 500.0, 6.0, 52.0, 29.0, FoodUnit.PACKAGE, 107.0),
            Food("coke-zero", "Coca-Cola Zero Sugar", "Coca-Cola", 0.2, 0.0, 0.0, 0.0, FoodUnit.MILLILITER),
        )
    }
}
