package com.caloly.app.domain.usecase

import com.caloly.app.domain.repository.NutritionRepository
import javax.inject.Inject

class ObserveDailySummaryUseCase @Inject constructor(
    private val repository: NutritionRepository,
) {
    operator fun invoke(dateKey: String) = repository.observeDailySummary(dateKey)
}
