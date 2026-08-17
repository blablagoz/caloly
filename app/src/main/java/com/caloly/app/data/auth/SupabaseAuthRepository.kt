package com.caloly.app.data.auth

import com.caloly.app.domain.auth.AuthRepository
import com.caloly.app.domain.auth.AuthState
import com.caloly.app.domain.auth.CalolyUser
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.auth.OtpType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : AuthRepository {

    override val authState: Flow<AuthState> = supabase.auth.sessionStatus.map { status ->
        when (status) {
            SessionStatus.Initializing -> AuthState.Loading
            is SessionStatus.Authenticated -> status.session.user?.let { AuthState.SignedIn(it.toCalolyUser()) } ?: AuthState.SignedOut
            is SessionStatus.NotAuthenticated -> AuthState.SignedOut
            is SessionStatus.RefreshFailure -> AuthState.SignedOut
        }
    }

    override suspend fun sendEmailOtp(email: String, createUser: Boolean) {
        supabase.auth.signInWith(OTP, redirectUrl = "caloly://auth") {
            this.email = email.trim()
            this.createUser = createUser
        }
    }

    override suspend fun verifyEmailOtp(email: String, token: String, isSignup: Boolean) {
        supabase.auth.verifyEmailOtp(
            type = if (isSignup) OtpType.Email.SIGNUP else OtpType.Email.EMAIL,
            email = email.trim(),
            token = token.trim(),
        )
    }

    override suspend fun signUp(email: String, password: String, displayName: String, username: String) {
        supabase.auth.signUpWith(Email, redirectUrl = "caloly://auth") {
            this.email = email.trim()
            this.password = password
            data = buildJsonObject {
                put("display_name", displayName.trim())
                put("username", username.trim().lowercase())
            }
        }
    }

    override suspend fun signIn(email: String, password: String) {
        supabase.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    override suspend fun signInWithGoogle() {
        supabase.auth.signInWith(Google)
    }

    override suspend fun sendPasswordReset(email: String) {
        supabase.auth.resetPasswordForEmail(email.trim(), redirectUrl = "caloly://auth")
    }

    override suspend fun changePassword(newPassword: String) {
        supabase.auth.updateUser { password = newPassword }
    }

    override suspend fun updateProfile(displayName: String, username: String) {
        supabase.auth.updateUser {
            data {
                put("display_name", displayName.trim())
                put("username", username.trim().lowercase())
            }
        }
    }

    override suspend fun uploadAvatar(bytes: ByteArray, contentType: String) {
        require(bytes.isNotEmpty()) { "Fotoğraf okunamadı." }
        require(bytes.size <= 5 * 1024 * 1024) { "Profil fotoğrafı 5 MB'dan küçük olmalı." }
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Oturum bulunamadı")
        val extension = if (contentType.contains("png", ignoreCase = true)) "png" else "jpg"
        val path = "$userId/avatar.$extension"
        val bucket = supabase.storage.from("avatars")
        bucket.upload(path, bytes) { upsert = true }
        val url = bucket.publicUrl(path)
        supabase.auth.updateUser {
            data { put("avatar_url", url) }
        }
    }

    override suspend fun signOut() {
        supabase.auth.signOut()
    }
}

private fun io.github.jan.supabase.auth.user.UserInfo.toCalolyUser(): CalolyUser {
    val metadata = userMetadata
    return CalolyUser(
        id = id,
        email = email,
        displayName = metadata?.get("display_name")?.jsonPrimitive?.content,
        username = metadata?.get("username")?.jsonPrimitive?.content,
        avatarUrl = metadata?.get("avatar_url")?.jsonPrimitive?.content ?: metadata?.get("picture")?.jsonPrimitive?.content,
    )
}
