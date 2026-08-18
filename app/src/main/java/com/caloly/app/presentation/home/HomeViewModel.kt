package com.caloly.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caloly.app.domain.model.*
import com.caloly.app.domain.repository.NutritionRepository
import com.caloly.app.domain.social.SocialRepository
import com.caloly.app.domain.usecase.GetHealthStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HealthUiState(
    val availability: HealthConnectAvailability = HealthConnectAvailability.UNAVAILABLE,
    val hasPermissions: Boolean = false,
    val health: HealthSummary = HealthSummary(),
    val loading: Boolean = false,
    val error: String? = null,
    val lastUpdatedAt: Long? = null,
)

data class TemplateActionState(
    val loading: Boolean = false,
    val message: String? = null,
)

data class LogActionState(
    val message: String? = null,
    val undoAvailable: Boolean = false,
    val eventId: Long = 0,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HomeViewModel @Inject constructor(
    private val nutritionRepository: NutritionRepository,
    private val healthStatus: GetHealthStatusUseCase,
    private val socialRepository: SocialRepository,
) : ViewModel() {
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val datedNutrition = _selectedDate.flatMapLatest { date ->
        nutritionRepository.observeDailySummary(date.toString()).map { date to it }
    }
    private val _healthState = MutableStateFlow(HealthUiState(availability = healthStatus.availability()))
    val healthState: StateFlow<HealthUiState> = _healthState.asStateFlow()
    val loggedDates = nutritionRepository.observeLoggedDates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    val templates = nutritionRepository.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _templateAction = MutableStateFlow(TemplateActionState())
    val templateAction: StateFlow<TemplateActionState> = _templateAction.asStateFlow()
    private val _logAction = MutableStateFlow(LogActionState())
    val logAction: StateFlow<LogActionState> = _logAction.asStateFlow()
    private var deletedBackup: Pair<String, List<FoodLog>>? = null

    val requiredHealthPermissions: Set<String> get() = healthStatus.requiredPermissions

    val summary: StateFlow<DailySummary> = combine(datedNutrition, _healthState) { (date, food), health ->
        val currentHealth = if (date == LocalDate.now()) health.health else HealthSummary()
        food.copy(
            calorieGoal = 0,
            proteinGoal = 0,
            carbsGoal = 0,
            fatGoal = 0,
            steps = currentHealth.steps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            activeCalories = currentHealth.activeCalories,
            totalCaloriesBurned = currentHealth.totalCaloriesBurned,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailySummary())

    init {
        refreshHealth()
        viewModelScope.launch {
            combine(datedNutrition, _healthState) { dated, health ->
                val (date, food) = dated
                val currentHealth = if (date == LocalDate.now()) health.health else HealthSummary()
                date to food.copy(
                    calorieGoal = 0, proteinGoal = 0, carbsGoal = 0, fatGoal = 0,
                    steps = currentHealth.steps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    activeCalories = currentHealth.activeCalories,
                    totalCaloriesBurned = currentHealth.totalCaloriesBurned,
                )
            }.drop(1).debounce(1_000).collectLatest { (date, value) ->
                runCatching { socialRepository.syncOwnDailySummary(date.toString(), value) }
            }
        }
    }

    fun previousDay() { _selectedDate.value = _selectedDate.value.minusDays(1) }
    fun nextDay() { _selectedDate.value = _selectedDate.value.plusDays(1) }
    fun selectDate(date: LocalDate) { _selectedDate.value = date }

    fun saveMealTemplate(name: String, mealType: MealType, share: Boolean) = templateAction {
        val logs = summary.value.logs.filter { it.mealType == mealType }
        val template = nutritionRepository.saveTemplate(name, TemplateKind.MEAL, logs)
        if (share) socialRepository.publishTemplate(template)
        "Öğün daha sonra kullanmak üzere kaydedildi"
    }

    fun saveDayTemplate(name: String, share: Boolean) = templateAction {
        val template = nutritionRepository.saveTemplate(name, TemplateKind.DAY, summary.value.logs)
        if (share) socialRepository.publishTemplate(template)
        "Günün tamamı şablon olarak kaydedildi"
    }

    fun applyTemplate(templateId: String) = templateAction {
        nutritionRepository.applyTemplate(templateId, _selectedDate.value.toString())
        "Kayıtlı beslenme seçili güne eklendi"
    }

    fun deleteTemplate(templateId: String) = templateAction {
        nutritionRepository.deleteTemplate(templateId)
        "Şablon silindi"
    }

    fun deleteLog(log: FoodLog) = deleteLogs(listOf(log), "Besin kaydı silindi")

    fun deleteMeal(mealType: MealType) {
        val logs = summary.value.logs.filter { it.mealType == mealType }
        deleteLogs(logs, "${mealType.label} kayıtları silindi")
    }

    fun deleteDay() = deleteLogs(summary.value.logs, "Seçili günün beslenme kayıtları silindi")

    fun undoLastDeletion() {
        val backup = deletedBackup ?: return
        viewModelScope.launch {
            runCatching { nutritionRepository.restoreFoodLogs(backup.first, backup.second) }
                .onSuccess {
                    deletedBackup = null
                    _logAction.value = LogActionState("Silme işlemi geri alındı", eventId = System.nanoTime())
                }
                .onFailure {
                    _logAction.value = LogActionState("Kayıtlar geri yüklenemedi", eventId = System.nanoTime())
                }
        }
    }

    fun updateLog(log: FoodLog, mealType: MealType, amount: Double) {
        viewModelScope.launch {
            runCatching { nutritionRepository.updateFoodLog(log, mealType, amount) }
                .onSuccess { _logAction.value = LogActionState("Besin kaydı güncellendi", eventId = System.nanoTime()) }
                .onFailure { _logAction.value = LogActionState("Besin kaydı güncellenemedi", eventId = System.nanoTime()) }
        }
    }

    private fun deleteLogs(logs: List<FoodLog>, successMessage: String) {
        if (logs.isEmpty()) return
        val dateKey = _selectedDate.value.toString()
        viewModelScope.launch {
            runCatching { nutritionRepository.deleteFoodLogs(logs.map { it.id }) }
                .onSuccess {
                    deletedBackup = dateKey to logs
                    _logAction.value = LogActionState(successMessage, undoAvailable = true, eventId = System.nanoTime())
                }
                .onFailure { _logAction.value = LogActionState("Silme işlemi tamamlanamadı", eventId = System.nanoTime()) }
        }
    }

    fun clearTemplateMessage() { _templateAction.value = TemplateActionState() }

    private fun templateAction(block: suspend () -> String) {
        viewModelScope.launch {
            _templateAction.value = TemplateActionState(loading = true)
            _templateAction.value = runCatching { TemplateActionState(message = block()) }
                .getOrElse { TemplateActionState(message = "İşlem şu anda tamamlanamadı. Tekrar deneyebilirsin.") }
        }
    }

    fun refreshHealth() {
        viewModelScope.launch {
            val availability = healthStatus.availability()
            _healthState.value = _healthState.value.copy(availability = availability, loading = true, error = null)
            if (availability != HealthConnectAvailability.AVAILABLE) {
                _healthState.value = _healthState.value.copy(loading = false, hasPermissions = false)
                return@launch
            }
            runCatching {
                val granted = healthStatus.hasAllPermissions()
                val health = if (granted) healthStatus.readToday() else HealthSummary()
                granted to health
            }.onSuccess { (granted, health) ->
                _healthState.value = HealthUiState(
                    availability = availability,
                    hasPermissions = granted,
                    health = health,
                    loading = false,
                    lastUpdatedAt = if (granted) System.currentTimeMillis() else null,
                )
            }.onFailure { error ->
                _healthState.value = _healthState.value.copy(loading = false, error = error.message ?: "Sağlık verisi okunamadı")
            }
        }
    }

    fun onHealthPermissionsResult() = refreshHealth()
}
