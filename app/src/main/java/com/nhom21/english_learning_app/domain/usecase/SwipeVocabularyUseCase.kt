package com.nhom21.english_learning_app.domain.usecase

import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.repository.RoundRepository
import com.nhom21.english_learning_app.domain.model.VocabularyAction
import com.nhom21.english_learning_app.domain.model.VocabularyActionResult
import javax.inject.Inject

class SwipeVocabularyUseCase @Inject constructor(private val repository: RoundRepository) {
    suspend operator fun invoke(
        roundId: String,
        vocabularyId: String,
        action: VocabularyAction,
    ): ApiResult<VocabularyActionResult> = repository.vocabularyAction(roundId, vocabularyId, action)
}
