package com.nhom21.english_learning_app.domain.model

data class Question(
    val id: String,
    val order: Int,
    val prompt: String,
    val imageUrl: String?,
    val audioUrl: String?,
    val content: QuestionContent,
)

sealed interface QuestionContent {
    data class LearnCard(val vocabulary: Vocabulary) : QuestionContent
    data class MultipleChoice(val options: List<TextOption>) : QuestionContent
    data class PictureChoice(val options: List<PictureOption>) : QuestionContent
    data class Matching(val left: List<MatchingItem>, val right: List<MatchingItem>) : QuestionContent
    data class FillBlank(val sentence: String, val wordBank: List<String>?) : QuestionContent
    data class Translate(
        val sourceText: String,
        val direction: TranslationDirection,
        val tokens: List<TextOption>,
    ) : QuestionContent
    data class SentenceOrder(val tokens: List<TextOption>) : QuestionContent
    data class SentenceCorrection(val options: List<TextOption>) : QuestionContent
    data class SentenceCompletion(val sentence: String, val options: List<TextOption>) : QuestionContent
    data class Unknown(val originalType: String) : QuestionContent
}

data class TextOption(val id: String, val text: String)
data class PictureOption(val id: String, val imageUrl: String)
data class MatchingItem(val id: String, val text: String?, val imageUrl: String?)
enum class TranslationDirection { EN_TO_VI, VI_TO_EN, UNKNOWN }

sealed interface Answer {
    data class MultipleChoice(val optionId: String) : Answer
    data class PictureChoice(val optionId: String) : Answer
    data class MatchingCheck(val pair: MatchingPair) : Answer
    data class Matching(val pairs: List<MatchingPair>, val attempts: List<MatchingPair> = emptyList()) : Answer
    data class FillBlank(val text: String) : Answer
    data class Translate(val tokenIds: List<String>) : Answer
    data class TranslateText(val text: String) : Answer
    data class SentenceOrder(val tokenIds: List<String>) : Answer
    data class SentenceCorrection(val optionId: String) : Answer
    data class SentenceCompletion(val optionId: String) : Answer
}

data class MatchingPair(val leftId: String, val rightId: String)
data class MatchingPairResult(val leftId: String, val isCorrect: Boolean)

data class QuestionResult(
    val questionId: String,
    val isCorrect: Boolean,
    val correctAnswer: Answer?,
    val explanation: String?,
    val pairResults: List<MatchingPairResult>? = null,
)
