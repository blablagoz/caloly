package com.caloly.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caloly.app.domain.model.DailySummary
import com.caloly.app.domain.model.HealthConnectAvailability
import com.caloly.app.domain.model.HealthSummary
import com.caloly.app.domain.auth.AuthRepository
import com.caloly.app.domain.auth.AuthState
import com.caloly.app.domain.auth.CalolyUser
import com.caloly.app.domain.usecase.GetHealthStatusUseCase
import com.caloly.app.domain.usecase.ObserveDailySummaryUseCase
import com.caloly.app.domain.social.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HealthUiState(
    val availability: HealthConnectAvailability = HealthConnectAvailability.UNAVAILABLE,
    val hasPermissions: Boolean = false,
    val health: HealthSummary = HealthSummary(),
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeDailySummary: ObserveDailySummaryUseCase,
    private val healthStatus: GetHealthStatusUseCase,
    private val socialRepository: SocialRepository,
    authRepository: AuthRepository,
) : ViewModel() {
    private val today = LocalDate.now().toString()
    private val nutrition = observeDailySummary(today)
    private val _healthState = MutableStateFlow(HealthUiState(availability = healthStatus.availability()))
    val healthState: StateFlow<HealthUiState> = _healthState

    val requiredHealthPermissions: Set<String> get() = healthStatus.requiredPermissions

    val summary: StateFlow<DailySummary> = combine(nutrition, _healthState, authRepository.authState) { food, health, auth ->
        val goals = (auth as? AuthState.SignedIn)?.user?.personalGoals()
        food.copy(
            calorieGoal = goals?.calories ?: food.calorieGoal,
            proteinGoal = goals?.protein ?: food.proteinGoal,
            carbsGoal = goals?.carbs ?: food.carbsGoal,
            fatGoal = goals?.fat ?: food.fatGoal,
            steps = health.health.steps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            activeCalories = health.health.activeCalories,
            totalCaloriesBurned = health.health.totalCaloriesBurned,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailySummary())

    init {
        refreshHealth()
        viewModelScope.launch {
            summary.drop(1).debounce(1_000).collectLatest { value ->
                runCatching { socialRepository.syncOwnDailySummary(today, value) }
            }
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
                _healthState.value = HealthUiState(availability, granted, health, loading = false)
            }.onFailure { error ->
                _healthState.value = _healthState.value.copy(loading = false, error = error.message ?: "Sağlık verisi okunamadı")
            }
        }
    }

    fun onHealthPermissionsResult() = refreshHealth()
}

private data class PersonalGoals(val calories: Int, val protein: Int, val carbs: Int, val fat: Int)

private fun CalolyUser.personalGoals(): PersonalGoals? {
    val height = heightCm ?: return null
    val weight = weightKg ?: return null
    val birthday = birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    val age = java.time.Period.between(birthday, LocalDate.now()).years.coerceIn(16, 100)
    val genderOffset = when (gender) { "MALE" -> 5.0; "FEMALE" -> -161.0; else -> -78.0 }
    val bmr = 10.0 * weight + 6.25 * height - 5.0 * age + genderOffset
    val activityMultiplier = when (activityLevel) { "SEDENTARY" -> 1.2; "LIGHT" -> 1.375; "ACTIVE" -> 1.725; else -> 1.55 }
    val goalAdjustment = when (nutritionGoal) { "LOSE" -> -400; "GAIN" -> 300; else -> 0 }
    val calories = (bmr * activityMultiplier + goalAdjustment).toInt().coerceIn(1200, 4500)
    val protein = (weight * if (nutritionGoal == "LOSE") 1.8 else 1.6).toInt().coerceAtLeast(50)
    val fat = (calories * .27 / 9).toInt()
    val carbs = ((calories - protein * 4 - fat * 9) / 4).coerceAtLeast(80)
    return PersonalGoals(calories, protein, carbs, fat)
}
