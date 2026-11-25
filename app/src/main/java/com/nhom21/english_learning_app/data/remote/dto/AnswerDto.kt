package com.nhom21.english_learning_app.data.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface AnswerDto

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface CheckAnswerDto

@Serializable
@SerialName("multiple_choice")
data class MultipleChoiceAnswerDto(val optionId: String) : AnswerDto, CheckAnswerDto

@Serializable
@SerialName("picture_choice")
data class PictureChoiceAnswerDto(val optionId: String) : AnswerDto, CheckAnswerDto

@Serializable
@SerialName("matching")
data class MatchingAnswerDto(
    val pairs: List<MatchingPairDto>,
    val attempts: List<MatchingPairDto> = emptyList(),
) : AnswerDto

@Serializable
@SerialName("matching")
data class MatchingCheckAnswerDto(val leftId: String, val rightId: String) : CheckAnswerDto

@Serializable
data class MatchingPairDto(val leftId: String, val rightId: String)

@Serializable
@SerialName("fill_blank")
data class FillBlankAnswerDto(val text: String) : AnswerDto, CheckAnswerDto

@Serializable
@SerialName("translate")
data class TranslateAnswerDto(val tokenIds: List<String>) : AnswerDto, CheckAnswerDto

@Serializable
@SerialName("sentence_order")
data class SentenceOrderAnswerDto(val tokenIds: List<String>) : AnswerDto, CheckAnswerDto

@Serializable
@SerialName("sentence_correction")
data class SentenceCorrectionAnswerDto(val optionId: String) : AnswerDto, CheckAnswerDto

@Serializable
@SerialName("sentence_completion")
data class SentenceCompletionAnswerDto(val optionId: String) : AnswerDto, CheckAnswerDto

@Serializable
data class CheckAnswerRequest(val answer: CheckAnswerDto)

@Serializable
data class MatchingPairResultDto(val leftId: String, val isCorrect: Boolean)

@Serializable
data class QuestionResultDto(
    val questionId: String,
    val isCorrect: Boolean,
    val correctAnswer: AnswerDto?,
    val explanation: String?,
    val pairResults: List<MatchingPairResultDto>? = null,
)
