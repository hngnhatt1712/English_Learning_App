package com.nhom21.english_learning_app.features.round

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Round1DemoSessionTest {
    @Test
    fun repeatedActionForOldCardDoesNotSkipTheNextCard() {
        val session = Round1DemoSession()
        val apple = (session.state() as Round1UiState.Learning).word

        assertTrue(session.onCardAction(apple.id, Round1Action.Add))
        assertFalse(session.onCardAction(apple.id, Round1Action.Skip))

        val next = session.state() as Round1UiState.Learning
        assertEquals(1, next.processedWords)
        assertEquals("banana", next.word.id)
    }

    @Test
    fun undoSkipRequeuesAnAddedCardAndReplayingDoesNotIncreaseCount() {
        val session = Round1DemoSession()
        session.onCardAction("apple", Round1Action.Skip)
        val notice = (session.state() as Round1UiState.Learning).feedback!!

        assertTrue(session.undo(notice.id))
        assertFalse(session.undo(notice.id))
        val returned = session.state() as Round1UiState.Learning
        assertEquals("apple", returned.word.id)
        assertTrue(returned.word.isInFlashcards)
        assertEquals(1, returned.processedWords)
        assertFalse(session.onCardAction("apple", Round1Action.Add))

        assertTrue(session.onCardAction("apple", Round1Action.KeepAdded))
        assertEquals(1, (session.state() as Round1UiState.Learning).processedWords)
    }

    @Test
    fun undoOnLastCardReturnsItWithoutResettingTheLearnedCount() {
        val session = Round1DemoSession(round1DemoWords.take(1))
        session.onCardAction("apple", Round1Action.Add)
        val ready = session.state() as Round1UiState.Finishing

        assertTrue(session.undo(ready.feedback!!.id))
        val returned = session.state() as Round1UiState.Learning
        assertFalse(returned.word.isInFlashcards)
        assertEquals(1, returned.processedWords)
        assertFalse(session.beginFinish())

        session.onCardAction("apple", Round1Action.Skip)
        assertTrue(session.beginFinish())
        assertFalse(session.beginFinish())
        assertEquals(Round1FinishStatus.Saving, (session.state() as Round1UiState.Finishing).status)
        session.complete()
        val result = session.state() as Round1UiState.Completed
        assertEquals(1, result.learnedWords)
        assertEquals(3, result.stars)
        assertEquals(0, result.addedWords)
        assertEquals(1, result.skippedWords)
        assertFalse(session.beginFinish())
    }

    @Test
    fun wordAlreadySavedCanContinueWithoutAnotherAdd() {
        val word = round1DemoWords.first { it.isInFlashcards }
        val session = Round1DemoSession(listOf(word))

        assertFalse(session.onCardAction(word.id, Round1Action.Add))
        assertTrue(session.onCardAction(word.id, Round1Action.KeepAdded))
        assertTrue(session.beginFinish())
        session.complete()

        val result = session.state() as Round1UiState.Completed
        assertEquals(1, result.learnedWords)
        assertEquals(0, result.addedWords)
        assertEquals(0, result.skippedWords)
    }
}
