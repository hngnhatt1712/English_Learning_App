package com.nhom21.english_learning_app.features.round

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Round2DemoSessionTest {
    @Test
    fun fixtureHasTenQuestionsInOrderAndPictureOptionsHaveNoLabels() {
        val expected = List(3) { Round2Variant.Meaning } + List(2) { Round2Variant.Word } +
            List(5) { Round2Variant.Picture }
        assertEquals(expected, round2DemoItems.map { it.question.variant })
        assertEquals(10, round2DemoItems.map { it.question.id }.distinct().size)
        round2DemoItems.forEach { item ->
            assertEquals(4, item.question.options.map { it.id }.distinct().size)
            assertTrue(item.question.options.any { it.id == item.correctOptionId })
            if (item.question.variant == Round2Variant.Picture) {
                assertTrue(item.question.options.all { it.label == null })
            }
        }
    }

    @Test
    fun checkAndNextRejectDuplicateAndStaleActionsAndResetTheQuestionState() {
        val session = Round2DemoSession()
        val first = round2DemoItems[0]
        val questionId = first.question.id

        assertFalse(session.check(questionId))
        assertFalse(session.next(questionId))
        assertTrue(session.select(questionId, first.question.options.first { it.id != first.correctOptionId }.id))
        assertTrue(session.select(questionId, first.correctOptionId))
        assertTrue(session.check(questionId))
        assertFalse(session.check(questionId))
        assertFalse(session.select(questionId, first.correctOptionId))
        assertEquals(1, (session.state() as Round2UiState.Question).number)
        assertEquals(Round2Feedback(true, first.correctOptionId), (session.state() as Round2UiState.Question).feedback)
        assertTrue(session.next(questionId))
        assertFalse(session.next(questionId))
        assertFalse(session.check(questionId))

        val next = session.state() as Round2UiState.Question
        assertEquals(2, next.number)
        assertNull(next.selectedOptionId)
        assertNull(next.feedback)
    }

    @Test
    fun lastContinueCompletesTheDemoAndShowsOnlyTheDemoResult() {
        val session = Round2DemoSession()
        round2DemoItems.forEachIndexed { index, item ->
            val id = item.question.id
            val selection = if (index < 8) item.correctOptionId else {
                item.question.options.first { it.id != item.correctOptionId }.id
            }
            assertTrue(session.select(id, selection))
            assertTrue(session.check(id))
            assertEquals(index < 8, (session.state() as Round2UiState.Question).feedback?.isCorrect)
            assertFalse(session.check(id))
            assertTrue(session.next(id))
        }

        assertEquals(Round2UiState.Completed(8, 10, 2, true), session.state())
        assertFalse(session.next(round2DemoItems.last().question.id))
    }
}
