package com.caloly.app.domain.usecase

import com.caloly.app.domain.repository.NutritionRepository
import javax.inject.Inject

class SearchFoodsUseCase @Inject constructor(
    private val repository: NutritionRepository,
) {
    fun local(query: String) = repository.searchLocalFoods(query)
    suspend fun remote(query: String) = repository.searchRemoteFoods(query)
}
