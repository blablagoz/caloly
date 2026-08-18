package com.caloly.app.domain.usecase

import com.caloly.app.domain.repository.AiMealRepository
import javax.inject.Inject

class AnalyzeMealDescriptionUseCase @Inject constructor(
    private val repository: AiMealRepository,
) {
    suspend operator fun invoke(description: String) = repository.analyzeDescription(description)
}
