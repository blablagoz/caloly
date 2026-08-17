package com.caloly.app.di

import com.caloly.app.data.repository.NutritionRepositoryImpl
import com.caloly.app.data.social.SupabaseSocialRepository
import com.caloly.app.domain.social.SocialRepository
import com.caloly.app.domain.repository.NutritionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindNutritionRepository(impl: NutritionRepositoryImpl): NutritionRepository

    @Binds
    @Singleton
    abstract fun bindSocialRepository(impl: SupabaseSocialRepository): SocialRepository
}
