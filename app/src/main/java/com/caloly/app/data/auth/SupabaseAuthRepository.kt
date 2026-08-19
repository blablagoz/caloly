package com.caloly.app.data.auth

import com.caloly.app.BuildConfig
import com.caloly.app.domain.auth.AuthRepository
import com.caloly.app.domain.auth.AuthState
import com.caloly.app.domain.auth.CalolyUser
import com.caloly.app.domain.auth.isValidUsername
import com.caloly.app.domain.auth.normalizeUsername
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val httpClient: OkHttpClient,
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
                put("username", normalizeUsername(username))
            }
        }
    }

    override suspend fun signIn(email: String, password: String) {
        val resolvedEmail = if ('@' in email) email.trim() else resolveUsername(email.trim(), password)
        supabase.auth.signInWith(Email) {
            this.email = resolvedEmail
            this.password = password
        }
    }

    override suspend fun signInWithGoogle() {
        ensureGoogleProviderEnabled()
        supabase.auth.signInWith(Google, redirectUrl = "caloly://auth")
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
                put("username", normalizeUsername(username))
            }
        }
    }

    override suspend fun updateHealthProfile(
        birthDate: String,
        heightCm: Int,
        weightKg: Double,
        gender: String,
    ) {
        supabase.auth.updateUser {
            data {
                put("birth_date", birthDate)
                put("height_cm", heightCm)
                put("weight_kg", weightKg)
                put("gender", gender)
                put("onboarding_completed", true)
            }
        }
    }

    override suspend fun skipHealthProfile() {
        supabase.auth.updateUser { data { put("onboarding_completed", true) } }
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

    private suspend fun resolveUsername(username: String, password: String): String = withContext(Dispatchers.IO) {
        require(isValidUsername(username)) { "Geçerli bir e-posta veya kullanıcı adı girin." }
        check(BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) { "Giriş servisi yapılandırılmamış." }
        val payload = buildJsonObject {
            put("identifier", normalizeUsername(username))
            put("password", password)
        }.toString()
        val request = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL}/functions/v1/login-identifier")
            .header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .header("Authorization", "Bearer ${BuildConfig.SUPABASE_PUBLISHABLE_KEY}")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("E-posta/kullanıcı adı veya şifre hatalı.")
            Json.parseToJsonElement(body).jsonObject["email"]?.jsonPrimitive?.content
                ?: error("Giriş servisi geçersiz yanıt verdi.")
        }
    }

    private suspend fun ensureGoogleProviderEnabled() = withContext(Dispatchers.IO) {
        check(BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
            "Google ile giriş şu anda kullanılamıyor."
        }
        val request = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL}/auth/v1/settings")
            .header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .get()
            .build()
        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val json = Json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                json["external"]?.jsonObject?.get("google")?.jsonPrimitive?.booleanOrNull == true
            }
        }.getOrDefault(false).let { enabled ->
            check(enabled) { "Google ile giriş şu anda kullanılamıyor." }
        }
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
        birthDate = metadata?.get("birth_date")?.jsonPrimitive?.content,
        heightCm = metadata?.get("height_cm")?.jsonPrimitive?.intOrNull,
        weightKg = metadata?.get("weight_kg")?.jsonPrimitive?.doubleOrNull,
        targetWeightKg = metadata?.get("target_weight_kg")?.jsonPrimitive?.doubleOrNull,
        gender = metadata?.get("gender")?.jsonPrimitive?.content,
        activityLevel = metadata?.get("activity_level")?.jsonPrimitive?.content,
        nutritionGoal = metadata?.get("nutrition_goal")?.jsonPrimitive?.content,
        onboardingCompleted = metadata?.get("onboarding_completed")?.jsonPrimitive?.booleanOrNull == true,
    )
}
