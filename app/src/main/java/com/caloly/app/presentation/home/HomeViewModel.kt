package com.caloly.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caloly.app.domain.model.DailySummary
import com.caloly.app.domain.model.HealthConnectAvailability
import com.caloly.app.domain.model.HealthSummary
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
) : ViewModel() {
    private val today = LocalDate.now().toString()
    private val nutrition = observeDailySummary(today)
    private val _healthState = MutableStateFlow(HealthUiState(availability = healthStatus.availability()))
    val healthState: StateFlow<HealthUiState> = _healthState

    val requiredHealthPermissions: Set<String> get() = healthStatus.requiredPermissions

    val summary: StateFlow<DailySummary> = combine(nutrition, _healthState) { food, health ->
        food.copy(
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
