package com.nhom21.english_learning_app.domain.usecase

import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.repository.RoundRepository
import com.nhom21.english_learning_app.domain.model.RoundDetail
import javax.inject.Inject

class GetRoundUseCase @Inject constructor(private val repository: RoundRepository) {
    suspend operator fun invoke(roundId: String): ApiResult<RoundDetail> =
        repository.getRound(roundId)
}
