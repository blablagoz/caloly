package com.caloly.app.domain.usecase

import com.caloly.app.domain.repository.NutritionRepository
import javax.inject.Inject

class FindFoodByBarcodeUseCase @Inject constructor(
    private val repository: NutritionRepository,
) {
    suspend operator fun invoke(barcode: String) = repository.findFoodByBarcode(barcode)
}
