package com.nhom21.english_learning_app.domain.usecase

import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.repository.RoundRepository
import com.nhom21.english_learning_app.domain.model.Answer
import com.nhom21.english_learning_app.domain.model.QuestionResult
import javax.inject.Inject

class CheckAnswerUseCase @Inject constructor(private val repository: RoundRepository) {
    suspend operator fun invoke(questionId: String, answer: Answer): ApiResult<QuestionResult> =
        repository.checkAnswer(questionId, answer)
}
