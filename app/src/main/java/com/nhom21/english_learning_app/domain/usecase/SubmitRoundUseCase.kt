package com.nhom21.english_learning_app.domain.usecase

import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.repository.RoundRepository
import com.nhom21.english_learning_app.domain.model.RoundAnswer
import com.nhom21.english_learning_app.domain.model.RoundResult
import javax.inject.Inject

class SubmitRoundUseCase @Inject constructor(private val repository: RoundRepository) {
    suspend operator fun invoke(roundId: String, answers: List<RoundAnswer>): ApiResult<RoundResult> =
        repository.submitRound(roundId, answers)
}
