package com.caloly.app.domain.auth

import kotlinx.coroutines.flow.Flow

data class CalolyUser(
    val id: String,
    val email: String?,
    val displayName: String?,
    val username: String?,
    val avatarUrl: String? = null,
)

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: CalolyUser) : AuthState
}

interface AuthRepository {
    val authState: Flow<AuthState>
    suspend fun sendEmailOtp(email: String, createUser: Boolean = false)
    suspend fun verifyEmailOtp(email: String, token: String, isSignup: Boolean = false)
    suspend fun signUp(email: String, password: String, displayName: String, username: String)
    suspend fun signIn(email: String, password: String)
    suspend fun signInWithGoogle()
    suspend fun sendPasswordReset(email: String)
    suspend fun changePassword(newPassword: String)
    suspend fun updateProfile(displayName: String, username: String)
    suspend fun uploadAvatar(bytes: ByteArray, contentType: String = "image/jpeg")
    suspend fun signOut()
}
