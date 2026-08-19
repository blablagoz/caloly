package com.caloly.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        Room.databaseBuilder(context, CalolyDatabase::class.java, "caloly.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideFoodLogDao(database: CalolyDatabase): FoodLogDao = database.foodLogDao()

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS nutrition_templates (id TEXT NOT NULL, name TEXT NOT NULL, kind TEXT NOT NULL, sourceOwnerName TEXT, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
            db.execSQL("CREATE TABLE IF NOT EXISTS nutrition_template_items (id TEXT NOT NULL, templateId TEXT NOT NULL, mealType TEXT NOT NULL, foodName TEXT NOT NULL, brand TEXT, amount REAL NOT NULL, unit TEXT NOT NULL, grams REAL NOT NULL, calories INTEGER NOT NULL, proteinGrams REAL NOT NULL, carbsGrams REAL NOT NULL, fatGrams REAL NOT NULL, PRIMARY KEY(id), FOREIGN KEY(templateId) REFERENCES nutrition_templates(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_nutrition_template_items_templateId ON nutrition_template_items(templateId)")
        }
    }
}
