package com.caloly.app.data.repository

import android.content.Context
import com.caloly.app.data.local.FoodLogDao
import com.caloly.app.data.local.FoodLogEntity
import com.caloly.app.data.local.NutritionTemplateEntity
import com.caloly.app.data.local.NutritionTemplateItemEntity
import com.caloly.app.data.local.NutritionTemplateWithItems
import com.caloly.app.data.remote.OpenFoodFactsApi
import com.caloly.app.data.remote.OffSearchResponse
import com.caloly.app.data.remote.toDomainOrNull
import com.caloly.app.domain.model.DailySummary
import com.caloly.app.domain.model.Food
import com.caloly.app.domain.model.FoodLog
import com.caloly.app.domain.model.FoodSearchPage
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
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext

class NutritionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val dao: FoodLogDao,
    private val openFoodFactsApi: OpenFoodFactsApi,
) : NutritionRepository {

    private data class SavedFoodRecord(
        val food: Food,
        val favorite: Boolean = false,
        val lastUsedAt: Long = 0,
    )

    private data class GenericFoodCatalog(val foods: List<GenericFoodDto> = emptyList())
    private data class GenericFoodDto(
        val id: String,
        val name: String,
        val aliases: List<String> = emptyList(),
        val calories: Double,
        val protein: Double = 0.0,
        val carbs: Double = 0.0,
        val fat: Double = 0.0,
    )
    private data class CafeMenuCatalog(val foods: List<CafeMenuFoodDto> = emptyList())
    private data class CafeMenuFoodDto(
        val id: String,
        val brand: String,
        val name: String,
        val aliases: List<String> = emptyList(),
        val calories: Double,
        val protein: Double = 0.0,
        val carbs: Double = 0.0,
        val fat: Double = 0.0,
    )

    private val savedFoodPreferences by lazy { context.getSharedPreferences("saved_foods", Context.MODE_PRIVATE) }
    private val savedFoods: MutableMap<String, SavedFoodRecord> by lazy {
        runCatching {
            gson.fromJson(savedFoodPreferences.getString("records", "[]"), Array<SavedFoodRecord>::class.java)
                .associateByTo(linkedMapOf()) { it.food.id }
        }.getOrDefault(linkedMapOf())
    }

    private val bundledTurkeyFoods: List<Food> by lazy {
        runCatching {
            context.assets.open("turkey_products_2024.json").bufferedReader().use { reader ->
                gson.fromJson(reader, OffSearchResponse::class.java).products
                    .mapNotNull { it.toDomainOrNull("tr") }
            }
        }.getOrDefault(emptyList())
    }

    private val turkishGenericFoods: List<Food> by lazy {
        runCatching {
            context.assets.open("turkish_generic_foods_2026.json").bufferedReader().use { reader ->
                gson.fromJson(reader, GenericFoodCatalog::class.java).foods.map { item ->
                    val produceWeight = produceUnitWeight(item.name)
                    Food(
                        id = item.id,
                        name = item.name,
                        caloriesPer100g = item.calories,
                        proteinPer100g = item.protein,
                        carbsPer100g = item.carbs,
                        fatPer100g = item.fat,
                        defaultUnit = if (produceWeight != null) FoodUnit.PIECE else FoodUnit.GRAM,
                        gramsPerUnit = produceWeight ?: 1.0,
                        source = com.caloly.app.domain.model.FoodSource.OPEN_NUTRITION,
                        searchAliases = item.aliases,
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private val localFoodsDelegate = lazy {
        (foodCatalog + bundledTurkeyFoods + turkishGenericFoods).distinctBy { it.barcode ?: it.id }
    }
    private val localFoods: List<Food> by localFoodsDelegate

    /**
     * Curated brand-menu matches are kept outside [localFoods]. They participate only when
     * the user explicitly presses “İnternette Ara”, so the normal offline search stays small
     * and generic. Values without an official nutrition declaration are labelled as estimates.
     */
    private val cafeMenuFoods: List<Food> by lazy {
        runCatching {
            context.assets.open("cafe_menu_tr.json").bufferedReader().use { reader ->
                gson.fromJson(reader, CafeMenuCatalog::class.java).foods.map { item ->
                    Food(
                        id = "cafe:${item.id}",
                        name = item.name,
                        brand = item.brand,
                        caloriesPer100g = item.calories,
                        proteinPer100g = item.protein,
                        carbsPer100g = item.carbs,
                        fatPer100g = item.fat,
                        defaultUnit = FoodUnit.SERVING,
                        gramsPerUnit = 100.0,
                        source = com.caloly.app.domain.model.FoodSource.CAFE_MENU,
                        searchAliases = item.aliases,
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private val knownBrandKeys: Set<String> by lazy {
        (foodCatalog + bundledTurkeyFoods + cafeMenuFoods)
            .mapNotNull { it.brand?.takeIf(String::isNotBlank)?.searchKey() }
            .toSet()
    }

    override val localCatalogSize: Int
        get() = (if (localFoodsDelegate.isInitialized()) localFoods.size else 0) +
            savedFoods.values.count { it.food.source == com.caloly.app.domain.model.FoodSource.USER }
    override val favoriteFoodIds: Set<String> get() = synchronized(savedFoods) { savedFoods.values.filter { it.favorite }.mapTo(mutableSetOf()) { it.food.id } }

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
        if (query.isBlank()) {
            val saved = synchronized(savedFoods) {
                savedFoods.values.sortedWith(compareByDescending<SavedFoodRecord> { it.favorite }.thenByDescending { it.lastUsedAt }).map { it.food }
            }
            return (saved + foodCatalog).distinctBy { it.id }.take(20)
        }
        val normalized = query.searchKey()
        val candidates = synchronized(savedFoods) { savedFoods.values.map { it.food } } + localFoods
        return candidates.distinctBy { it.barcode ?: it.id }.mapNotNull { food ->
            food.searchScore(normalized).takeIf { it > 0 }?.let { it to food }
        }.sortedWith(compareByDescending<Pair<Int, Food>> { it.first }.thenBy { it.second.name })
            .map { it.second }
            .take(40)
    }

    override suspend fun searchRemoteFoods(query: String, page: Int): Result<FoodSearchPage> = runCatching {
        require(query.trim().length >= 2) { "En az 2 karakter gir." }
        require(page > 0) { "Sayfa numarası geçersiz." }
        val locale = Locale.getDefault()
        val normalizedQuery = query.searchKey()
        val languageCode = locale.language.ifBlank { "tr" }
        val countryCode = locale.country.lowercase(Locale.ROOT).ifBlank { if (languageCode == "tr") "tr" else "world" }
        val isBrandQuery = normalizedQuery in knownBrandKeys
        val response = if (isBrandQuery) {
            openFoodFactsApi.searchByBrand(
                brand = query.trim(),
                page = page,
                languageCode = languageCode,
                countryCode = countryCode,
            )
        } else {
            openFoodFactsApi.search(
                query = query.trim(),
                page = page,
                languageCode = languageCode,
                countryCode = countryCode,
            )
        }
        val openFoods = response.products
            .mapNotNull { it.toDomainOrNull(locale.language) }
            .mapNotNull { food -> food.searchScore(normalizedQuery).takeIf { it > 0 }?.let { it to food } }
            .distinctBy { (_, food) -> food.barcode ?: "${food.name.searchKey()}:${food.brand?.searchKey()}" }
        val cafeFoods = if (page == 1) {
            cafeMenuFoods.mapNotNull { food ->
                food.searchScore(normalizedQuery).takeIf { it > 0 }?.let { it to food }
            }
        } else {
            emptyList()
        }
        val items = (cafeFoods + openFoods)
            .sortedWith(compareByDescending<Pair<Int, Food>> { it.first }.thenBy { it.second.name })
            .map { it.second }
            .distinctBy { it.barcode ?: "${it.brand?.searchKey()}:${it.name.searchKey()}" }
        val totalPages = response.totalPages()
        FoodSearchPage(
            items = items,
            currentPage = page.coerceAtMost(totalPages),
            totalPages = totalPages,
            totalResults = (response.count ?: 0) +
                cafeMenuFoods.count { it.searchScore(normalizedQuery) > 0 },
        )
    }

    override suspend fun findFoodByBarcode(barcode: String): Result<Food?> = runCatching {
        val clean = barcode.filter(Char::isDigit)
        require(clean.length in 8..14) { "Geçersiz barkod." }
        synchronized(savedFoods) { savedFoods.values.firstOrNull { it.food.barcode == clean }?.food }
            ?: openFoodFactsApi.productByBarcode(clean).product?.toDomainOrNull(Locale.getDefault().language)
    }

    override fun saveCustomFood(food: Food) {
        require(food.source == com.caloly.app.domain.model.FoodSource.USER)
        synchronized(savedFoods) {
            val previous = savedFoods[food.id]
            savedFoods[food.id] = SavedFoodRecord(food, previous?.favorite ?: false, System.currentTimeMillis())
            persistSavedFoods()
        }
    }

    override fun toggleFavorite(food: Food): Boolean = synchronized(savedFoods) {
        val previous = savedFoods[food.id]
        val favorite = previous?.favorite != true
        savedFoods[food.id] = SavedFoodRecord(food, favorite, previous?.lastUsedAt ?: 0)
        persistSavedFoods()
        favorite
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
        rememberFood(food)
    }

    override suspend fun deleteFoodLog(id: String) = dao.deleteById(id)

    override suspend fun deleteFoodLogs(ids: List<String>) {
        if (ids.isNotEmpty()) dao.deleteByIds(ids)
    }

    override suspend fun restoreFoodLogs(dateKey: String, logs: List<FoodLog>) {
        dao.insertLogs(logs.mapIndexed { index, log -> log.toEntity(dateKey, log.id, System.currentTimeMillis() + index) })
    }

    override suspend fun updateFoodLog(log: FoodLog, mealType: MealType, amount: Double) {
        require(amount > 0) { "Miktar sıfırdan büyük olmalı." }
        val factor = amount / log.amount.coerceAtLeast(0.0001)
        dao.updateLog(
            id = log.id,
            mealType = mealType.name,
            amount = amount,
            grams = log.grams * factor,
            calories = (log.calories * factor).roundToInt(),
            proteinGrams = log.proteinGrams * factor,
            carbsGrams = log.carbsGrams * factor,
            fatGrams = log.fatGrams * factor,
        )
    }

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

    private fun rememberFood(food: Food) = synchronized(savedFoods) {
        val previous = savedFoods[food.id]
        savedFoods[food.id] = SavedFoodRecord(food, previous?.favorite ?: false, System.currentTimeMillis())
        persistSavedFoods()
    }

    private fun persistSavedFoods() {
        savedFoodPreferences.edit().putString("records", gson.toJson(savedFoods.values.toList())).apply()
    }

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

    private fun FoodLog.toEntity(dateKey: String, entityId: String, timestamp: Long) = FoodLogEntity(
        id = entityId,
        dateKey = dateKey,
        mealType = mealType.name,
        foodName = foodName,
        brand = brand,
        amount = amount,
        unit = unit.name,
        grams = grams,
        calories = calories,
        proteinGrams = proteinGrams,
        carbsGrams = carbsGrams,
        fatGrams = fatGrams,
        createdAt = timestamp,
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
            Food("strawberry", "Çilek", caloriesPer100g = 32.0, proteinPer100g = 0.7, carbsPer100g = 7.7, fatPer100g = 0.3, defaultUnit = FoodUnit.PIECE, gramsPerUnit = 12.0),
            Food("almond", "Badem", caloriesPer100g = 579.0, proteinPer100g = 21.0, carbsPer100g = 22.0, fatPer100g = 50.0),
            Food("walnut", "Ceviz", caloriesPer100g = 654.0, proteinPer100g = 15.0, carbsPer100g = 14.0, fatPer100g = 65.0),
            Food("hazelnut", "Fındık", caloriesPer100g = 628.0, proteinPer100g = 15.0, carbsPer100g = 17.0, fatPer100g = 61.0),
            Food("honey", "Bal", caloriesPer100g = 304.0, proteinPer100g = 0.3, carbsPer100g = 82.0, fatPer100g = 0.0),
            Food("tahini-molasses", "Tahin pekmez", caloriesPer100g = 490.0, proteinPer100g = 10.0, carbsPer100g = 55.0, fatPer100g = 27.0),
        )
    }
}

private fun OffSearchResponse.totalPages(): Int {
    pageCount?.takeIf { it > 0 }?.let { return it }
    val size = pageSize?.takeIf { it > 0 } ?: 25
    val resultCount = count?.coerceAtLeast(0) ?: products.size
    return maxOf(1, (resultCount + size - 1) / size)
}

internal fun String.searchKey(): String = Normalizer.normalize(lowercase(Locale.forLanguageTag("tr-TR")), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .replace('ı', 'i')

private val producePieceWeights = linkedMapOf(
    "hindistan cevizi" to 400.0, "çarkıfelek" to 18.0, "ejder meyvesi" to 300.0,
    "dolmalık biber" to 140.0, "kapya biber" to 120.0, "sivri biber" to 25.0,
    "brüksel lahanası" to 20.0, "tatlı patates" to 180.0, "yer elması" to 80.0,
    "yeşil soğan" to 15.0, "taze soğan" to 15.0, "kuru soğan" to 110.0,
    "avokado" to 150.0, "greyfurt" to 230.0, "portakal" to 180.0, "mandalina" to 100.0,
    "limon" to 80.0, "misket limonu" to 55.0, "elma" to 180.0, "armut" to 180.0,
    "ayva" to 250.0, "muz" to 120.0, "şeftali" to 150.0, "nektarin" to 140.0,
    "kayısı" to 35.0, "erik" to 45.0, "kiraz" to 8.0, "vişne" to 7.0,
    "çilek" to 12.0, "incir" to 50.0, "hurma" to 24.0, "kivi" to 75.0,
    "nar" to 280.0, "mango" to 250.0, "papaya" to 300.0, "guava" to 90.0,
    "üzüm" to 5.0, "yaban mersini" to 2.0, "böğürtlen" to 5.0, "ahududu" to 4.0,
    "karpuz" to 4000.0, "kavun" to 1500.0, "domates" to 120.0, "salatalık" to 150.0,
    "patlıcan" to 250.0, "kabak" to 200.0, "biber" to 80.0, "patates" to 170.0,
    "havuç" to 70.0, "pancar" to 100.0, "turp" to 35.0, "şalgam" to 120.0,
    "soğan" to 110.0, "sarımsak" to 4.0, "pırasa" to 180.0, "kereviz" to 450.0,
    "brokoli" to 500.0, "karnabahar" to 600.0, "enginar" to 300.0, "bamya" to 12.0,
    "mantar" to 18.0, "bezelye" to 0.5, "fasulye" to 6.0, "kuşkonmaz" to 16.0,
    "mısır" to 120.0, "marul" to 300.0, "lahana" to 900.0, "pazı" to 20.0,
    "ıspanak" to 10.0, "roka" to 3.0, "maydanoz" to 2.0, "dereotu" to 1.0,
)

private val preparedProduceExclusions = listOf(
    "suyu", "icecek", "recel", "marmelat", "kek", "pasta", "dondurma", "yogurt", "sos",
    "corba", "tursu", "konserve", "kurutulmus", "cips", "salata", "sandvic", "pure", "yemegi",
)

private fun produceUnitWeight(name: String): Double? {
    val key = name.searchKey()
    if (preparedProduceExclusions.any(key::contains)) return null
    return producePieceWeights.entries.firstOrNull { (produce, _) ->
        Regex("(^|[^a-z0-9])${Regex.escape(produce.searchKey())}([^a-z0-9]|$)").containsMatchIn(key)
    }?.value
}

private val broadFoodAliases = mapOf(
    "kahve" to listOf("kahve", "coffee", "espresso", "latte", "cappuccino", "americano", "mocha", "nescafe", "starbucks"),
    "ekmek" to listOf("ekmek", "bread", "tost", "baget", "bazlama", "pide", "lavaş", "simit"),
    "cikolata" to listOf("çikolata", "chocolate", "gofret", "kakao", "cocoa"),
    "su" to listOf("su", "water", "maden suyu", "soda"),
    "peynir" to listOf("peynir", "cheese", "kaşar", "beyaz peynir", "labne"),
    "sut" to listOf("süt", "milk", "laktozsuz", "badem içeceği", "yulaf içeceği"),
)

internal fun Food.searchScore(normalizedQuery: String): Int {
    if (normalizedQuery.isBlank()) return 1
    val nameKey = name.searchKey()
    val brandKey = brand?.searchKey().orEmpty()
    val aliasKey = searchAliases.joinToString(" ").searchKey()
    val haystack = "$nameKey $brandKey $aliasKey"
    var score = when {
        nameKey == normalizedQuery -> 1000
        nameKey.startsWith("$normalizedQuery ") -> 850
        nameKey.split(' ').any { it == normalizedQuery } -> 760
        nameKey.contains(normalizedQuery) -> 650
        brandKey == normalizedQuery -> 900
        brandKey.startsWith(normalizedQuery) -> 820
        brandKey.contains(normalizedQuery) -> 720
        normalizedQuery.split(' ').all { it.length < 2 || haystack.contains(it) } -> 400
        else -> 0
    }
    val aliases = broadFoodAliases[normalizedQuery]
    if (aliases != null && aliases.any { haystack.contains(it.searchKey()) }) score = maxOf(score, 560)
    if (score > 0 && source == com.caloly.app.domain.model.FoodSource.CALOLY) score += 30
    return score
}
