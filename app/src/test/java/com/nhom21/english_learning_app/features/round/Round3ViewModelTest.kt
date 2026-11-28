package com.nhom21.english_learning_app.features.round

import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiFailure
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.core.network.InMemoryPreferencesDataStore
import com.nhom21.english_learning_app.data.repository.RoundRepository
import com.nhom21.english_learning_app.domain.model.Answer
import com.nhom21.english_learning_app.domain.model.ContentStatus
import com.nhom21.english_learning_app.domain.model.MatchingItem
import com.nhom21.english_learning_app.domain.model.MatchingPair
import com.nhom21.english_learning_app.domain.model.MatchingPairResult
import com.nhom21.english_learning_app.domain.model.Question
import com.nhom21.english_learning_app.domain.model.QuestionContent
import com.nhom21.english_learning_app.domain.model.QuestionResult
import com.nhom21.english_learning_app.domain.model.RoundAnswer
import com.nhom21.english_learning_app.domain.model.RoundDetail
import com.nhom21.english_learning_app.domain.model.RoundResult
import com.nhom21.english_learning_app.domain.model.RoundSummary
import com.nhom21.english_learning_app.domain.model.RoundType
import com.nhom21.english_learning_app.domain.model.RoundUnlocks
import com.nhom21.english_learning_app.domain.model.Streak
import com.nhom21.english_learning_app.domain.model.VocabularyAction
import com.nhom21.english_learning_app.domain.usecase.CheckAnswerUseCase
import com.nhom21.english_learning_app.domain.usecase.GetRoundUseCase
import com.nhom21.english_learning_app.domain.usecase.SubmitRoundUseCase
import com.nhom21.english_learning_app.domain.usecase.SwipeVocabularyUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Round3ViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val wrong = MatchingPair("l1", "r2")
    private val first = MatchingPair("l1", "r1")
    private val second = MatchingPair("l2", "r2")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun checkLocksWhilePendingAndOnlyLocksServerConfirmedItemsAfterSuccess() = runTest {
        val repo = FakeRounds()
        val response = CompletableDeferred<ApiResult<QuestionResult>>()
        repo.check = { response.await() }
        val vm = viewModel(repo)
        runCurrent()

        vm.selectMatchingLeft("q1", "l1")
        assertEquals("l1", vm.matching.selectedLeftId)
        assertTrue(repo.checks.isEmpty())
        vm.selectMatchingRight("q1", "r1")
        assertEquals(first, vm.matching.pendingPair)
        vm.selectMatchingRight("q1", "r2")
        vm.selectMatchingLeft("q1", "l2")
        vm.nextMatchingQuestion("q1")
        runCurrent()
        assertEquals(listOf("q1" to Answer.MatchingCheck(first)), repo.checks)
        assertTrue(vm.matching.attempts.isEmpty())

        response.complete(checked("q1", true))
        runCurrent()
        assertNull(vm.matching.pendingPair)
        assertEquals(listOf(first), vm.matching.pairs)
        assertTrue(vm.matching.feedback!!.isCorrect)
        assertEquals(listOf(first), vm.matching.attempts)
        assertFalse(vm.matching.canContinue)
        vm.selectMatchingLeft("q1", "l1")
        assertNull(vm.matching.selectedLeftId)
        vm.selectMatchingLeft("q1", "l2")
        vm.selectMatchingRight("q1", "r1")
        vm.nextMatchingQuestion("q1")
        runCurrent()
        assertNull(vm.matching.selectedRightId)
        assertEquals(1, repo.checks.size)
        assertTrue(repo.submissions.isEmpty())
    }

    @Test
    fun wrongPairWaitsForAcknowledgementAndSubmitRetriesUseTheSameOrderedAnswers() = runTest {
        val repo = FakeRounds(questionCount = 2)
        repo.check = { (id, answer) -> checked(id, (answer as Answer.MatchingCheck).pair != wrong) }
        val inFlight = CompletableDeferred<ApiResult<RoundResult>>()
        repo.submit = { if (repo.submissions.size == 1) failure(ApiErrorType.NETWORK) else inFlight.await() }
        val vm = viewModel(repo)
        runCurrent()

        assertEquals(2, vm.matching.questionCount)
        assertEquals(4, vm.matching.totalQuestions)
        vm.select("q1", wrong)
        runCurrent()
        val feedback = requireNotNull(vm.matching.feedback)
        assertFalse(feedback.isCorrect)
        assertEquals(wrong, feedback.pair)
        assertEquals(listOf(wrong), vm.matching.attempts)
        assertTrue(vm.matching.pairs.isEmpty())
        vm.acknowledgeMatchingFeedback("q1", feedback.id - 1)
        vm.select("q1", first)
        runCurrent()
        assertEquals(1, repo.checks.size)
        vm.acknowledgeMatchingFeedback("q1", feedback.id)
        assertNull(vm.matching.selectedLeftId)
        assertNull(vm.matching.selectedRightId)
        vm.select("q1", first)
        runCurrent()
        vm.select("q1", second)
        runCurrent()
        assertTrue(vm.matching.canContinue)
        vm.nextMatchingQuestion("q1")
        assertEquals(2, vm.matching.number)
        vm.select("q1", first)
        vm.selectMatchingRight("q2", "r2")
        vm.selectMatchingLeft("q2", "l2")
        runCurrent()
        vm.select("q2", first)
        runCurrent()
        vm.nextMatchingQuestion("q2")
        vm.nextMatchingQuestion("q2")
        vm.retry()
        runCurrent()

        val payload = listOf(
            RoundAnswer("q1", Answer.Matching(listOf(first, second), listOf(wrong, first, second))),
            RoundAnswer("q2", Answer.Matching(listOf(second, first), listOf(second, first))),
        )
        assertEquals(listOf(payload), repo.submissions)
        assertEquals(ApiErrorType.NETWORK, (vm.uiState.value as Round3UiState.Submitting).error)
        vm.retry()
        vm.retry()
        runCurrent()
        assertEquals(listOf(payload, payload), repo.submissions)
        val result = RoundResult(
            "round", 4, 3, 75.0, true, 70, 1, 2, 80.0, false, 2,
            listOf(QuestionResult("q1", false, null, null, listOf(
                MatchingPairResult("l1", false), MatchingPairResult("l2", true),
            ))),
            RoundUnlocks("next", false, null, false, null), Streak(4, null, false),
        )
        inFlight.complete(ApiResult.Success(result))
        runCurrent()
        assertEquals(Round3UiState.RepairIntro(1), vm.uiState.value)
        vm.startMatchingRepair()
        assertTrue(vm.matching.isRepair)
        assertEquals(listOf("l1"), vm.matching.question.left.map { it.id })
        assertEquals(listOf("r2", "r1"), vm.matching.question.right.map { it.id })

        vm.select("q1", wrong)
        runCurrent()
        assertFalse(vm.matching.canContinue)
        vm.acknowledgeMatchingFeedback("q1", requireNotNull(vm.matching.feedback).id)
        vm.finishMatchingRepair()
        val paused = vm.uiState.value as Round3UiState.Completed
        assertSame(result, paused.result)
        assertEquals(0, paused.repairedCount)

        vm.startMatchingRepair()
        assertTrue(vm.matching.isRepair)
        vm.select("q1", first)
        runCurrent()
        assertTrue(vm.matching.canContinue)
        assertTrue(vm.matching.attempts.isEmpty())
        vm.nextMatchingQuestion("q1")
        runCurrent()

        val completed = vm.uiState.value as Round3UiState.Completed
        assertSame(result, completed.result)
        assertEquals(1, completed.repairedCount)
        assertEquals(1, completed.repairTotal)
        assertEquals(listOf(payload, payload), repo.submissions)
    }

    @Test
    fun networkTimeoutAndValidationAllowReselectionWithoutRecordingAttempts() = runTest {
        val repo = FakeRounds()
        val vm = viewModel(repo)
        runCurrent()
        repo.check = { failure(ApiErrorType.NETWORK) }
        vm.select("q1", first)
        runCurrent()
        vm.assertReadyAfterFailure(ApiErrorType.NETWORK)
        repo.check = { withTimeout(1) { awaitCancellation() } }
        vm.select("q1", first)
        advanceUntilIdle()
        vm.assertReadyAfterFailure(ApiErrorType.NETWORK)
        repo.check = { failure(ApiErrorType.VALIDATION_ERROR) }
        vm.select("q1", first)
        runCurrent()
        vm.assertReadyAfterFailure(ApiErrorType.VALIDATION_ERROR)
        repo.check = { checked("another-question", true) }
        vm.select("q1", first)
        runCurrent()
        vm.assertReadyAfterFailure(ApiErrorType.UNKNOWN)

        repo.check = { checked("q1", true) }
        vm.select("q1", first)
        runCurrent()
        assertEquals(listOf(first), vm.matching.attempts)
    }

    @Test
    fun lateCompletionAfterExitIsIgnoredAndLockedCheckUsesExistingMessage() = runTest {
        val repo = FakeRounds()
        val completion = CompletableDeferred<ApiResult<QuestionResult>>()
        repo.check = { withContext(NonCancellable) { completion.await() } }
        val vm = viewModel(repo)
        runCurrent()
        vm.select("q1", first)
        runCurrent()
        vm.leaveMatching()
        completion.complete(checked("q1", true))
        runCurrent()
        assertEquals(Round3UiState.Exited, vm.uiState.value)
        assertTrue(repo.submissions.isEmpty())

        repo.check = { failure(ApiErrorType.ROUND_LOCKED) }
        val locked = viewModel(repo)
        runCurrent()
        locked.select("q1", first)
        runCurrent()
        assertEquals(Round2UiState.Message(RoundMessage.Locked), locked.uiState.value)
    }

    private fun RoundViewModel.assertReadyAfterFailure(error: ApiErrorType) {
        assertEquals(error, matching.checkError)
        assertNull(matching.pendingPair)
        assertNull(matching.feedback)
        assertNull(matching.selectedLeftId)
        assertNull(matching.selectedRightId)
        assertTrue(matching.attempts.isEmpty())
    }

    private fun RoundViewModel.select(id: String, pair: MatchingPair) {
        selectMatchingLeft(id, pair.leftId)
        selectMatchingRight(id, pair.rightId)
    }

    private val RoundViewModel.matching get() = uiState.value as Round3UiState.Question

    private fun viewModel(repo: RoundRepository) = RoundViewModel(
        "round", GetRoundUseCase(repo), SubmitRoundUseCase(repo), SwipeVocabularyUseCase(repo),
        CheckAnswerUseCase(repo), InMemoryPreferencesDataStore(),
    )

    private class FakeRounds(private val questionCount: Int = 1) : RoundRepository {
        val checks = mutableListOf<Pair<String, Answer>>()
        val submissions = mutableListOf<List<RoundAnswer>>()
        var check: suspend (Pair<String, Answer>) -> ApiResult<QuestionResult> = { error("Missing check stub") }
        var submit: suspend () -> ApiResult<RoundResult> = { error("Unexpected submit") }

        override suspend fun getRound(roundId: String): ApiResult<RoundDetail> {
            val round = RoundSummary(roundId, "lesson", 3, RoundType.MATCHING, null, ContentStatus.UNLOCKED,
                0, null, 0, questionCount * 2)
            val board = QuestionContent.Matching(
                listOf(MatchingItem("l1", "Cat", null), MatchingItem("l2", "Dog", null)),
                listOf(MatchingItem("r2", "Dog meaning", null), MatchingItem("r1", "Cat meaning", null)),
            )
            val questions = (1..questionCount).reversed().map { Question("q$it", it, "Match", null, null, board) }
            return ApiResult.Success(RoundDetail(round, questions))
        }

        override suspend fun checkAnswer(questionId: String, answer: Answer): ApiResult<QuestionResult> {
            val request = questionId to answer
            checks += request
            return check(request)
        }

        override suspend fun submitRound(roundId: String, answers: List<RoundAnswer>): ApiResult<RoundResult> {
            submissions += answers
            return submit()
        }

        override suspend fun vocabularyAction(roundId: String, vocabularyId: String, action: VocabularyAction) =
            error("Unexpected vocabulary action")
    }

    private companion object {
        fun failure(type: ApiErrorType) = ApiResult.Failure(ApiFailure(type, "Test failure"))
        fun checked(id: String, correct: Boolean) = ApiResult.Success(QuestionResult(id, correct, null, null))
    }
}
