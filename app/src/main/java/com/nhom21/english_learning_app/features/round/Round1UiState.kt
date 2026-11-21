package com.nhom21.english_learning_app.features.round

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.core.ui.components.VocabularyAudioState
import com.nhom21.english_learning_app.core.ui.components.VocabularyCardContent

data class Round1Word(
    val id: String,
    val word: String,
    val partOfSpeech: String,
    val phonetic: String,
    val meaning: String,
    val example: String? = null,
    val imageUrl: String? = null,
    val isInFlashcards: Boolean = false,
    val audioUrl: String? = null
)

enum class Round1Action { Skip, Add, KeepAdded }

data class Round1Feedback(
    val id: Long,
    @param:StringRes @get:StringRes val message: Int,
    val canUndo: Boolean = false
)

sealed interface RoundUiState

sealed interface Round1UiState : RoundUiState {
    data object Loading : Round1UiState
    data object Empty : Round1UiState
    data object Error : Round1UiState
    data object Locked : Round1UiState

    data class Learning(
        val word: Round1Word,
        val remainingCardsBehind: Int,
        val processedWords: Int,
        val totalWords: Int,
        val audio: VocabularyAudioState,
        val hasPendingActions: Boolean = false,
        val feedback: Round1Feedback? = null,
        val actionsEnabled: Boolean = true,
        @param:StringRes val syncError: Int? = null
    ) : Round1UiState

    data class Finishing(
        val totalWords: Int,
        val status: Round1FinishStatus = Round1FinishStatus.Ready,
        val feedback: Round1Feedback? = null,
        @param:StringRes val errorMessage: Int = R.string.round_error,
        val hasPendingActions: Boolean = false
    ) : Round1UiState

    data class Completed(
        val learnedWords: Int,
        val stars: Int,
        val addedWords: Int? = null,
        val skippedWords: Int? = null
    ) : Round1UiState
}

enum class Round1FinishStatus { Ready, Saving, Error }

@Composable
internal fun Round1Word.cardContent() = VocabularyCardContent(
    word, roundPartOfSpeechLabel(partOfSpeech)?.let { stringResource(it) },
    phonetic, meaning, example, isInFlashcards
)

@StringRes
internal fun roundPartOfSpeechLabel(partOfSpeech: String): Int? = when (partOfSpeech.lowercase()) {
    "noun" -> R.string.round_pos_noun
    "verb" -> R.string.round_pos_verb
    "adjective" -> R.string.round_pos_adjective
    "adverb" -> R.string.round_pos_adverb
    "pronoun" -> R.string.round_pos_pronoun
    "preposition" -> R.string.round_pos_preposition
    "conjunction" -> R.string.round_pos_conjunction
    "interjection" -> R.string.round_pos_interjection
    "phrase" -> R.string.round_pos_phrase
    else -> null
}
