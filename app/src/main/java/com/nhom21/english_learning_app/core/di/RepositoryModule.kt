package com.nhom21.english_learning_app.core.di

import com.nhom21.english_learning_app.data.repository.AuthRepository
import com.nhom21.english_learning_app.data.repository.AuthRepositoryImpl
import com.nhom21.english_learning_app.data.repository.HomeRepository
import com.nhom21.english_learning_app.data.repository.HomeRepositoryImpl
import com.nhom21.english_learning_app.data.repository.TopicRepository
import com.nhom21.english_learning_app.data.repository.TopicRepositoryImpl
import com.nhom21.english_learning_app.data.repository.LevelRepository
import com.nhom21.english_learning_app.data.repository.LevelRepositoryImpl
import com.nhom21.english_learning_app.data.repository.ProfileRepository
import com.nhom21.english_learning_app.data.repository.ProfileRepositoryImpl
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
    abstract fun bindLevelRepository(impl: LevelRepositoryImpl): LevelRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

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

    @Binds
    @Singleton
    abstract fun bindTopicRepository(
        impl: TopicRepositoryImpl
    ): TopicRepository
}
