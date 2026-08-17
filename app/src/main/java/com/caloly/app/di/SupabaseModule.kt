package com.caloly.app.di

import com.caloly.app.BuildConfig
import com.caloly.app.data.auth.SupabaseAuthRepository
import com.caloly.app.domain.auth.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.ExternalAuthAction
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseClientModule {
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        require(BuildConfig.SUPABASE_URL.isNotBlank()) {
            "SUPABASE_URL tanımlı değil. gradle.properties içine proje URL'sini ekleyin."
        }
        require(BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
            "SUPABASE_PUBLISHABLE_KEY tanımlı değil. gradle.properties içine publishable key ekleyin."
        }
        return createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        ) {
            install(Postgrest)
            install(Storage)
            install(Auth) {
                scheme = "caloly"
                host = "auth"
                flowType = FlowType.PKCE
                defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindingModule {
    @Binds
    abstract fun bindAuthRepository(impl: SupabaseAuthRepository): AuthRepository
}
