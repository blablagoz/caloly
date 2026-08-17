package com.caloly.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "food_logs")
data class FoodLogEntity(
    @PrimaryKey val id: String,
    val dateKey: String,
    val mealType: String,
    val foodName: String,
    val brand: String?,
    val amount: Double,
    val unit: String,
    val grams: Double,
    val calories: Int,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val createdAt: Long,
)
