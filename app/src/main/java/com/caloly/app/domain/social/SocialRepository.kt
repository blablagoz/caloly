package com.caloly.app.domain.social

import com.caloly.app.domain.model.DailySummary

interface SocialRepository {
    suspend fun searchUsers(query: String): List<SocialProfile>
    suspend fun sendFollowRequest(targetUserId: String, relationshipType: String = "FRIEND")
    suspend fun incomingRequests(): List<IncomingFollowRequest>
    suspend fun respondToRequest(requestId: String, accept: Boolean)
    suspend fun connections(): List<SocialConnection>
    suspend fun updateSharing(relationshipId: String, permissions: SharingPermissions)
    suspend fun sharedToday(userId: String, date: String): SharedDailySummary?
    suspend fun goals(relationshipId: String, date: String): List<RelationshipGoal>
    suspend fun createGoal(relationshipId: String, metric: GoalMetric, targetValue: Int)
    suspend fun syncOwnDailySummary(date: String, summary: DailySummary)
}
