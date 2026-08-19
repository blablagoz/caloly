package com.caloly.app.domain.usecase

import com.caloly.app.domain.repository.AiMealRepository
import javax.inject.Inject

class AnalyzeMealPhotoUseCase @Inject constructor(
    private val repository: AiMealRepository,
) {
    suspend operator fun invoke(contentUri: String) = repository.analyzePhoto(contentUri)
}
