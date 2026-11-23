package com.nhom21.english_learning_app.domain.usecase

import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.repository.ProfileRepository
import com.nhom21.english_learning_app.domain.model.Profile
import javax.inject.Inject

class SelectLevelUseCase @Inject constructor(private val repository: ProfileRepository) {
    suspend operator fun invoke(levelId: String): ApiResult<Profile> = repository.selectLevel(levelId)
}
