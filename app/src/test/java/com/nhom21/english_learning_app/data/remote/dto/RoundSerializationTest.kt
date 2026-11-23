package com.nhom21.english_learning_app.data.remote.dto

import com.nhom21.english_learning_app.core.network.NetworkModule
import com.nhom21.english_learning_app.data.remote.mapper.toDto
import com.nhom21.english_learning_app.domain.model.Answer
import com.nhom21.english_learning_app.domain.model.MatchingPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoundSerializationTest {
    private val json = NetworkModule.provideJson()

    @Test
    fun laterQuestionTypesDecodeTheirContractFields() {
        val common = """"id":"question","order":1,"prompt":"Prompt","imageUrl":null,"audioUrl":null"""
        val questions = json.decodeFromString<List<QuestionDto>>(
            """[
                {$common,"type":"matching","left":[{"id":"l","text":"left","imageUrl":null}],
                "right":[{"id":"r","text":null,"imageUrl":"https://media.example/right.png"}]},
                {$common,"type":"fill_blank","sentence":"I ___ here.","wordBank":null},
                {$common,"type":"translate","sourceText":"Hello","direction":"future_direction","tokens":[{"id":"t1","text":"Xin chào"}]},
                {$common,"type":"sentence_order","tokens":[{"id":"t2","text":"there"},{"id":"t1","text":"Hi"}]},
                {$common,"type":"sentence_correction","options":[{"id":"o","text":"She goes."}]},
                {$common,"type":"sentence_completion","sentence":"She ___ home.","options":[{"id":"o","text":"goes"}]}
            ]"""
        )

        val matching = questions[0] as MatchingQuestionDto
        assertEquals("l", matching.left.single().id)
        assertNull(matching.right.single().text)
        assertEquals("https://media.example/right.png", matching.right.single().imageUrl)
        assertNull((questions[1] as FillBlankQuestionDto).wordBank)
        val translate = questions[2] as TranslateQuestionDto
        assertEquals("Hello", translate.sourceText)
        assertEquals(TranslationDirectionDto.UNKNOWN, translate.direction)
        assertEquals(listOf("t2", "t1"), (questions[3] as SentenceOrderQuestionDto).tokens.map { it.id })
        assertEquals("She goes.", (questions[4] as SentenceCorrectionQuestionDto).options.single().text)
        assertEquals("She ___ home.", (questions[5] as SentenceCompletionQuestionDto).sentence)
    }

    @Test
    fun answerUnionSendsTheContractShapeForEverySubtype() {
        val cases = listOf(
            Answer.MultipleChoice("o1") to """{"type":"multiple_choice","optionId":"o1"}""",
            Answer.PictureChoice("o2") to """{"type":"picture_choice","optionId":"o2"}""",
            Answer.Matching(listOf(MatchingPair("l", "r"))) to
                """{"type":"matching","pairs":[{"leftId":"l","rightId":"r"}]}""",
            Answer.FillBlank("goes") to """{"type":"fill_blank","text":"goes"}""",
            Answer.Translate(listOf("t2", "t1")) to """{"type":"translate","tokenIds":["t2","t1"]}""",
            Answer.SentenceOrder(listOf("t2", "t1")) to """{"type":"sentence_order","tokenIds":["t2","t1"]}""",
            Answer.SentenceCorrection("o3") to """{"type":"sentence_correction","optionId":"o3"}""",
            Answer.SentenceCompletion("o4") to """{"type":"sentence_completion","optionId":"o4"}""",
        )

        for ((answer, expected) in cases) {
            val encoded = json.encodeToString<AnswerDto>(answer.toDto())
            assertEquals(json.parseToJsonElement(expected), json.parseToJsonElement(encoded))
            assertEquals(1, Regex("\"type\"\\s*:").findAll(encoded).count())
        }
    }
}
