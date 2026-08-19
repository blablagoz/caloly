package com.caloly.app.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.time.TimeRangeFilter
import com.caloly.app.domain.model.HealthConnectAvailability
import com.caloly.app.domain.model.HealthSummary
import com.caloly.app.domain.repository.HealthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class HealthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : HealthRepository {

    override val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
    )

    override fun availability(): HealthConnectAvailability = when (
        HealthConnectClient.getSdkStatus(context)
    ) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.UPDATE_REQUIRED
        else -> HealthConnectAvailability.UNAVAILABLE
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    override suspend fun hasAllPermissions(): Boolean {
        if (availability() != HealthConnectAvailability.AVAILABLE) return false
        val granted = client().permissionController.getGrantedPermissions()
        return granted.containsAll(requiredPermissions)
    }

    override suspend fun readToday(): HealthSummary {
        if (!hasAllPermissions()) return HealthSummary()

        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val end = java.time.Instant.now()

        val result = client().aggregate(
            AggregateRequest(
                metrics = setOf(
                    StepsRecord.COUNT_TOTAL,
                    ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                    BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL,
                    TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                ),
                timeRangeFilter = TimeRangeFilter.between(start, end),
            )
        )

        val activeEnergy = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
        val totalEnergy = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]
        val basalEnergy = result[BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL]
        return HealthSummary(
            steps = result[StepsRecord.COUNT_TOTAL] ?: 0L,
            activeCalories = (activeEnergy?.inKilocalories ?: 0.0).roundToInt(),
            totalCaloriesBurned = (totalEnergy?.inKilocalories ?: 0.0).roundToInt(),
            basalCalories = basalEnergy?.inKilocalories?.roundToInt(),
            hasActiveCaloriesData = activeEnergy != null,
            hasTotalCaloriesData = totalEnergy != null,
        )
    }
}
