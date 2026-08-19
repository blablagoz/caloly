package com.caloly.app.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "nutrition_templates")
data class NutritionTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val sourceOwnerName: String?,
    val createdAt: Long,
)

@Entity(
    tableName = "nutrition_template_items",
    foreignKeys = [ForeignKey(
        entity = NutritionTemplateEntity::class,
        parentColumns = ["id"],
        childColumns = ["templateId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("templateId")],
)
data class NutritionTemplateItemEntity(
    @PrimaryKey val id: String,
    val templateId: String,
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
)

data class NutritionTemplateWithItems(
    @Embedded val template: NutritionTemplateEntity,
    @Relation(parentColumn = "id", entityColumn = "templateId")
    val items: List<NutritionTemplateItemEntity>,
)
