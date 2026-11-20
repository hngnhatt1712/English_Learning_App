package com.nhom21.english_learning_app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class VocabularyStatusDto {
    @SerialName("new") NEW,
    @SerialName("skipped") SKIPPED,
    @SerialName("in_flashcard") IN_FLASHCARD,
    @SerialName("learning") LEARNING,
    @SerialName("review") REVIEW,
    @SerialName("mastered") MASTERED,
    UNKNOWN,
}

@Serializable
data class VocabularyDto(
    val id: String,
    val topicId: String,
    val levelId: String,
    val word: String,
    val meaning: String,
    val partOfSpeech: String,
    val phonetic: String,
    val example: String?,
    val imageUrl: String?,
    val audioUrl: String,
    val status: VocabularyStatusDto = VocabularyStatusDto.UNKNOWN,
    val isInFlashcard: Boolean,
)

@Serializable
enum class VocabularyActionDto {
    @SerialName("skip") SKIP,
    @SerialName("flashcard") FLASHCARD,
}

@Serializable
data class VocabularyActionRequest(
    val vocabularyId: String,
    val action: VocabularyActionDto,
)

@Serializable
data class VocabularyActionResultDto(
    val vocabularyId: String,
    val status: VocabularyStatusDto = VocabularyStatusDto.UNKNOWN,
    val isInFlashcard: Boolean,
)
