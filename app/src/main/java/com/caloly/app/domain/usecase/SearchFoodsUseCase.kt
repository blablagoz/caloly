package com.caloly.app.domain.usecase

import com.caloly.app.domain.repository.NutritionRepository
import javax.inject.Inject

class SearchFoodsUseCase @Inject constructor(
    private val repository: NutritionRepository,
) {
    val localCatalogSize: Int get() = repository.localCatalogSize
    val favoriteFoodIds: Set<String> get() = repository.favoriteFoodIds
    fun local(query: String) = repository.searchLocalFoods(query)
    suspend fun remote(query: String) = repository.searchRemoteFoods(query)
    fun saveCustom(food: com.caloly.app.domain.model.Food) = repository.saveCustomFood(food)
    fun toggleFavorite(food: com.caloly.app.domain.model.Food) = repository.toggleFavorite(food)
}
