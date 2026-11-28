package com.nhom21.english_learning_app.features.round

import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.domain.model.MatchingItem
import com.nhom21.english_learning_app.domain.model.MatchingPair
import com.nhom21.english_learning_app.domain.model.RoundResult

internal sealed interface Round3UiState : RoundUiState {
    data object Exited : Round3UiState
    data class Submitting(val error: ApiErrorType? = null) : Round3UiState
    data class Completed(val result: RoundResult, val resultId: Long) : Round3UiState

    data class Question(
        val question: MatchingQuestion,
        val number: Int,
        val questionCount: Int,
        val totalQuestions: Int,
        val savedStars: Int,
        val selectedLeftId: String? = null,
        val selectedRightId: String? = null,
        val pendingPair: MatchingPair? = null,
        val feedback: MatchingFeedback? = null,
        val checkError: ApiErrorType? = null,
        val pairs: List<MatchingPair> = emptyList(),
        val attempts: List<MatchingPair> = emptyList(),
    ) : Round3UiState {
        val canContinue: Boolean get() = pairs.size == question.left.size && pendingPair == null
    }
}

internal data class MatchingQuestion(
    val id: String,
    val prompt: String,
    val left: List<MatchingItem>,
    val right: List<MatchingItem>,
)

internal data class MatchingFeedback(val id: Long, val pair: MatchingPair, val isCorrect: Boolean)
