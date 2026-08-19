package com.caloly.app.data.repository

import com.google.gson.JsonParser
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalogAssetTest {
    private fun asset(name: String): File =
        listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
            .first { it.isFile }

    @Test fun `bundles thousands of Turkish generic foods`() {
        val root = JsonParser.parseReader(asset("turkish_generic_foods_2026.json").reader()).asJsonObject
        val foods = root.getAsJsonArray("foods")
        assertTrue("Expected at least 8,000 Turkish foods", foods.size() >= 8_000)
        assertTrue(foods.all { it.asJsonObject["name"].asString.isNotBlank() })
        assertTrue(foods.all { it.asJsonObject["calories"].asDouble in 0.01..1_000.0 })
    }

    @Test fun `Turkey packaged products are current and carry calories`() {
        val root = JsonParser.parseReader(asset("turkey_products_2024.json").reader()).asJsonObject
        val products = root.getAsJsonArray("products")
        assertTrue("Expected a substantial offline packaged-product snapshot", products.size() >= 800)
        assertTrue(products.all { it.asJsonObject["last_modified_t"].asLong >= 1_704_067_200L })
        assertTrue(products.all {
            it.asJsonObject.getAsJsonObject("nutriments")["energy-kcal_100g"]?.isJsonNull == false
        })
    }
}
