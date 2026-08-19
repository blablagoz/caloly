package com.caloly.app.data.repository

import com.caloly.app.domain.model.Food
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodSearchNormalizerTest {
    @Test fun `search is case and Turkish diacritic insensitive`() {
        assertEquals("ekmek", "Ekmek".searchKey())
        assertEquals("ekmek", "EKMEK".searchKey())
        assertEquals("cagri isik", "ÇAĞRI IŞIK".searchKey())
        assertEquals("sutlu cikolata", "Sütlü Çikolata".searchKey())
    }

    @Test fun `brand search rejects unrelated foods`() {
        val egg = Food("egg", "Yumurta", caloriesPer100g = 155.0, proteinPer100g = 13.0, carbsPer100g = 1.0, fatPer100g = 11.0)
        val latte = egg.copy(id = "latte", name = "Caffè Latte Tall", brand = "Starbucks")

        assertEquals(0, egg.searchScore("Starbucks".searchKey()))
        assertTrue(latte.searchScore("Starbucks".searchKey()) > 0)
    }

    @Test fun `broad coffee search includes coffee variants`() {
        val latte = Food("latte", "Iced Latte", brand = "Espressolab", caloriesPer100g = 120.0, proteinPer100g = 6.0, carbsPer100g = 12.0, fatPer100g = 5.0)
        assertTrue(latte.searchScore("kahve".searchKey()) > 0)
    }
}
