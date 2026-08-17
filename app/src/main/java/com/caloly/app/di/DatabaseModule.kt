package com.caloly.app.di

import android.content.Context
import androidx.room.Room
import com.caloly.app.data.local.CalolyDatabase
import com.caloly.app.data.local.FoodLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CalolyDatabase =
        Room.databaseBuilder(context, CalolyDatabase::class.java, "caloly.db").build()

    @Provides
    fun provideFoodLogDao(database: CalolyDatabase): FoodLogDao = database.foodLogDao()
}
