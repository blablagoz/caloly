package com.caloly.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodLogDao {
    @Query("SELECT * FROM food_logs WHERE dateKey = :dateKey ORDER BY createdAt DESC")
    fun observeByDate(dateKey: String): Flow<List<FoodLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: FoodLogEntity)

    @Query("DELETE FROM food_logs WHERE id = :id")
    suspend fun deleteById(id: String)
}
