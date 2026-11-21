package com.nhom21.english_learning_app.features.round

import androidx.annotation.DrawableRes
import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.domain.model.RoundUnlocks
import com.nhom21.english_learning_app.domain.model.Streak

internal sealed interface Round2UiState : RoundUiState {
    data class Message(val reason: RoundMessage) : Round2UiState
    data class Submitting(val error: ApiErrorType? = null) : Round2UiState
    data object Loading : Round2UiState

    data class Question(
        val question: Round2Question,
        val number: Int,
        val total: Int,
        val selectedOptionId: String? = null,
        val feedback: Round2Feedback? = null,
        val checking: Boolean = false,
        val checkError: ApiErrorType? = null,
        val networkFailures: Int = 0,
    ) : Round2UiState

    data class Completed(
        val correctCount: Int,
        val total: Int,
        val stars: Int,
        val passed: Boolean,
        val scorePercent: Double? = null,
        val unlocks: RoundUnlocks? = null,
        val streak: Streak? = null,
    ) : Round2UiState
}

internal enum class RoundMessage { Empty, Unsupported, UpdateRequired, Locked, LoadFailed, InvalidContent }
internal enum class Round2Variant { Meaning, Word, Text, Picture }

internal data class Round2Question(
    val id: String,
    val variant: Round2Variant,
    val prompt: String,
    val options: List<Round2Option>,
    @param:DrawableRes val illustrationRes: Int? = null,
    val imageUrl: String? = null,
    val audioUrl: String? = null
)

internal data class Round2Option(
    val id: String,
    val label: String? = null,
    @param:DrawableRes val imageRes: Int? = null,
    val imageUrl: String? = null
)

internal data class Round2Feedback(val isCorrect: Boolean, val correctOptionId: String?)
