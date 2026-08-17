package com.caloly.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [FoodLogEntity::class, NutritionTemplateEntity::class, NutritionTemplateItemEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class CalolyDatabase : RoomDatabase() {
    abstract fun foodLogDao(): FoodLogDao
}
