package com.caloly.app.data.repository

import com.caloly.app.data.local.FoodLogDao
import com.caloly.app.data.local.FoodLogEntity
import com.caloly.app.data.local.NutritionTemplateEntity
import com.caloly.app.data.local.NutritionTemplateItemEntity
import com.caloly.app.data.local.NutritionTemplateWithItems
import com.caloly.app.data.remote.OpenFoodFactsApi
import com.caloly.app.data.remote.toDomainOrNull
import com.caloly.app.domain.model.DailySummary
import com.caloly.app.domain.model.Food
import com.caloly.app.domain.model.FoodLog
import com.caloly.app.domain.model.FoodUnit
import com.caloly.app.domain.model.MealType
import com.caloly.app.domain.model.NutritionTemplate
import com.caloly.app.domain.model.NutritionTemplateItem
import com.caloly.app.domain.model.TemplateKind
import com.caloly.app.domain.repository.NutritionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt
import java.text.Normalizer
import java.util.Locale

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
        val normalized = query.searchKey()
        return foodCatalog.filter { food ->
            food.name.searchKey().contains(normalized) ||
                food.brand?.searchKey()?.contains(normalized) == true
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

    override fun observeLoggedDates(): Flow<Set<String>> = dao.observeLoggedDates().map { it.toSet() }

    override fun observeTemplates(): Flow<List<NutritionTemplate>> =
        dao.observeTemplates().map { templates -> templates.map { it.toDomain() } }

    override suspend fun saveTemplate(
        name: String,
        kind: TemplateKind,
        logs: List<FoodLog>,
        sourceOwnerName: String?,
    ): NutritionTemplate {
        require(name.isNotBlank()) { "Şablona bir ad ver." }
        require(logs.isNotEmpty()) { "Kaydedilecek bir besin yok." }
        val template = NutritionTemplate(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            kind = kind,
            sourceOwnerName = sourceOwnerName,
            items = logs.map { it.toTemplateItem() },
        )
        persistTemplate(template)
        return template
    }

    override suspend fun saveImportedTemplate(template: NutritionTemplate): NutritionTemplate {
        require(template.items.isNotEmpty()) { "Örnek beslenme kaydı boş." }
        val imported = template.copy(id = UUID.randomUUID().toString(), createdAt = System.currentTimeMillis())
        persistTemplate(imported)
        return imported
    }

    override suspend fun applyTemplate(templateId: String, dateKey: String) {
        val template = dao.templateById(templateId)?.toDomain() ?: error("Kayıtlı öğün bulunamadı.")
        val now = System.currentTimeMillis()
        dao.insertLogs(template.items.mapIndexed { index, item ->
            FoodLogEntity(
                id = UUID.randomUUID().toString(),
                dateKey = dateKey,
                mealType = item.mealType.name,
                foodName = item.foodName,
                brand = item.brand,
                amount = item.amount,
                unit = item.unit.name,
                grams = item.grams,
                calories = item.calories,
                proteinGrams = item.proteinGrams,
                carbsGrams = item.carbsGrams,
                fatGrams = item.fatGrams,
                createdAt = now + index,
            )
        })
    }

    override suspend fun deleteTemplate(id: String) = dao.deleteTemplate(id)

    private suspend fun persistTemplate(template: NutritionTemplate) {
        dao.replaceTemplate(
            NutritionTemplateEntity(template.id, template.name, template.kind.name, template.sourceOwnerName, template.createdAt),
            template.items.map { item ->
                NutritionTemplateItemEntity(
                    id = UUID.randomUUID().toString(),
                    templateId = template.id,
                    mealType = item.mealType.name,
                    foodName = item.foodName,
                    brand = item.brand,
                    amount = item.amount,
                    unit = item.unit.name,
                    grams = item.grams,
                    calories = item.calories,
                    proteinGrams = item.proteinGrams,
                    carbsGrams = item.carbsGrams,
                    fatGrams = item.fatGrams,
                )
            },
        )
    }

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

    private fun FoodLog.toTemplateItem() = NutritionTemplateItem(
        mealType, foodName, brand, amount, unit, grams, calories,
        proteinGrams, carbsGrams, fatGrams,
    )

    private fun NutritionTemplateWithItems.toDomain() = NutritionTemplate(
        id = template.id,
        name = template.name,
        kind = TemplateKind.valueOf(template.kind),
        sourceOwnerName = template.sourceOwnerName,
        createdAt = template.createdAt,
        items = items.map { item ->
            NutritionTemplateItem(
                mealType = MealType.valueOf(item.mealType),
                foodName = item.foodName,
                brand = item.brand,
                amount = item.amount,
                unit = FoodUnit.valueOf(item.unit),
                grams = item.grams,
                calories = item.calories,
                proteinGrams = item.proteinGrams,
                carbsGrams = item.carbsGrams,
                fatGrams = item.fatGrams,
            )
        },
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
            Food("water", "Su", caloriesPer100g = 0.0, proteinPer100g = 0.0, carbsPer100g = 0.0, fatPer100g = 0.0, defaultUnit = FoodUnit.MILLILITER),
            Food("ayran", "Ayran", caloriesPer100g = 37.0, proteinPer100g = 2.0, carbsPer100g = 2.8, fatPer100g = 2.0, defaultUnit = FoodUnit.MILLILITER),
            Food("kefir", "Kefir", caloriesPer100g = 55.0, proteinPer100g = 3.4, carbsPer100g = 4.5, fatPer100g = 2.5, defaultUnit = FoodUnit.MILLILITER),
            Food("cheese-white", "Beyaz peynir", caloriesPer100g = 260.0, proteinPer100g = 17.0, carbsPer100g = 3.0, fatPer100g = 20.0),
            Food("cheese-kasar", "Kaşar peyniri", caloriesPer100g = 404.0, proteinPer100g = 25.0, carbsPer100g = 1.3, fatPer100g = 33.0),
            Food("olive", "Zeytin", caloriesPer100g = 116.0, proteinPer100g = 0.8, carbsPer100g = 6.0, fatPer100g = 10.9, defaultUnit = FoodUnit.PIECE, gramsPerUnit = 4.0),
            Food("olive-oil", "Zeytinyağı", caloriesPer100g = 884.0, proteinPer100g = 0.0, carbsPer100g = 0.0, fatPer100g = 100.0),
            Food("butter", "Tereyağı", caloriesPer100g = 717.0, proteinPer100g = 0.9, carbsPer100g = 0.1, fatPer100g = 81.0),
            Food("bulgur", "Bulgur pilavı", caloriesPer100g = 114.0, proteinPer100g = 3.1, carbsPer100g = 18.6, fatPer100g = 3.2),
            Food("pasta", "Makarna", caloriesPer100g = 158.0, proteinPer100g = 5.8, carbsPer100g = 30.9, fatPer100g = 0.9),
            Food("red-meat", "Dana eti", caloriesPer100g = 250.0, proteinPer100g = 26.0, carbsPer100g = 0.0, fatPer100g = 15.0),
            Food("salmon", "Somon", caloriesPer100g = 208.0, proteinPer100g = 20.0, carbsPer100g = 0.0, fatPer100g = 13.0),
            Food("lentil-soup", "Mercimek çorbası", caloriesPer100g = 72.0, proteinPer100g = 3.7, carbsPer100g = 11.0, fatPer100g = 1.7),
            Food("tarhana-soup", "Tarhana çorbası", caloriesPer100g = 61.0, proteinPer100g = 2.0, carbsPer100g = 9.0, fatPer100g = 2.0),
            Food("beans", "Kuru fasulye", caloriesPer100g = 142.0, proteinPer100g = 8.7, carbsPer100g = 21.0, fatPer100g = 2.5),
            Food("chickpeas", "Nohut yemeği", caloriesPer100g = 164.0, proteinPer100g = 8.9, carbsPer100g = 27.0, fatPer100g = 2.6),
            Food("lahmacun", "Lahmacun", caloriesPer100g = 220.0, proteinPer100g = 9.0, carbsPer100g = 27.0, fatPer100g = 8.5, defaultUnit = FoodUnit.PIECE, gramsPerUnit = 150.0),
            Food("pide", "Kıymalı pide", caloriesPer100g = 245.0, proteinPer100g = 10.0, carbsPer100g = 30.0, fatPer100g = 9.0, defaultUnit = FoodUnit.PIECE, gramsPerUnit = 250.0),
            Food("simit", "Simit", caloriesPer100g = 333.0, proteinPer100g = 10.0, carbsPer100g = 58.0, fatPer100g = 7.0, defaultUnit = FoodUnit.PIECE, gramsPerUnit = 100.0),
            Food("borek", "Peynirli börek", caloriesPer100g = 280.0, proteinPer100g = 8.0, carbsPer100g = 30.0, fatPer100g = 14.0),
            Food("tomato", "Domates", caloriesPer100g = 18.0, proteinPer100g = 0.9, carbsPer100g = 3.9, fatPer100g = 0.2, defaultUnit = FoodUnit.PIECE, gramsPerUnit = 120.0),
            Food("cucumber", "Salatalık", caloriesPer100g = 15.0, proteinPer100g = 0.7, carbsPer100g = 3.6, fatPer100g = 0.1, defaultUnit = FoodUnit.PIECE, gramsPerUnit = 150.0),
            Food("potato", "Patates", caloriesPer100g = 77.0, proteinPer100g = 2.0, carbsPer100g = 17.0, fatPer100g = 0.1, defaultUnit = FoodUnit.PIECE, gramsPerUnit = 170.0),
            Food("orange", "Portakal", caloriesPer100g = 47.0, proteinPer100g = 0.9, carbsPer100g = 12.0, fatPer100g = 0.1, defaultUnit = FoodUnit.PIECE, gramsPerUnit = 180.0),
            Food("strawberry", "Çilek", caloriesPer100g = 32.0, proteinPer100g = 0.7, carbsPer100g = 7.7, fatPer100g = 0.3),
            Food("almond", "Badem", caloriesPer100g = 579.0, proteinPer100g = 21.0, carbsPer100g = 22.0, fatPer100g = 50.0),
            Food("walnut", "Ceviz", caloriesPer100g = 654.0, proteinPer100g = 15.0, carbsPer100g = 14.0, fatPer100g = 65.0),
            Food("hazelnut", "Fındık", caloriesPer100g = 628.0, proteinPer100g = 15.0, carbsPer100g = 17.0, fatPer100g = 61.0),
            Food("honey", "Bal", caloriesPer100g = 304.0, proteinPer100g = 0.3, carbsPer100g = 82.0, fatPer100g = 0.0),
            Food("tahini-molasses", "Tahin pekmez", caloriesPer100g = 490.0, proteinPer100g = 10.0, carbsPer100g = 55.0, fatPer100g = 27.0),
        )
    }
}

private fun String.searchKey(): String = Normalizer.normalize(lowercase(Locale("tr", "TR")), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .replace('ı', 'i')
