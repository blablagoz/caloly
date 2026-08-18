package com.caloly.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class FoodSearchNormalizerTest {
    @Test fun `search is case and Turkish diacritic insensitive`() {
        assertEquals("ekmek", "Ekmek".searchKey())
        assertEquals("ekmek", "EKMEK".searchKey())
        assertEquals("cagri isik", "ÇAĞRI IŞIK".searchKey())
        assertEquals("sutlu cikolata", "Sütlü Çikolata".searchKey())
    }
}
