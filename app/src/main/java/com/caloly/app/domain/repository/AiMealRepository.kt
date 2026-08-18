package com.caloly.app.domain.repository

import com.caloly.app.domain.model.AiMealAnalysis

interface AiMealRepository {
    suspend fun analyzePhoto(contentUri: String): Result<AiMealAnalysis>
    suspend fun analyzeDescription(description: String): Result<AiMealAnalysis>
}
