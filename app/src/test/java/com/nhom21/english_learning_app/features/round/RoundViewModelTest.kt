package com.nhom21.english_learning_app.features.round

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiFailure
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.repository.RoundRepository
import com.nhom21.english_learning_app.domain.model.Answer
import com.nhom21.english_learning_app.domain.model.ContentStatus
import com.nhom21.english_learning_app.domain.model.Question
import com.nhom21.english_learning_app.domain.model.QuestionContent
import com.nhom21.english_learning_app.domain.model.RoundAnswer
import com.nhom21.english_learning_app.domain.model.RoundDetail
import com.nhom21.english_learning_app.domain.model.RoundResult
import com.nhom21.english_learning_app.domain.model.RoundSummary
import com.nhom21.english_learning_app.domain.model.RoundType
import com.nhom21.english_learning_app.domain.model.RoundUnlocks
import com.nhom21.english_learning_app.domain.model.Streak
import com.nhom21.english_learning_app.domain.model.Vocabulary
import com.nhom21.english_learning_app.domain.model.VocabularyAction
import com.nhom21.english_learning_app.domain.model.VocabularyActionResult
import com.nhom21.english_learning_app.domain.model.VocabularyStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoundViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val preferences = MemoryPreferences()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun undoBeforeResponseKeepsRequestOrderAndCancelsPendingFinish() = runTest {
        val repository = FakeRounds(word("a"), word("b"))
        val response = CompletableDeferred<Unit>()
        repository.beforeAction = { if (repository.actions.size == 1) response.await() }
        val model = viewModel(repository)
        runCurrent()

        model.swipe("a", Round1Action.Add)
        runCurrent()
        val firstNotice = requireNotNull(model.learning.feedback)
        model.swipe("b", Round1Action.Skip)
        model.finish()
        assertEquals(listOf("a" to VocabularyAction.FLASHCARD), repository.actions)
        assertEquals(Round1FinishStatus.Saving, model.finishing.status)

        model.undo(firstNotice.id)
        model.undo(firstNotice.id)
        assertEquals("a", model.learning.word.id)
        assertTrue(model.learning.hasPendingActions)
        response.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("a" to VocabularyAction.FLASHCARD, "b" to VocabularyAction.SKIP,
            "a" to VocabularyAction.SKIP), repository.actions)
        assertFalse(model.learning.word.isInFlashcards)
        assertTrue(repository.submissions.isEmpty())

        model.swipe("a", Round1Action.Skip)
        model.finish()
        advanceUntilIdle()
        assertEquals(listOf(emptyList<RoundAnswer>()), repository.submissions)
        assertEquals(2, (model.uiState.value as Round1UiState.Completed).stars)
    }

    @Test
    fun failedActionStaysQueuedAndFinishingWaitsForEveryUndoWindow() = runTest {
        val repository = FakeRounds(word("a"), word("b"))
        repository.failFirstAction = true
        val model = viewModel(repository)
        runCurrent()
        model.swipe("a", Round1Action.Add)
        runCurrent()
        assertEquals(R.string.round_network_error, model.learning.syncError)
        assertTrue(model.learning.hasPendingActions)
        model.swipe("b", Round1Action.Add)
        model.finish()
        model.finish()
        runCurrent()
        assertEquals(listOf("a", "a", "b"), repository.actions.map { it.first })
        assertEquals(R.string.round_feedback_added, model.finishing.feedback?.message)
        assertTrue(repository.submissions.isEmpty())
        advanceTimeBy(3_000)
        runCurrent()
        assertTrue(requireNotNull(model.finishing.feedback).canUndo)
        assertTrue(repository.submissions.isEmpty())
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(listOf(emptyList<RoundAnswer>()), repository.submissions)
    }

    @Test
    fun alreadySavedCardOffersUndoAndKeepsServerConfirmedReviewedState() = runTest {
        val repository = FakeRounds(word("a", VocabularyStatus.LEARNING))
        val model = viewModel(repository)
        runCurrent()
        assertTrue(model.learning.word.isInFlashcards)
        model.swipe("a", Round1Action.KeepAdded)
        val undo = requireNotNull(model.finishing.feedback)
        assertTrue(undo.canUndo)
        model.undo(undo.id)
        runCurrent()
        assertTrue(model.learning.word.isInFlashcards)
        assertEquals(R.string.round_feedback_kept, model.learning.feedback?.message)
        assertEquals(listOf("a" to VocabularyAction.FLASHCARD, "a" to VocabularyAction.SKIP), repository.actions)
        advanceUntilIdle()
    }

    @Test
    fun submitNetworkErrorCanRetryAndConflictReloadsOnlyMissingVocabulary() = runTest {
        val repository = FakeRounds(word("a"), word("b"))
        repository.submitErrors.add(ApiErrorType.NETWORK)
        repository.submitErrors.add(ApiErrorType.ROUND_INCOMPLETE)
        val model = viewModel(repository)
        runCurrent()
        model.swipe("a", Round1Action.Skip)
        model.swipe("b", Round1Action.Add)
        model.finish()
        advanceUntilIdle()
        assertEquals(Round1FinishStatus.Error, model.finishing.status)
        assertEquals(R.string.round_network_error, model.finishing.errorMessage)
        repository.words["a"] = word("a")

        model.finish()
        advanceUntilIdle()
        assertEquals("a", model.learning.word.id)
        assertEquals(1, model.learning.processedWords)
        assertEquals(0, model.learning.remainingCardsBehind)
        assertEquals(2, repository.fetches)
        assertEquals(2, repository.actions.size)
        assertTrue(repository.submissions.all { it.isEmpty() })
    }

    @Test
    fun lockedRoundHasDedicatedStateAndSwipeDemoIsClaimedOnceAcrossModels() = runTest {
        val repository = FakeRounds(word("a")).apply { getError = ApiErrorType.ROUND_LOCKED }
        val first = viewModel(repository)
        runCurrent()
        assertEquals(Round1UiState.Locked, first.uiState.value)
        repository.getError = null
        val second = viewModel(repository)
        runCurrent()
        assertTrue(second.claimSwipeDemo())
        assertFalse(viewModel(repository).claimSwipeDemo())
        advanceUntilIdle()
    }

    private fun viewModel(repository: RoundRepository) =
        RoundViewModel("round", repository, preferences)

    private val RoundViewModel.learning get() = uiState.value as Round1UiState.Learning
    private val RoundViewModel.finishing get() = uiState.value as Round1UiState.Finishing

    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            return transform(data.value).also { data.value = it }
        }
    }

    private class FakeRounds(vararg initialWords: Vocabulary) : RoundRepository {
        val words = initialWords.associateBy { it.id }.toMutableMap()
        val actions = mutableListOf<Pair<String, VocabularyAction>>()
        val submissions = mutableListOf<List<RoundAnswer>>()
        val submitErrors = ArrayDeque<ApiErrorType>()
        var beforeAction: suspend () -> Unit = {}
        var failFirstAction = false
        var getError: ApiErrorType? = null
        var fetches = 0

        override suspend fun getRound(roundId: String): ApiResult<RoundDetail> {
            fetches++
            getError?.let { return failure(it) }
            val round = RoundSummary(roundId, "lesson", 1, RoundType.LEARN, null,
                ContentStatus.UNLOCKED, 0, null, 0, words.size)
            val cards = words.values.mapIndexed { index, word ->
                Question("q-${word.id}", index + 1, word.word, null, null, QuestionContent.LearnCard(word))
            }
            return ApiResult.Success(RoundDetail(round, cards))
        }

        override suspend fun checkAnswer(questionId: String, answer: Answer) = error("Round 1 does not check")

        override suspend fun vocabularyAction(roundId: String, vocabularyId: String, action: VocabularyAction):
            ApiResult<VocabularyActionResult> {
            actions.add(vocabularyId to action)
            beforeAction()
            if (failFirstAction && actions.size == 1) return failure(ApiErrorType.NETWORK)
            val current = words.getValue(vocabularyId)
            val status = when {
                current.status == VocabularyStatus.LEARNING -> VocabularyStatus.LEARNING
                action == VocabularyAction.FLASHCARD -> VocabularyStatus.IN_FLASHCARD
                else -> VocabularyStatus.SKIPPED
            }
            val saved = current.copy(status = status, isInFlashcard = status != VocabularyStatus.SKIPPED)
            words[vocabularyId] = saved
            return ApiResult.Success(VocabularyActionResult(vocabularyId, saved.status, saved.isInFlashcard))
        }

        override suspend fun submitRound(roundId: String, answers: List<RoundAnswer>): ApiResult<RoundResult> {
            submissions.add(answers)
            submitErrors.removeFirstOrNull()?.let { return failure(it) }
            return ApiResult.Success(RoundResult(roundId, words.size, words.size, 100.0, true, 70,
                2, 0, null, false, 0, emptyList(), RoundUnlocks(null, false, null, false, null),
                Streak(0, null, false)))
        }

        private fun failure(type: ApiErrorType) = ApiResult.Failure(ApiFailure(type, "test failure"))
    }

    private fun word(id: String, status: VocabularyStatus = VocabularyStatus.NEW) =
        Vocabulary(id, "topic", "level", id, "meaning", "noun", "/word/", "Example",
            "http://example.test/$id.png", "http://example.test/$id.mp3", status, status != VocabularyStatus.NEW)
}
