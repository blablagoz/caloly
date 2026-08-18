package com.caloly.app.presentation.addfood

import com.caloly.app.domain.model.Food
import com.caloly.app.domain.model.MealType
import org.junit.Assert.assertEquals
import org.junit.Test

class AddFoodResultsTest {
    @Test
    fun `changing meal never filters search results`() {
        val coffee = food("coffee", "Kahve")
        val breakfast = AddFoodUiState(mealType = MealType.BREAKFAST, localResults = listOf(coffee))

        val dinner = breakfast.copy(mealType = MealType.DINNER)

        assertEquals(listOf(coffee), dinner.visibleResults)
    }

    @Test
    fun `first online page interleaves local and internet matches`() {
        val local = listOf(food("local-1", "Yerel 1"), food("local-2", "Yerel 2"))
        val online = listOf(food("online-1", "İnternet 1"), food("online-2", "İnternet 2"))

        val merged = mergeFoodResults(local, online, hasSearchedOnline = true, onlinePage = 1)

        assertEquals(listOf("Yerel 1", "İnternet 1", "Yerel 2", "İnternet 2"), merged.map { it.name })
    }

    @Test
    fun `later online pages contain only that pages matches`() {
        val merged = mergeFoodResults(
            local = listOf(food("local", "Yerel")),
            online = listOf(food("online", "İnternet sayfa 2")),
            hasSearchedOnline = true,
            onlinePage = 2,
        )

        assertEquals(listOf("İnternet sayfa 2"), merged.map { it.name })
    }

    @Test
    fun `same barcode is shown once in unified matches`() {
        val local = food("local", "Ürün", barcode = "8690000000001")
        val remote = food("remote", "Ürün yeni adı", barcode = "8690000000001")

        val merged = mergeFoodResults(listOf(local), listOf(remote), hasSearchedOnline = true, onlinePage = 1)

        assertEquals(1, merged.size)
    }

    @Test
    fun `pagination keeps first last and nearby pages visible`() {
        assertEquals(listOf(1, 7, 8, 9, 20), paginationWindow(currentPage = 8, totalPages = 20))
        assertEquals(listOf(1, 2, 3), paginationWindow(currentPage = 1, totalPages = 3))
    }

    private fun food(id: String, name: String, barcode: String? = null) = Food(
        id = id,
        name = name,
        caloriesPer100g = 100.0,
        proteinPer100g = 1.0,
        carbsPer100g = 1.0,
        fatPer100g = 1.0,
        barcode = barcode,
    )
}
