package com.caloly.app.domain.repository

import com.caloly.app.domain.model.HealthConnectAvailability
import com.caloly.app.domain.model.HealthSummary

interface HealthRepository {
    val requiredPermissions: Set<String>
    fun availability(): HealthConnectAvailability
    suspend fun hasAllPermissions(): Boolean
    suspend fun readToday(): HealthSummary
}
