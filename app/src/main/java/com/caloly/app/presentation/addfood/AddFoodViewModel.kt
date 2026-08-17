package com.caloly.app.presentation.addfood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caloly.app.domain.model.Food
import com.caloly.app.domain.model.FoodUnit
import com.caloly.app.domain.model.MealType
import com.caloly.app.domain.usecase.AddFoodLogUseCase
import com.caloly.app.domain.usecase.FindFoodByBarcodeUseCase
import com.caloly.app.domain.usecase.SearchFoodsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class AddFoodViewModel @Inject constructor(
    private val searchFoods: SearchFoodsUseCase,
    private val findFoodByBarcode: FindFoodByBarcodeUseCase,
    private val addFoodLog: AddFoodLogUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddFoodUiState())
    val uiState: StateFlow<AddFoodUiState> = _uiState.asStateFlow()

    init { refreshLocalResults() }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value, errorMessage = null, showingRemoteResults = false) }
        refreshLocalResults()
    }

    fun onMealSelected(meal: MealType) = _uiState.update { it.copy(mealType = meal) }

    fun onFoodSelected(food: Food) {
        _uiState.update {
            it.copy(
                selectedFood = food,
                amountText = defaultAmount(food.defaultUnit),
                unit = food.defaultUnit,
                errorMessage = null,
            )
        }
    }

    fun onAmountChange(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d{0,5}([.,]\\d{0,2})?$"))) {
            _uiState.update { it.copy(amountText = value) }
        }
    }

    fun onUnitSelected(unit: FoodUnit) = _uiState.update { it.copy(unit = unit) }

    fun clearSelection() = _uiState.update { it.copy(selectedFood = null) }

    fun searchOnline() {
        val query = _uiState.value.query.trim()
        if (query.length < 2) {
            _uiState.update { it.copy(errorMessage = "İnternette aramak için en az 2 karakter gir.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            searchFoods.remote(query)
                .onSuccess { foods ->
                    _uiState.update {
                        it.copy(
                            results = foods,
                            isLoading = false,
                            showingRemoteResults = true,
                            errorMessage = if (foods.isEmpty()) "İnternette eşleşen ürün bulunamadı." else null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Besin veritabanına ulaşılamadı.",
                        )
                    }
                }
        }
    }

    fun onBarcodeScanned(barcode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            findFoodByBarcode(barcode)
                .onSuccess { food ->
                    if (food == null) {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Bu barkod Open Food Facts'te bulunamadı.") }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                query = food.name,
                                results = listOf(food),
                                showingRemoteResults = true,
                            )
                        }
                        onFoodSelected(food)
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "Barkod sorgulanamadı.") }
                }
        }
    }

    fun onScannerError(message: String) = _uiState.update { it.copy(errorMessage = message) }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        val food = state.selectedFood ?: return
        val amount = state.amountText.replace(',', '.').toDoubleOrNull() ?: return
        if (amount <= 0) return
        viewModelScope.launch {
            addFoodLog(LocalDate.now().toString(), state.mealType, food, amount, state.unit)
            onSaved()
        }
    }

    private fun refreshLocalResults() {
        _uiState.update { it.copy(results = searchFoods.local(it.query)) }
    }

    private fun defaultAmount(unit: FoodUnit) = when (unit) {
        FoodUnit.GRAM -> "100"
        FoodUnit.MILLILITER -> "200"
        else -> "1"
    }
}

data class AddFoodUiState(
    val mealType: MealType = MealType.BREAKFAST,
    val query: String = "",
    val results: List<Food> = emptyList(),
    val selectedFood: Food? = null,
    val amountText: String = "100",
    val unit: FoodUnit = FoodUnit.GRAM,
    val isLoading: Boolean = false,
    val showingRemoteResults: Boolean = false,
    val errorMessage: String? = null,
) {
    val amount: Double get() = amountText.replace(',', '.').toDoubleOrNull() ?: 0.0

    private val estimatedGrams: Double
        get() = selectedFood?.let { food ->
            when (unit) {
                FoodUnit.GRAM, FoodUnit.MILLILITER -> amount
                else -> amount * food.gramsPerUnit
            }
        } ?: 0.0

    val previewCalories: Int
        get() = selectedFood?.let { (it.caloriesPer100g * estimatedGrams / 100.0).roundToInt() } ?: 0
    val previewProtein: Int
        get() = selectedFood?.let { (it.proteinPer100g * estimatedGrams / 100.0).roundToInt() } ?: 0
    val previewCarbs: Int
        get() = selectedFood?.let { (it.carbsPer100g * estimatedGrams / 100.0).roundToInt() } ?: 0
    val previewFat: Int
        get() = selectedFood?.let { (it.fatPer100g * estimatedGrams / 100.0).roundToInt() } ?: 0
}
