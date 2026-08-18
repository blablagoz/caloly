package com.caloly.app.di

import com.caloly.app.data.remote.OpenFoodFactsApi
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton
import java.util.Locale
import java.util.concurrent.TimeUnit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Caloly/0.9.3 (https://github.com/blablagoz/caloly)")
                .header("Accept-Language", Locale.getDefault().toLanguageTag())
                .build()
            chain.proceed(request)
        }
        .build()

    @Provides
    @Singleton
    fun provideOpenFoodFactsApi(client: OkHttpClient, gson: Gson): OpenFoodFactsApi = Retrofit.Builder()
        .baseUrl("https://tr.openfoodfacts.org/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(OpenFoodFactsApi::class.java)
}
