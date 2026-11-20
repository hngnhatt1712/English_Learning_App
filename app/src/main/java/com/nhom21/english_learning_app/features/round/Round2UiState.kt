package com.nhom21.english_learning_app.features.round

import androidx.annotation.DrawableRes

internal sealed interface Round2UiState {
    data object Loading : Round2UiState

    data class Question(
        val question: Round2Question,
        val number: Int,
        val total: Int,
        val selectedOptionId: String? = null,
        val feedback: Round2Feedback? = null
    ) : Round2UiState

    data class Completed(
        val correctCount: Int,
        val total: Int,
        val stars: Int,
        val passed: Boolean
    ) : Round2UiState
}

internal enum class Round2Variant { Meaning, Word, Picture }

internal data class Round2Question(
    val id: String,
    val variant: Round2Variant,
    val prompt: String,
    val options: List<Round2Option>,
    @param:DrawableRes val illustrationRes: Int? = null
)

internal data class Round2Option(
    val id: String,
    val label: String? = null,
    @param:DrawableRes val imageRes: Int? = null
)

internal data class Round2Feedback(val isCorrect: Boolean, val correctOptionId: String?)
