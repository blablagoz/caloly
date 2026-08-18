package com.caloly.app.presentation.addfood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caloly.app.domain.model.AiMealAnalysis
import com.caloly.app.domain.model.DetectedFood
import com.caloly.app.domain.model.Food
import com.caloly.app.domain.model.FoodSource
import com.caloly.app.domain.model.FoodUnit
import com.caloly.app.domain.model.MealType
import com.caloly.app.domain.usecase.AddFoodLogUseCase
import com.caloly.app.domain.usecase.AnalyzeMealDescriptionUseCase
import com.caloly.app.domain.usecase.AnalyzeMealPhotoUseCase
import com.caloly.app.domain.usecase.FindFoodByBarcodeUseCase
import com.caloly.app.domain.usecase.SearchFoodsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class AddFoodViewModel @Inject constructor(
    private val searchFoods: SearchFoodsUseCase,
    private val findFoodByBarcode: FindFoodByBarcodeUseCase,
    private val addFoodLog: AddFoodLogUseCase,
    private val analyzeMealPhoto: AnalyzeMealPhotoUseCase,
    private val analyzeMealDescription: AnalyzeMealDescriptionUseCase,
) : ViewModel() {
    private var localSearchJob: Job? = null
    private var onlineSearchJob: Job? = null
    private var searchGeneration = 0L
    private val _uiState = MutableStateFlow(AddFoodUiState(favoriteIds = searchFoods.favoriteFoodIds))
    val uiState: StateFlow<AddFoodUiState> = _uiState.asStateFlow()

    init { launchLocalSearch("", 0) }

    fun setDate(dateKey: String) {
        if (runCatching { LocalDate.parse(dateKey) }.isSuccess) _uiState.update { it.copy(dateKey = dateKey) }
    }

    fun onQueryChange(value: String) {
        searchGeneration++
        onlineSearchJob?.cancel()
        _uiState.update {
            it.copy(
                query = value,
                onlineResults = emptyList(),
                onlinePage = 1,
                totalOnlinePages = 1,
                totalOnlineResults = 0,
                hasSearchedOnline = false,
                isOnlineLoading = false,
                errorMessage = null,
            )
        }
        launchLocalSearch(value, if (value.isBlank()) 0 else 220)
    }

    fun onMealSelected(meal: MealType) = _uiState.update { it.copy(mealType = meal) }

    fun analyzePhoto(contentUri: String) {
        if (_uiState.value.isAiLoading) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAiLoading = true,
                    aiErrorMessage = null,
                    aiAnalysis = null,
                    lastAiPhotoUri = contentUri,
                )
            }
            analyzeMealPhoto(contentUri)
                .onSuccess { analysis ->
                    _uiState.update { it.copy(isAiLoading = false, aiAnalysis = analysis, aiErrorMessage = null) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isAiLoading = false,
                            aiErrorMessage = error.message ?: "Yemek analizi tamamlanamadı. Tekrar deneyebilirsin.",
                        )
                    }
                }
        }
    }

    fun retryPhotoAnalysis() {
        _uiState.value.lastAiPhotoUri?.let(::analyzePhoto)
    }

    fun dismissAiFlow() {
        _uiState.update {
            it.copy(
                aiAnalysis = null,
                aiErrorMessage = null,
                isAiLoading = false,
                lastAiPhotoUri = null,
            )
        }
    }

    fun updateDetectedFood(index: Int, food: DetectedFood) {
        _uiState.update { state ->
            val analysis = state.aiAnalysis ?: return@update state
            if (index !in analysis.foods.indices) return@update state
            state.copy(aiAnalysis = analysis.copy(foods = analysis.foods.toMutableList().apply { set(index, food) }))
        }
    }

    fun removeDetectedFood(index: Int) {
        _uiState.update { state ->
            val analysis = state.aiAnalysis ?: return@update state
            if (index !in analysis.foods.indices) return@update state
            state.copy(aiAnalysis = analysis.copy(foods = analysis.foods.filterIndexed { itemIndex, _ -> itemIndex != index }))
        }
    }

    fun confirmAiMeal(onSaved: () -> Unit) {
        val foods = _uiState.value.aiAnalysis?.foods.orEmpty()
        if (foods.isEmpty() || _uiState.value.isAiSaving) return
        saveAiFoods(foods, onSaved)
    }

    fun analyzeDescriptionAndSave(description: String, onSaved: () -> Unit) {
        if (_uiState.value.isAiLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAiLoading = true, aiErrorMessage = null) }
            analyzeMealDescription(description)
                .onSuccess { analysis ->
                    saveAiFoods(analysis.foods, onSaved)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isAiLoading = false,
                            aiErrorMessage = error.message ?: "Yazdığın öğün hesaplanamadı. Tekrar deneyebilirsin.",
                        )
                    }
                }
        }
    }

    fun onFoodSelected(food: Food) {
        _uiState.update {
            it.copy(selectedFood = food, amountText = defaultAmount(food.defaultUnit), unit = food.defaultUnit, errorMessage = null)
        }
    }

    fun toggleFavorite(food: Food) {
        searchFoods.toggleFavorite(food)
        _uiState.update { it.copy(favoriteIds = searchFoods.favoriteFoodIds) }
        launchLocalSearch(_uiState.value.query, 0)
    }

    fun onAmountChange(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d{0,5}([.,]\\d{0,2})?$"))) _uiState.update { it.copy(amountText = value) }
    }

    fun onUnitSelected(unit: FoodUnit) = _uiState.update { it.copy(unit = unit) }
    fun clearSelection() = _uiState.update { it.copy(selectedFood = null) }

    fun searchOnline(page: Int = 1) {
        val query = _uiState.value.query.trim()
        if (query.length < 2) {
            _uiState.update { it.copy(errorMessage = "İnternette aramak için en az 2 karakter gir.") }
            return
        }
        val requestedPage = page.coerceAtLeast(1)
        val generation = ++searchGeneration
        localSearchJob?.cancel()
        onlineSearchJob?.cancel()
        onlineSearchJob = viewModelScope.launch {
            _uiState.update { it.copy(isOnlineLoading = true, errorMessage = null) }
            val local = if (requestedPage == 1) {
                withContext(Dispatchers.Default) { searchFoods.local(query) }
            } else {
                _uiState.value.localResults
            }
            val remoteResult = searchFoods.remote(query, requestedPage)
            if (generation != searchGeneration || _uiState.value.query.trim() != query) return@launch
            remoteResult.onSuccess { remotePage ->
                val localKeys = local.mapTo(hashSetOf(), ::resultKey)
                val uniqueRemote = if (requestedPage == 1) {
                    remotePage.items.filterNot { resultKey(it) in localKeys }
                } else {
                    remotePage.items
                }
                _uiState.update {
                    it.copy(
                        localResults = local,
                        onlineResults = uniqueRemote,
                        onlinePage = remotePage.currentPage,
                        totalOnlinePages = remotePage.totalPages,
                        totalOnlineResults = remotePage.totalResults,
                        catalogSize = searchFoods.localCatalogSize,
                        isLocalLoading = false,
                        isOnlineLoading = false,
                        hasSearchedOnline = true,
                        errorMessage = if (local.isEmpty() && uniqueRemote.isEmpty()) "Ürün bulunamadı." else null,
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        localResults = local,
                        catalogSize = searchFoods.localCatalogSize,
                        isLocalLoading = false,
                        isOnlineLoading = false,
                        hasSearchedOnline = true,
                        errorMessage = "İnternet araması şu anda tamamlanamadı. Mevcut eşleşmeler gösteriliyor.",
                    )
                }
            }
        }
    }

    fun onBarcodeScanned(barcode: String) {
        onlineSearchJob?.cancel()
        viewModelScope.launch {
            _uiState.update { it.copy(isOnlineLoading = true, errorMessage = null) }
            findFoodByBarcode(barcode).onSuccess { food ->
                if (food == null) {
                    _uiState.update {
                        it.copy(isOnlineLoading = false, missingBarcode = barcode.filter(Char::isDigit), errorMessage = "Ürün veritabanında bulunamadı. Barkodu koruyarak ürünü ekleyebilirsin.")
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isOnlineLoading = false,
                            query = food.name,
                            localResults = emptyList(),
                            onlineResults = listOf(food),
                            onlinePage = 1,
                            totalOnlinePages = 1,
                            totalOnlineResults = 1,
                            hasSearchedOnline = true,
                        )
                    }
                    onFoodSelected(food)
                }
            }.onFailure {
                _uiState.update { it.copy(isOnlineLoading = false, errorMessage = "Barkod şu anda sorgulanamadı. Tekrar deneyebilirsin.") }
            }
        }
    }

    fun onScannerError(message: String) = _uiState.update { it.copy(errorMessage = message) }

    fun createCustomFood(name: String, calories: Double, protein: Double, carbs: Double, fat: Double) {
        require(name.isNotBlank())
        val food = Food(
            id = "user:${_uiState.value.missingBarcode ?: System.currentTimeMillis()}", name = name.trim(),
            caloriesPer100g = calories.coerceAtLeast(0.0), proteinPer100g = protein.coerceAtLeast(0.0),
            carbsPer100g = carbs.coerceAtLeast(0.0), fatPer100g = fat.coerceAtLeast(0.0),
            barcode = _uiState.value.missingBarcode, source = FoodSource.USER,
        )
        searchFoods.saveCustom(food)
        _uiState.update { it.copy(missingBarcode = null, errorMessage = null) }
        onFoodSelected(food)
    }

    fun dismissManualFood() = _uiState.update { it.copy(missingBarcode = null) }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        val food = state.selectedFood ?: return
        val amount = state.amountText.replace(',', '.').toDoubleOrNull() ?: return
        if (amount <= 0) return
        viewModelScope.launch { addFoodLog(state.dateKey, state.mealType, food, amount, state.unit); onSaved() }
    }

    private fun launchLocalSearch(query: String, debounceMillis: Long) {
        val generation = ++searchGeneration
        localSearchJob?.cancel()
        localSearchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLocalLoading = true) }
            if (debounceMillis > 0) delay(debounceMillis)
            val results = withContext(Dispatchers.Default) { searchFoods.local(query) }
            if (generation == searchGeneration && _uiState.value.query == query) {
                _uiState.update { it.copy(localResults = results, catalogSize = searchFoods.localCatalogSize, isLocalLoading = false) }
            }
        }
    }

    private fun saveAiFoods(foods: List<DetectedFood>, onSaved: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAiSaving = true, isAiLoading = false, aiErrorMessage = null) }
            runCatching {
                val dateKey = _uiState.value.dateKey
                val mealType = _uiState.value.mealType
                foods.forEachIndexed { index, detected ->
                    val food = detected.toFood("ai:${System.currentTimeMillis()}:$index")
                    addFoodLog(dateKey, mealType, food, 1.0, FoodUnit.SERVING)
                }
            }.onSuccess {
                _uiState.update { it.copy(isAiSaving = false, aiAnalysis = null, lastAiPhotoUri = null) }
                onSaved()
            }.onFailure {
                _uiState.update {
                    it.copy(isAiSaving = false, aiErrorMessage = "Öğün kaydedilemedi. Tekrar deneyebilirsin.")
                }
            }
        }
    }

    private fun resultKey(food: Food): String = food.barcode ?: "${food.brand.orEmpty()}|${food.name}".lowercase(Locale.ROOT)

    private fun defaultAmount(unit: FoodUnit) = when (unit) {
        FoodUnit.GRAM -> "100"
        FoodUnit.MILLILITER -> "200"
        else -> "1"
    }
}

data class AddFoodUiState(
    val dateKey: String = LocalDate.now().toString(),
    val mealType: MealType = MealType.BREAKFAST,
    val query: String = "",
    val localResults: List<Food> = emptyList(),
    val onlineResults: List<Food> = emptyList(),
    val onlinePage: Int = 1,
    val totalOnlinePages: Int = 1,
    val totalOnlineResults: Int = 0,
    val selectedFood: Food? = null,
    val amountText: String = "100",
    val unit: FoodUnit = FoodUnit.GRAM,
    val isLocalLoading: Boolean = false,
    val isOnlineLoading: Boolean = false,
    val hasSearchedOnline: Boolean = false,
    val missingBarcode: String? = null,
    val errorMessage: String? = null,
    val catalogSize: Int = 0,
    val favoriteIds: Set<String> = emptySet(),
    val aiAnalysis: AiMealAnalysis? = null,
    val isAiLoading: Boolean = false,
    val isAiSaving: Boolean = false,
    val aiErrorMessage: String? = null,
    val lastAiPhotoUri: String? = null,
) {
    val isLoading: Boolean get() = isOnlineLoading || isAiLoading || isAiSaving
    val visibleResults: List<Food>
        get() = mergeFoodResults(localResults, onlineResults, hasSearchedOnline, onlinePage)
    val allResultsEmpty: Boolean get() = visibleResults.isEmpty()
    val amount: Double get() = amountText.replace(',', '.').toDoubleOrNull() ?: 0.0
    private val estimatedGrams: Double get() = selectedFood?.let { food ->
        when (unit) { FoodUnit.GRAM, FoodUnit.MILLILITER -> amount; else -> amount * food.gramsPerUnit }
    } ?: 0.0
    val previewCalories: Int get() = selectedFood?.let { (it.caloriesPer100g * estimatedGrams / 100.0).roundToInt() } ?: 0
    val previewProtein: Int get() = selectedFood?.let { (it.proteinPer100g * estimatedGrams / 100.0).roundToInt() } ?: 0
    val previewCarbs: Int get() = selectedFood?.let { (it.carbsPer100g * estimatedGrams / 100.0).roundToInt() } ?: 0
    val previewFat: Int get() = selectedFood?.let { (it.fatPer100g * estimatedGrams / 100.0).roundToInt() } ?: 0
}

internal fun mergeFoodResults(
    local: List<Food>,
    online: List<Food>,
    hasSearchedOnline: Boolean,
    onlinePage: Int,
): List<Food> {
    if (!hasSearchedOnline) return local.distinctBy(::foodResultKey)
    if (onlinePage > 1) return online.distinctBy(::foodResultKey)

    val merged = ArrayList<Food>(local.size + online.size)
    repeat(maxOf(local.size, online.size)) { index ->
        local.getOrNull(index)?.let(merged::add)
        online.getOrNull(index)?.let(merged::add)
    }
    return merged.distinctBy(::foodResultKey)
}

private fun foodResultKey(food: Food): String =
    food.barcode ?: "${food.brand.orEmpty()}|${food.name}".lowercase(Locale.ROOT)
