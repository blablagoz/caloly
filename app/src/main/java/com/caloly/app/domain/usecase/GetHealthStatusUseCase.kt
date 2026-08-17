package com.caloly.app.domain.usecase

import com.caloly.app.domain.repository.HealthRepository
import javax.inject.Inject

class GetHealthStatusUseCase @Inject constructor(
    private val repository: HealthRepository,
) {
    val requiredPermissions: Set<String> get() = repository.requiredPermissions
    fun availability() = repository.availability()
    suspend fun hasAllPermissions() = repository.hasAllPermissions()
    suspend fun readToday() = repository.readToday()
}
