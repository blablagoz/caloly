package com.caloly.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodLogDao {
    @Query("SELECT * FROM food_logs WHERE dateKey = :dateKey ORDER BY createdAt DESC")
    fun observeByDate(dateKey: String): Flow<List<FoodLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: FoodLogEntity)

    @Query("DELETE FROM food_logs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT DISTINCT dateKey FROM food_logs ORDER BY dateKey")
    fun observeLoggedDates(): Flow<List<String>>

    @Transaction
    @Query("SELECT * FROM nutrition_templates ORDER BY createdAt DESC")
    fun observeTemplates(): Flow<List<NutritionTemplateWithItems>>

    @Transaction
    @Query("SELECT * FROM nutrition_templates WHERE id = :id")
    suspend fun templateById(id: String): NutritionTemplateWithItems?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: NutritionTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplateItems(items: List<NutritionTemplateItemEntity>)

    @Transaction
    suspend fun replaceTemplate(template: NutritionTemplateEntity, items: List<NutritionTemplateItemEntity>) {
        insertTemplate(template)
        insertTemplateItems(items)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<FoodLogEntity>)

    @Query("DELETE FROM nutrition_templates WHERE id = :id")
    suspend fun deleteTemplate(id: String)
}
