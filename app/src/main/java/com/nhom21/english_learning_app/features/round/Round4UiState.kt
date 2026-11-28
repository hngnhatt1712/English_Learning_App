package com.nhom21.english_learning_app.features.round

import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.domain.model.Answer
import com.nhom21.english_learning_app.domain.model.QuestionContent
import com.nhom21.english_learning_app.domain.model.QuestionResult
import com.nhom21.english_learning_app.domain.model.RoundResult
import com.nhom21.english_learning_app.domain.model.TextOption

internal sealed interface Round4UiState : RoundUiState {
    data object Loading : Round4UiState
    data class Message(val reason: RoundMessage) : Round4UiState
    data class Submitting(val error: ApiErrorType? = null) : Round4UiState
    data class Completed(val result: RoundResult, val resultId: Long) : Round4UiState

    data class Question(
        val question: com.nhom21.english_learning_app.domain.model.Question,
        val number: Int,
        val total: Int,
        val keyboard: Boolean = question.content is QuestionContent.FillBlank && question.content.wordBank.isNullOrEmpty(),
        val draft: String = "",
        val selectedIds: List<String> = emptyList(),
        val checking: Boolean = false,
        val feedback: QuestionResult? = null,
        val error: ApiErrorType? = null,
    ) : Round4UiState {
        val tokens: List<TextOption> get() = when (val content = question.content) {
            is QuestionContent.FillBlank -> content.wordBank.orEmpty().mapIndexed { index, word ->
                TextOption(index.toString(), word)
            }
            is QuestionContent.Translate -> content.tokens
            is QuestionContent.SentenceOrder -> content.tokens
            else -> emptyList()
        }

        fun answer(): Answer? = when (question.content) {
            is QuestionContent.FillBlank -> {
                val text = if (keyboard) draft.trim() else tokens.find { it.id == selectedIds.singleOrNull() }?.text
                text?.takeIf { it.isNotBlank() }?.let { Answer.FillBlank(it) }
            }
            is QuestionContent.Translate -> if (keyboard) {
                draft.trim().takeIf { it.isNotBlank() }?.let { Answer.TranslateText(it) }
            } else {
                selectedIds.takeIf { it.isNotEmpty() }?.let { Answer.Translate(it) }
            }
            is QuestionContent.SentenceOrder -> selectedIds
                .takeIf { it.isNotEmpty() && it.size == tokens.size }
                ?.let { Answer.SentenceOrder(it) }
            else -> null
        }
    }
}
