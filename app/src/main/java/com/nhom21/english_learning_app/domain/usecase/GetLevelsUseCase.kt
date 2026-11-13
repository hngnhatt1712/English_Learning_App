package com.nhom21.english_learning_app.domain.usecase

import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.repository.LevelRepository
import com.nhom21.english_learning_app.domain.model.Level
import javax.inject.Inject

class GetLevelsUseCase @Inject constructor(private val repository: LevelRepository) {
    suspend operator fun invoke(): ApiResult<List<Level>> = when (val result = repository.getLevels()) {
        is ApiResult.Success -> ApiResult.Success(result.data.sortedWith(compareBy<Level> { it.orderIndex }.thenBy { it.id }))
        is ApiResult.Failure -> result
    }
}
