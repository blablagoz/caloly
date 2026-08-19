package com.caloly.app.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import com.caloly.app.BuildConfig
import com.caloly.app.domain.model.AiMealAnalysis
import com.caloly.app.domain.model.DetectedFood
import com.caloly.app.domain.model.NutritionSource
import com.caloly.app.domain.repository.AiMealRepository
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class GeminiMealRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val gson: Gson,
    private val supabase: SupabaseClient,
) : AiMealRepository {
    private val aiClient = client.newBuilder()
        .callTimeout(55, TimeUnit.SECONDS)
        .readTimeout(50, TimeUnit.SECONDS)
        .build()

    override suspend fun analyzePhoto(contentUri: String): Result<AiMealAnalysis> = runCatching {
        val encodedPhoto = withContext(Dispatchers.IO) { preparePhoto(contentUri) }
        requestAnalysis(
            AiMealRequest(
                mode = "photo",
                imageBase64 = encodedPhoto,
                mimeType = "image/jpeg",
            )
        )
    }

    override suspend fun analyzeDescription(description: String): Result<AiMealAnalysis> = runCatching {
        val clean = description.trim()
        require(clean.length >= 3) { "Yemeği biraz daha ayrıntılı yaz." }
        requestAnalysis(AiMealRequest(mode = "text", description = clean.take(600)))
    }

    private suspend fun requestAnalysis(payload: AiMealRequest): AiMealAnalysis = withContext(Dispatchers.IO) {
        check(BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
            "Yapay zekâ servisi henüz hazır değil."
        }
        val accessToken = supabase.auth.currentSessionOrNull()?.accessToken
            ?: error("Yapay zekâyı kullanmak için yeniden giriş yap.")
        val request = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL}/functions/v1/analyze-meal")
            .header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .post(gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        aiClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException(aiErrorMessage(response.code, body))
            val dto = runCatching { gson.fromJson(body, AiMealResponse::class.java) }
                .getOrElse { throw IOException("Yapay zekâ yanıtı okunamadı. Tekrar deneyebilirsin.") }
            dto.toDomain()
        }
    }

    private fun preparePhoto(contentUri: String): String {
        val uri = Uri.parse(contentUri)
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            val scale = minOf(1.0, MAX_IMAGE_EDGE.toDouble() / maxOf(width, height))
            if (scale < 1.0) {
                decoder.setTargetSize((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) { "Fotoğraf hazırlanamadı." }
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }
    }

    private fun aiErrorMessage(status: Int, body: String): String {
        val serverMessage = runCatching { gson.fromJson(body, AiErrorResponse::class.java).message }.getOrNull()
        return when (status) {
            401 -> "Oturumun sona ermiş. Yeniden giriş yap."
            413 -> "Fotoğraf çok büyük. Yeniden çekmeyi dene."
            422 -> serverMessage ?: "Yemek bulunamadı"
            429 -> serverMessage ?: "Saatlik 3 fotoğraftan tanıma hakkını kullandın. Daha sonra tekrar dene."
            500, 502, 503, 504 -> "Yapay zekâ şu anda yanıt veremiyor. Biraz sonra tekrar dene."
            else -> "Yemek analizi tamamlanamadı. Tekrar deneyebilirsin."
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MAX_IMAGE_EDGE = 1280
        const val JPEG_QUALITY = 82
    }
}

internal data class AiMealRequest(
    val mode: String,
    val imageBase64: String? = null,
    val mimeType: String? = null,
    val description: String? = null,
)

internal data class AiMealResponse(
    val confirmationQuestion: String? = null,
    val foods: List<DetectedFoodResponse>? = null,
    val overallConfidence: Double? = null,
    val analysisNotes: String? = null,
    val remainingPhotoScans: Int? = null,
) {
    fun toDomain(): AiMealAnalysis {
        val validFoods = foods.orEmpty().mapNotNull(DetectedFoodResponse::toDomain)
        require(validFoods.isNotEmpty()) { "Yemek bulunamadı" }
        val fallbackNames = validFoods.joinToString(" ve ") { it.name }
        return AiMealAnalysis(
            confirmationQuestion = confirmationQuestion?.trim()?.takeIf(String::isNotBlank)
                ?: "Önündeki yemek $fallbackNames mi?",
            foods = validFoods,
            overallConfidence = overallConfidence?.coerceIn(0.0, 1.0)
                ?: validFoods.map(DetectedFood::confidence).average(),
            analysisNotes = analysisNotes?.trim()?.takeIf(String::isNotBlank),
            remainingPhotoScans = remainingPhotoScans?.coerceIn(0, 3),
        )
    }
}

internal data class DetectedFoodResponse(
    val name: String? = null,
    val brand: String? = null,
    val estimatedGrams: Double? = null,
    val gramsMin: Double? = null,
    val gramsMax: Double? = null,
    val calories: Double? = null,
    val caloriesMin: Double? = null,
    val caloriesMax: Double? = null,
    val proteinGrams: Double? = null,
    val carbsGrams: Double? = null,
    val fatGrams: Double? = null,
    val confidence: Double? = null,
) {
    fun toDomain(): DetectedFood? {
        val cleanName = name?.trim()?.takeIf(String::isNotBlank) ?: return null
        val grams = estimatedGrams?.coerceIn(1.0, 3000.0) ?: return null
        val kcal = calories?.coerceIn(0.0, 5000.0) ?: return null
        return DetectedFood(
            name = cleanName,
            brand = brand?.trim()?.takeIf(String::isNotBlank),
            estimatedGrams = grams,
            gramsMin = (gramsMin ?: grams).coerceIn(1.0, grams),
            gramsMax = (gramsMax ?: grams).coerceIn(grams, 3000.0),
            calories = kcal,
            caloriesMin = (caloriesMin ?: kcal).coerceIn(0.0, kcal),
            caloriesMax = (caloriesMax ?: kcal).coerceIn(kcal, 5000.0),
            proteinGrams = (proteinGrams ?: 0.0).coerceIn(0.0, 500.0),
            carbsGrams = (carbsGrams ?: 0.0).coerceIn(0.0, 1000.0),
            fatGrams = (fatGrams ?: 0.0).coerceIn(0.0, 500.0),
            confidence = (confidence ?: 0.5).coerceIn(0.0, 1.0),
            nutritionSource = NutritionSource.AI_ESTIMATE,
        )
    }
}

private data class AiErrorResponse(val message: String? = null)
