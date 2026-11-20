package com.nhom21.english_learning_app.data.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface QuestionDto {
    val id: String
    val order: Int
    val prompt: String
    val imageUrl: String?
    val audioUrl: String?
}

@Serializable
data class TextOptionDto(val id: String, val text: String)

@Serializable
data class PictureOptionDto(val id: String, val imageUrl: String)

@Serializable
data class MatchingItemDto(val id: String, val text: String?, val imageUrl: String?)

@Serializable
enum class TranslationDirectionDto {
    @SerialName("en_to_vi") EN_TO_VI,
    @SerialName("vi_to_en") VI_TO_EN,
    UNKNOWN,
}

@Serializable
@SerialName("learn_card")
data class LearnCardQuestionDto(
    override val id: String,
    override val order: Int,
    override val prompt: String,
    override val imageUrl: String?,
    override val audioUrl: String?,
    val vocabulary: VocabularyDto,
) : QuestionDto

@Serializable
@SerialName("multiple_choice")
data class MultipleChoiceQuestionDto(
    override val id: String,
    override val order: Int,
    override val prompt: String,
    override val imageUrl: String?,
    override val audioUrl: String?,
    val options: List<TextOptionDto>,
) : QuestionDto

@Serializable
@SerialName("picture_choice")
data class PictureChoiceQuestionDto(
    override val id: String,
    override val order: Int,
    override val prompt: String,
    override val imageUrl: String?,
    override val audioUrl: String?,
    val options: List<PictureOptionDto>,
) : QuestionDto

@Serializable
@SerialName("matching")
data class MatchingQuestionDto(
    override val id: String,
    override val order: Int,
    override val prompt: String,
    override val imageUrl: String?,
    override val audioUrl: String?,
    val left: List<MatchingItemDto>,
    val right: List<MatchingItemDto>,
) : QuestionDto

@Serializable
@SerialName("fill_blank")
data class FillBlankQuestionDto(
    override val id: String,
    override val order: Int,
    override val prompt: String,
    override val imageUrl: String?,
    override val audioUrl: String?,
    val sentence: String,
    val wordBank: List<String>?,
) : QuestionDto

@Serializable
@SerialName("translate")
data class TranslateQuestionDto(
    override val id: String,
    override val order: Int,
    override val prompt: String,
    override val imageUrl: String?,
    override val audioUrl: String?,
    val sourceText: String,
    val direction: TranslationDirectionDto = TranslationDirectionDto.UNKNOWN,
    val tokens: List<TextOptionDto>,
) : QuestionDto

@Serializable
@SerialName("sentence_order")
data class SentenceOrderQuestionDto(
    override val id: String,
    override val order: Int,
    override val prompt: String,
    override val imageUrl: String?,
    override val audioUrl: String?,
    val tokens: List<TextOptionDto>,
) : QuestionDto

@Serializable
@SerialName("sentence_correction")
data class SentenceCorrectionQuestionDto(
    override val id: String,
    override val order: Int,
    override val prompt: String,
    override val imageUrl: String?,
    override val audioUrl: String?,
    val options: List<TextOptionDto>,
) : QuestionDto

@Serializable
@SerialName("sentence_completion")
data class SentenceCompletionQuestionDto(
    override val id: String,
    override val order: Int,
    override val prompt: String,
    override val imageUrl: String?,
    override val audioUrl: String?,
    val sentence: String,
    val options: List<TextOptionDto>,
) : QuestionDto

@Serializable
@SerialName("unknown")
data class UnknownQuestionDto(
    override val id: String,
    override val order: Int,
    override val prompt: String,
    override val imageUrl: String? = null,
    override val audioUrl: String? = null,
    @Transient val originalType: String = "unknown",
) : QuestionDto

/** The discriminator is consumed by Json before the subtype reads its fields. */
internal fun unknownQuestionDeserializer(type: String): KSerializer<UnknownQuestionDto> {
    val serializer = UnknownQuestionDto.serializer()
    return object : KSerializer<UnknownQuestionDto> by serializer {
        override fun deserialize(decoder: Decoder): UnknownQuestionDto =
            serializer.deserialize(decoder).copy(originalType = type)
    }
}
