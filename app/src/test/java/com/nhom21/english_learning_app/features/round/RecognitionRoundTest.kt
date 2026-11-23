package com.nhom21.english_learning_app.features.round

import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiFailure
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.core.network.InMemoryPreferencesDataStore
import com.nhom21.english_learning_app.data.repository.RoundRepository
import com.nhom21.english_learning_app.domain.model.*
import com.nhom21.english_learning_app.domain.usecase.CheckAnswerUseCase
import com.nhom21.english_learning_app.domain.usecase.GetRoundUseCase
import com.nhom21.english_learning_app.domain.usecase.SubmitRoundUseCase
import com.nhom21.english_learning_app.domain.usecase.SwipeVocabularyUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecognitionRoundTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = RecognitionRepository()
    private fun model() = RoundViewModel(
        roundId = "selected-round",
        getRound = GetRoundUseCase(repository),
        submitRound = SubmitRoundUseCase(repository),
        swipeVocabulary = SwipeVocabularyUseCase(repository),
        checkAnswer = CheckAnswerUseCase(repository),
        preferences = InMemoryPreferencesDataStore(),
    )
    private val RoundViewModel.current get() = uiState.value as Round2UiState.Question

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun checksThreePromptsAndSubmitsOnceWithConfirmedSelectionsInServerOrder() = runTest {
        val model = model()
        runCurrent()
        assertEquals(listOf("selected-round"), repository.loads)
        assertEquals("Chọn nghĩa của book", model.current.question.prompt)
        assertEquals(listOf("b", "a"), model.current.question.options.map { it.id })
        assertEquals(3, model.current.total)
        assertEquals(2, model.current.savedStars)
        model.selectOption("meaning", "b")
        model.checkOption("meaning")
        model.checkOption("meaning")
        model.selectOption("meaning", "a")
        model.nextQuestion("meaning")
        assertTrue(model.current.checking)
        assertEquals("b", model.current.selectedOptionId)
        runCurrent()
        assertEquals(Round2Feedback(false, "a"), model.current.feedback)
        model.nextQuestion("meaning")
        model.nextQuestion("meaning")
        assertEquals(2, model.current.savedStars)
        assertEquals("Chọn từ có nghĩa là sách", model.current.question.prompt)
        model.selectOption("word", "a")
        model.checkOption("word")
        runCurrent()
        assertTrue(model.current.feedback!!.isCorrect)
        model.nextQuestion("word")
        assertEquals(listOf("https://example.test/b.png", "https://example.test/a.png"),
            model.current.question.options.map { it.imageUrl })
        model.selectOption("picture", "a")
        model.checkOption("picture")
        runCurrent()
        model.nextQuestion("picture")
        model.nextQuestion("picture")
        model.retry()
        assertEquals(Round2UiState.Submitting(), model.uiState.value)
        runCurrent()
        val expected = listOf(RoundAnswer("meaning", Answer.MultipleChoice("b")),
            RoundAnswer("word", Answer.MultipleChoice("a")), RoundAnswer("picture", Answer.PictureChoice("a")))
        assertEquals(expected, repository.checks)
        assertEquals(listOf(expected), repository.submissions)
        repository.submitGate.complete(Unit)
        runCurrent()
        val state = model.uiState.value as Round2UiState.Completed
        assertEquals(1, state.correctCount)
        assertEquals(33.3, state.scorePercent!!, 0.0)
        assertEquals(0, state.stars)
        assertFalse(state.passed)
        assertEquals(Streak(4, null, false), state.streak)
        assertNull(state.unlocks!!.nextRoundId)
        model.retry()
        runCurrent()
        assertNull(model.current.selectedOptionId)
    }

    @Test fun networkRetryAndSkipPreserveOneAnswerAndFailedSubmitRetriesSamePayload() = runTest {
        repository.questions = listOf(repository.questions.single { it.id == "picture" })
        repository.checkError = ApiErrorType.NETWORK
        val model = model()
        runCurrent()
        model.selectOption("picture", "b")
        model.checkOption("picture")
        runCurrent()
        model.nextQuestion("picture")
        assertEquals("b", model.current.selectedOptionId)
        assertFalse(model.current.checking)
        assertEquals(1, model.current.networkFailures)
        model.checkOption("picture")
        runCurrent()
        assertEquals(2, model.current.networkFailures)
        repository.submitError = ApiErrorType.NETWORK
        repository.submitGate.complete(Unit)
        model.nextQuestion("picture")
        runCurrent()
        assertEquals(Round2UiState.Submitting(ApiErrorType.NETWORK), model.uiState.value)
        repository.submitError = null
        model.retry()
        model.retry()
        runCurrent()
        val payload = listOf(RoundAnswer("picture", Answer.PictureChoice("b")))
        assertEquals(listOf(payload, payload), repository.submissions)
        assertTrue(model.uiState.value is Round2UiState.Completed)
    }

    @Test fun checkValidationRetainsSelectionAndOnlyNetworkPermitsSkip() = runTest {
        val model = model()
        runCurrent()
        model.selectOption("meaning", "a")
        repository.checkError = ApiErrorType.VALIDATION_ERROR
        repeat(2) {
            model.checkOption("meaning")
            runCurrent()
        }
        model.nextQuestion("meaning")
        assertEquals(0, model.current.networkFailures)
        assertEquals("a", model.current.selectedOptionId)
        repository.checkError = ApiErrorType.ROUND_LOCKED
        model.checkOption("meaning")
        runCurrent()
        assertEquals(Round2UiState.Message(RoundMessage.Locked), model.uiState.value)
        assertTrue(repository.submissions.isEmpty())
    }

    @Test fun unknownUnsupportedAndEmptyContentHaveExplicitStates() = runTest {
        val original = repository.questions.first()
        for ((content, expected) in listOf(QuestionContent.Unknown("future") to RoundMessage.UpdateRequired,
            QuestionContent.Matching(emptyList(), emptyList()) to RoundMessage.Unsupported)) {
            repository.questions = listOf(original, original.copy(id = "other", content = content))
            val model = model()
            runCurrent()
            assertEquals(Round2UiState.Message(expected), model.uiState.value)
        }
        repository.questions = emptyList()
        val model = model()
        runCurrent()
        assertEquals(Round2UiState.Message(RoundMessage.Empty), model.uiState.value)
        assertTrue(repository.submissions.isEmpty())
    }

    private class RecognitionRepository : RoundRepository {
        private val options = listOf(TextOption("b", "vở"), TextOption("a", "sách"))
        var questions = listOf(
            Question("picture", 3, "Chọn hình", null, null, QuestionContent.PictureChoice(
                listOf("b", "a").map { PictureOption(it, "https://example.test/$it.png") })),
            Question("meaning", 1, "Chọn nghĩa của book", null, null, QuestionContent.MultipleChoice(options)),
            Question("word", 2, "Chọn từ có nghĩa là sách", null, null,
                QuestionContent.MultipleChoice(listOf(TextOption("b", "pen"), TextOption("a", "book")))),
        )
        val loads = mutableListOf<String>()
        val submissions = mutableListOf<List<RoundAnswer>>()
        val checks = mutableListOf<RoundAnswer>()
        val submitGate = CompletableDeferred<Unit>()
        var checkError: ApiErrorType? = null
        var submitError: ApiErrorType? = null

        override suspend fun getRound(roundId: String): ApiResult<RoundDetail> {
            loads.add(roundId)
            return ApiResult.Success(RoundDetail(RoundSummary(roundId, "lesson", 2, RoundType.RECOGNITION,
                null, ContentStatus.UNLOCKED, 2, null, 0, questions.size), questions))
        }

        override suspend fun checkAnswer(questionId: String, answer: Answer): ApiResult<QuestionResult> {
            checks.add(RoundAnswer(questionId, answer))
            checkError?.let { return ApiResult.Failure(ApiFailure(it, "fixture")) }
            val correct = if (questionId == "picture") Answer.PictureChoice("a") else Answer.MultipleChoice("a")
            return ApiResult.Success(QuestionResult(questionId, correct == answer, correct, null))
        }

        override suspend fun submitRound(roundId: String, answers: List<RoundAnswer>): ApiResult<RoundResult> {
            assertEquals("selected-round", roundId)
            submissions.add(answers)
            submitGate.await()
            submitError?.let { return ApiResult.Failure(ApiFailure(it, "fixture")) }
            return ApiResult.Success(RoundResult(roundId, questions.size, 1, 33.3, false, 70, 0, 2, 85.0,
                false, 3, emptyList(), RoundUnlocks(null, false, null, false, null), Streak(4, null, false)))
        }

        override suspend fun vocabularyAction(roundId: String, vocabularyId: String, action: VocabularyAction) =
            error("Unexpected vocabulary action")
    }
}
