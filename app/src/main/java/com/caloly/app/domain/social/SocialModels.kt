package com.caloly.app.domain.social

data class SocialProfile(
    val id: String,
    val username: String?,
    val displayName: String?,
    val avatarUrl: String? = null,
    val relationshipStatus: String? = null,
)

data class IncomingFollowRequest(
    val requestId: String,
    val requester: SocialProfile,
    val relationshipType: String,
    val createdAt: String,
)

data class SharingPermissions(
    val calories: Boolean = true,
    val macros: Boolean = true,
    val steps: Boolean = true,
    val activity: Boolean = true,
    val weight: Boolean = false,
    val foodDetails: Boolean = false,
    val history: Boolean = false,
)

data class SocialConnection(
    val relationshipId: String,
    val profile: SocialProfile,
    val relationshipType: String,
    val mySharing: SharingPermissions = SharingPermissions(),
)

data class SharedDailySummary(
    val date: String,
    val consumedCalories: Int? = null,
    val calorieGoal: Int? = null,
    val proteinGrams: Int? = null,
    val proteinGoal: Int? = null,
    val carbsGrams: Int? = null,
    val carbsGoal: Int? = null,
    val fatGrams: Int? = null,
    val fatGoal: Int? = null,
    val steps: Int? = null,
    val activeCalories: Int? = null,
    val totalCaloriesBurned: Int? = null,
)

enum class GoalMetric { STEPS_DAILY, CALORIE_TARGET }

data class RelationshipGoal(
    val id: String,
    val relationshipId: String,
    val title: String,
    val metric: GoalMetric,
    val targetValue: Int,
    val myValue: Int? = null,
    val partnerValue: Int? = null,
    val myCompleted: Boolean = false,
    val partnerCompleted: Boolean = false,
    val active: Boolean = true,
)
