package com.nhom21.english_learning_app.core.di

import com.nhom21.english_learning_app.data.repository.AuthRepository
import com.nhom21.english_learning_app.data.repository.AuthRepositoryImpl
import com.nhom21.english_learning_app.data.repository.HomeRepository
import com.nhom21.english_learning_app.data.repository.HomeRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module binding repository abstractions to their implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindHomeRepository(
        impl: HomeRepositoryImpl
    ): HomeRepository
}
