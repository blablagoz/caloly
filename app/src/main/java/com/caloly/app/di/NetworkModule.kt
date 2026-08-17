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

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Caloly/0.9.1 (https://github.com/blablagoz/caloly)")
                .header("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.7")
                .build()
            chain.proceed(request)
        }
        .build()

    @Provides
    @Singleton
    fun provideOpenFoodFactsApi(client: OkHttpClient): OpenFoodFactsApi = Retrofit.Builder()
        .baseUrl("https://world.openfoodfacts.org/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(Gson()))
        .build()
        .create(OpenFoodFactsApi::class.java)
}
