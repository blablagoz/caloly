package com.caloly.app.data.social

import com.caloly.app.domain.model.DailySummary
import com.caloly.app.domain.social.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable private data class SearchParams(val search_text: String)
@Serializable private data class SendRequestParams(val target_user_id: String, val relation_type: String)
@Serializable private data class RespondParams(val request_id: String, val accept_request: Boolean)
@Serializable private data class SharedSummaryParams(val target_user_id: String, val summary_date: String)
@Serializable private data class SharingParams(
    val relationship_id: String,
    val share_calories: Boolean,
    val share_macros: Boolean,
    val share_steps: Boolean,
    val share_activity: Boolean,
    val share_weight: Boolean,
    val share_food_details: Boolean,
    val share_history: Boolean,
)

@Serializable private data class ProfileDto(
    val id: String,
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("relationship_status") val relationshipStatus: String? = null,
)

@Serializable private data class RequestDto(
    @SerialName("request_id") val requestId: String,
    @SerialName("requester_id") val requesterId: String,
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("relationship_type") val relationshipType: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable private data class ConnectionDto(
    @SerialName("relationship_id") val relationshipId: String,
    @SerialName("other_user_id") val otherUserId: String,
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("relationship_type") val relationshipType: String,
    @SerialName("share_calories") val calories: Boolean = true,
    @SerialName("share_macros") val macros: Boolean = true,
    @SerialName("share_steps") val steps: Boolean = true,
    @SerialName("share_activity") val activity: Boolean = true,
    @SerialName("share_weight") val weight: Boolean = false,
    @SerialName("share_food_details") val foodDetails: Boolean = false,
    @SerialName("share_history") val history: Boolean = false,
)

@Serializable private data class SharedSummaryDto(
    val date: String,
    @SerialName("consumed_calories") val consumedCalories: Int? = null,
    @SerialName("calorie_goal") val calorieGoal: Int? = null,
    @SerialName("protein_grams") val proteinGrams: Int? = null,
    @SerialName("protein_goal") val proteinGoal: Int? = null,
    @SerialName("carbs_grams") val carbsGrams: Int? = null,
    @SerialName("carbs_goal") val carbsGoal: Int? = null,
    @SerialName("fat_grams") val fatGrams: Int? = null,
    @SerialName("fat_goal") val fatGoal: Int? = null,
    val steps: Int? = null,
    @SerialName("active_calories") val activeCalories: Int? = null,
    @SerialName("total_calories_burned") val totalCaloriesBurned: Int? = null,
)


@Serializable private data class GoalListParams(val p_relationship_id: String, val p_goal_date: String)
@Serializable private data class CreateGoalParams(val p_relationship_id: String, val p_goal_metric: String, val p_target_value: Int)

@Serializable private data class GoalDto(
    val id: String,
    @SerialName("relationship_id") val relationshipId: String,
    val title: String,
    val metric: String,
    @SerialName("target_value") val targetValue: Int,
    @SerialName("my_value") val myValue: Int? = null,
    @SerialName("partner_value") val partnerValue: Int? = null,
    @SerialName("my_completed") val myCompleted: Boolean = false,
    @SerialName("partner_completed") val partnerCompleted: Boolean = false,
    val active: Boolean = true,
)

@Serializable private data class DailySummaryRow(
    @SerialName("user_id") val userId: String,
    val date: String,
    @SerialName("consumed_calories") val consumedCalories: Int,
    @SerialName("calorie_goal") val calorieGoal: Int,
    @SerialName("protein_grams") val proteinGrams: Int,
    @SerialName("protein_goal") val proteinGoal: Int,
    @SerialName("carbs_grams") val carbsGrams: Int,
    @SerialName("carbs_goal") val carbsGoal: Int,
    @SerialName("fat_grams") val fatGrams: Int,
    @SerialName("fat_goal") val fatGoal: Int,
    val steps: Int,
    @SerialName("active_calories") val activeCalories: Int,
    @SerialName("total_calories_burned") val totalCaloriesBurned: Int,
)

@Singleton
class SupabaseSocialRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : SocialRepository {
    override suspend fun searchUsers(query: String): List<SocialProfile> {
        if (query.trim().length < 2) return emptyList()
        return supabase.postgrest.rpc("search_caloly_profiles", SearchParams(query.trim()))
            .decodeList<ProfileDto>()
            .map { SocialProfile(it.id, it.username, it.displayName, it.avatarUrl, it.relationshipStatus) }
    }

    override suspend fun sendFollowRequest(targetUserId: String, relationshipType: String) {
        supabase.postgrest.rpc("send_caloly_follow_request", SendRequestParams(targetUserId, relationshipType))
    }

    override suspend fun incomingRequests(): List<IncomingFollowRequest> =
        supabase.postgrest.rpc("get_caloly_follow_requests")
            .decodeList<RequestDto>()
            .map { IncomingFollowRequest(it.requestId, SocialProfile(it.requesterId, it.username, it.displayName, it.avatarUrl), it.relationshipType, it.createdAt) }

    override suspend fun respondToRequest(requestId: String, accept: Boolean) {
        supabase.postgrest.rpc("respond_caloly_follow_request", RespondParams(requestId, accept))
    }

    override suspend fun connections(): List<SocialConnection> =
        supabase.postgrest.rpc("get_caloly_connections")
            .decodeList<ConnectionDto>()
            .map {
                SocialConnection(
                    relationshipId = it.relationshipId,
                    profile = SocialProfile(it.otherUserId, it.username, it.displayName, it.avatarUrl),
                    relationshipType = it.relationshipType,
                    mySharing = SharingPermissions(it.calories, it.macros, it.steps, it.activity, it.weight, it.foodDetails, it.history),
                )
            }

    override suspend fun updateSharing(relationshipId: String, permissions: SharingPermissions) {
        supabase.postgrest.rpc(
            "update_caloly_sharing",
            SharingParams(relationshipId, permissions.calories, permissions.macros, permissions.steps, permissions.activity, permissions.weight, permissions.foodDetails, permissions.history),
        )
    }

    override suspend fun sharedToday(userId: String, date: String): SharedDailySummary? =
        supabase.postgrest.rpc("get_caloly_shared_daily_summary", SharedSummaryParams(userId, date))
            .decodeList<SharedSummaryDto>()
            .firstOrNull()
            ?.let { SharedDailySummary(it.date, it.consumedCalories, it.calorieGoal, it.proteinGrams, it.proteinGoal, it.carbsGrams, it.carbsGoal, it.fatGrams, it.fatGoal, it.steps, it.activeCalories, it.totalCaloriesBurned) }

    override suspend fun goals(relationshipId: String, date: String): List<RelationshipGoal> =
        supabase.postgrest.rpc("get_caloly_relationship_goals", GoalListParams(relationshipId, date))
            .decodeList<GoalDto>()
            .map { dto ->
                RelationshipGoal(
                    id = dto.id,
                    relationshipId = dto.relationshipId,
                    title = dto.title,
                    metric = GoalMetric.valueOf(dto.metric),
                    targetValue = dto.targetValue,
                    myValue = dto.myValue,
                    partnerValue = dto.partnerValue,
                    myCompleted = dto.myCompleted,
                    partnerCompleted = dto.partnerCompleted,
                    active = dto.active,
                )
            }

    override suspend fun createGoal(relationshipId: String, metric: GoalMetric, targetValue: Int) {
        supabase.postgrest.rpc("create_caloly_relationship_goal", CreateGoalParams(relationshipId, metric.name, targetValue))
    }

    override suspend fun syncOwnDailySummary(date: String, summary: DailySummary) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        supabase.from("daily_summaries").upsert(
            DailySummaryRow(userId, date, summary.consumedCalories, summary.calorieGoal, summary.proteinGrams, summary.proteinGoal, summary.carbsGrams, summary.carbsGoal, summary.fatGrams, summary.fatGoal, summary.steps, summary.activeCalories, summary.totalCaloriesBurned),
        ) {
            onConflict = "user_id,date"
        }
    }
}
