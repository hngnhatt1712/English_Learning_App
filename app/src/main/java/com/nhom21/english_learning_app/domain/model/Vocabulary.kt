package com.nhom21.english_learning_app.domain.model

data class Vocabulary(
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
    val status: VocabularyStatus,
    val isInFlashcard: Boolean,
)

enum class VocabularyStatus { NEW, SKIPPED, IN_FLASHCARD, LEARNING, REVIEW, MASTERED, UNKNOWN }

enum class VocabularyAction { SKIP, FLASHCARD }

data class VocabularyActionResult(
    val vocabularyId: String,
    val status: VocabularyStatus,
    val isInFlashcard: Boolean,
)
