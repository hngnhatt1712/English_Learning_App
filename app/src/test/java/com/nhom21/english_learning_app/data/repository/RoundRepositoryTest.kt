package com.nhom21.english_learning_app.data.repository

import com.nhom21.english_learning_app.core.network.ApiErrorParser
import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.core.network.AuthInterceptor
import com.nhom21.english_learning_app.core.network.FieldError
import com.nhom21.english_learning_app.core.network.InMemoryPreferencesDataStore
import com.nhom21.english_learning_app.core.network.NetworkModule
import com.nhom21.english_learning_app.core.network.SessionEvents
import com.nhom21.english_learning_app.core.network.TokenManager
import com.nhom21.english_learning_app.data.remote.api.RoundApi
import com.nhom21.english_learning_app.data.remote.dto.SubmitRoundRequest
import com.nhom21.english_learning_app.domain.model.Answer
import com.nhom21.english_learning_app.domain.model.MatchingPair
import com.nhom21.english_learning_app.domain.model.MatchingPairResult
import com.nhom21.english_learning_app.domain.model.QuestionContent
import com.nhom21.english_learning_app.domain.model.RoundAnswer
import com.nhom21.english_learning_app.domain.model.VocabularyAction
import com.nhom21.english_learning_app.domain.model.VocabularyStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class RoundRepositoryTest {
    private val server = MockWebServer()
    private val json = NetworkModule.provideJson()
    private val parser = ApiErrorParser(json)
    private val tokenManager = TokenManager(InMemoryPreferencesDataStore())
    private val sessionEvents = SessionEvents()
    private lateinit var repository: RoundRepositoryImpl
    private lateinit var withRoundApi: (RoundApi) -> RoundRepositoryImpl

    @Before
    fun setUp() {
        server.start()
        val localUrl = server.url("/")
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                // Keep the app's base path and converter while routing HTTP to the local test server.
                val request = chain.request()
                val url = request.url.newBuilder()
                    .scheme(localUrl.scheme)
                    .host(localUrl.host)
                    .port(localUrl.port)
                    .build()
                chain.proceed(request.newBuilder().url(url).build())
            }
            .addInterceptor(AuthInterceptor(tokenManager, sessionEvents, parser))
            .retryOnConnectionFailure(false)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        val retrofit = NetworkModule.provideRetrofit(client, json)
        val questionApi = NetworkModule.provideQuestionApi(retrofit)
        val vocabularyApi = NetworkModule.provideVocabularyApi(retrofit)
        withRoundApi = { roundApi -> RoundRepositoryImpl(roundApi, questionApi, vocabularyApi, parser) }
        repository = withRoundApi(NetworkModule.provideRoundApi(retrofit))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun getRoundDecodesKnownQuestionsAndKeepsUnknownQuestionMetadata() = runTest {
        tokenManager.saveToken("test-round-session")
        enqueue(roundDetail(listOf(learnCard, multipleChoice, unknownQuestion, pictureChoice).joinToString(","), 4))

        val data = repository.getRound(ROUND_ID).success()

        assertEquals(ROUND_ID, data.round.id)
        assertNull(data.round.bestScorePercent)
        assertEquals(listOf("learn", "multiple", "future", "picture"), data.questions.map { it.id })
        assertEquals(listOf(1, 2, 3, 4), data.questions.map { it.order })
        val vocabulary = (data.questions[0].content as QuestionContent.LearnCard).vocabulary
        assertEquals(VOCABULARY_ID, vocabulary.id)
        assertEquals("noun", vocabulary.partOfSpeech)
        assertEquals("/fɪʃ/", vocabulary.phonetic)
        assertEquals(AUDIO, vocabulary.audioUrl)
        assertEquals(VocabularyStatus.NEW, vocabulary.status)
        assertNull(vocabulary.imageUrl)
        assertNull(vocabulary.example)

        val textOptions = (data.questions[1].content as QuestionContent.MultipleChoice).options
        assertEquals(listOf("o2", "o1"), textOptions.map { it.id })
        val unknown = data.questions[2]
        assertEquals(QuestionContent.Unknown("audio_choice"), unknown.content)
        assertEquals("Listen", unknown.prompt)
        assertEquals(IMAGE, unknown.imageUrl)
        assertEquals(AUDIO, unknown.audioUrl)
        val pictureOptions = (data.questions[3].content as QuestionContent.PictureChoice).options
        assertEquals(listOf("o2", "o1"), pictureOptions.map { it.id })
        assertEquals(IMAGE, pictureOptions.first().imageUrl)

        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("GET", request.method)
        assertEquals("/api/v1/rounds/$ROUND_ID", request.path)
        assertEquals("Bearer test-round-session", request.getHeader("Authorization"))
        assertEquals(0L, request.bodySize)
    }

    @Test
    fun emptyRoundIsValidButMalformedKnownTypeAndMissingDataAreFailures() = runTest {
        enqueue(roundDetail("", 0))
        assertTrue(repository.getRound(ROUND_ID).success().questions.isEmpty())

        val badChoice = """{"id":"broken","order":2,"prompt":"Pick","type":"multiple_choice","imageUrl":null,"audioUrl":null}"""
        enqueue(roundDetail("$learnCard,$badChoice", 2))
        val invalid = repository.getRound(ROUND_ID) as ApiResult.Failure
        assertEquals(ApiErrorType.UNKNOWN, invalid.error.type)
        assertEquals("Invalid response", invalid.error.message)

        enqueue("null")
        val missing = repository.getRound(ROUND_ID) as ApiResult.Failure
        assertEquals(ApiErrorType.UNKNOWN, missing.error.type)
        assertEquals("Missing response data", missing.error.message)
        assertEquals(200, missing.error.httpStatus)
    }

    @Test
    fun checkSendsOneDiscriminatorAndWrongAnswerIsSuccess() = runTest {
        enqueue(questionResult)

        val data = repository.checkAnswer(QUESTION_ID, Answer.MultipleChoice("o1")).success()

        assertEquals(QUESTION_ID, data.questionId)
        assertFalse(data.isCorrect)
        assertEquals(Answer.MultipleChoice("o2"), data.correctAnswer)
        assertEquals("Backend feedback", data.explanation)
        assertPost("/api/v1/questions/$QUESTION_ID/check", """{"answer":{"type":"multiple_choice","optionId":"o1"}}""", 1)
    }

    @Test
    fun matchingCheckAndSubmitUseDifferentShapesAndMapNullableFeedback() = runTest {
        val board = """{"id":"$QUESTION_ID","order":1,"prompt":"Match","type":"matching",
            "imageUrl":null,"audioUrl":null,
            "left":[{"id":"l1","text":"Cat","imageUrl":null},{"id":"l2","text":"Dog","imageUrl":null}],
            "right":[{"id":"r2","text":"Dog meaning","imageUrl":null},{"id":"r1","text":null,"imageUrl":"$IMAGE"}]}"""
        enqueue(roundDetail(board, 2, number = 3, type = "matching"))
        val detail = repository.getRound(ROUND_ID).success()
        assertEquals(2, detail.round.totalQuestions)
        with(detail.questions.single().content as QuestionContent.Matching) {
            assertEquals(listOf("l1", "l2"), left.map { it.id })
            assertEquals(listOf("r2", "r1"), right.map { it.id })
            assertEquals(IMAGE, right.last().imageUrl)
        }
        assertEquals("/api/v1/rounds/$ROUND_ID", requireNotNull(server.takeRequest(2, TimeUnit.SECONDS)).path)

        val feedback = """"questionId":"$QUESTION_ID","isCorrect":false,"correctAnswer":null,"explanation":null"""
        enqueue("{$feedback}")
        val wrong = MatchingPair("l1", "r2")
        val fixed = MatchingPair("l1", "r1")
        val checked = repository.checkAnswer(QUESTION_ID, Answer.MatchingCheck(wrong)).success()
        assertFalse(checked.isCorrect)
        assertNull(checked.correctAnswer)
        assertNull(checked.explanation)
        assertNull(checked.pairResults)
        assertPost(
            "/api/v1/questions/$QUESTION_ID/check",
            """{"answer":{"type":"matching","leftId":"l1","rightId":"r2"}}""", 1,
        )

        val result = """{$feedback,"pairResults":[{"leftId":"l1","isCorrect":false},{"leftId":"l2","isCorrect":true}]}"""
        enqueue(roundResult(score = 50.0, bestScore = 80.0, stars = 0, bestStars = 2, results = result))
        val paired = repository.submitRound(ROUND_ID, listOf(RoundAnswer(
            QUESTION_ID, Answer.Matching(listOf(fixed, MatchingPair("l2", "r2")), listOf(wrong, fixed)),
        ))).success().results.single()
        assertNull(paired.correctAnswer)
        assertNull(paired.explanation)
        assertEquals(listOf(MatchingPairResult("l1", false), MatchingPairResult("l2", true)), paired.pairResults)
        assertPost(
            "/api/v1/rounds/$ROUND_ID/submit",
            """{"answers":[{"questionId":"$QUESTION_ID","answer":{"type":"matching",
                "pairs":[{"leftId":"l1","rightId":"r1"},{"leftId":"l2","rightId":"r2"}],
                "attempts":[{"leftId":"l1","rightId":"r2"},{"leftId":"l1","rightId":"r1"}]}}]}""", 1,
        )
    }

    @Test
    fun translateCheckAndSubmitEncodeOnlyTheSelectedInputMode() = runTest {
        val feedback = """{"questionId":"$QUESTION_ID","isCorrect":true,
            "correctAnswer":{"type":"translate","tokenIds":["t1","t2"]},"explanation":null}"""
        val typed = Answer.TranslateText("I read.")
        val chips = Answer.Translate(listOf("t1", "t2"))
        enqueue(feedback)
        assertEquals(chips, repository.checkAnswer(QUESTION_ID, typed).success().correctAnswer)
        assertPost("/api/v1/questions/$QUESTION_ID/check",
            """{"answer":{"type":"translate","text":"I read."}}""", 1)

        enqueue(feedback)
        assertTrue(repository.checkAnswer(QUESTION_ID, chips).success().isCorrect)
        assertPost("/api/v1/questions/$QUESTION_ID/check",
            """{"answer":{"type":"translate","tokenIds":["t1","t2"]}}""", 1)

        enqueue(roundResult(score = 100.0, bestScore = 100.0, stars = 3, bestStars = 3, results = feedback))
        assertTrue(repository.submitRound(ROUND_ID, listOf(RoundAnswer(QUESTION_ID, typed))).success().passed)
        assertPost("/api/v1/rounds/$ROUND_ID/submit",
            """{"answers":[{"questionId":"$QUESTION_ID","answer":{"type":"translate","text":"I read."}}]}""", 1)
    }

    @Test
    fun submitSendsCallerAnswersAndEmptyListAndMapsSprintFiveResults() = runTest {
        enqueue(roundResult(score = 66.6, bestScore = 83.3, stars = 0, bestStars = 2, results = questionResult))
        val scored = repository.submitRound(ROUND_ID, listOf(RoundAnswer(QUESTION_ID, Answer.PictureChoice("o2")))).success()
        assertPost(
            "/api/v1/rounds/$ROUND_ID/submit",
            """{"answers":[{"questionId":"$QUESTION_ID","answer":{"type":"picture_choice","optionId":"o2"}}]}""",
            1,
        )
        assertEquals(ROUND_ID, scored.roundId)
        assertEquals(66.6, scored.scorePercent, 0.0)
        assertEquals(83.3, requireNotNull(scored.bestScorePercent), 0.0)
        assertEquals(70, scored.passThresholdPercent)
        assertFalse(scored.passed)
        assertEquals(2, scored.bestStars)
        assertEquals(QUESTION_ID, scored.results.single().questionId)

        enqueue(roundResult(score = 100.0, bestScore = null, stars = 3, bestStars = 0, results = ""))
        val learn = repository.submitRound(ROUND_ID, emptyList()).success()
        assertPost("/api/v1/rounds/$ROUND_ID/submit", """{"answers":[]}""", 0)
        assertTrue(learn.passed)
        assertEquals(3, learn.stars)
        assertEquals(0, learn.bestStars)
        assertNull(learn.bestScorePercent)
        assertEquals(0, learn.attempts)
        assertFalse(learn.isNewBest)
        assertTrue(learn.results.isEmpty())
        assertNull(learn.unlocks.nextRoundId)
        assertNull(learn.unlocks.nextTopicId)
        assertNull(learn.unlocks.nextLevelId)
        assertFalse(learn.unlocks.topicCompleted)
        assertFalse(learn.unlocks.levelCompleted)
        assertEquals(4, learn.streak.current)
        assertEquals("2026-11-19", learn.streak.lastLearningDate)
        assertFalse(learn.streak.learnedToday)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun vocabularyActionsUseRoundPathAndReturnServerState() = runTest {
        val path = "/api/v1/rounds/$ROUND_ID/vocabulary-action"
        enqueue(vocabularyResult("in_flashcard", true))
        assertEquals(VocabularyStatus.IN_FLASHCARD, repository
            .vocabularyAction(ROUND_ID, VOCABULARY_ID, VocabularyAction.FLASHCARD).success().status)
        assertPost(path, """{"vocabularyId":"$VOCABULARY_ID","action":"flashcard"}""", 0)

        enqueue(vocabularyResult("in_flashcard", true))
        val kept = repository.vocabularyAction(ROUND_ID, VOCABULARY_ID, VocabularyAction.SKIP).success()
        assertEquals(VOCABULARY_ID, kept.vocabularyId)
        assertEquals(VocabularyStatus.IN_FLASHCARD, kept.status)
        assertTrue(kept.isInFlashcard)
        assertPost(path, """{"vocabularyId":"$VOCABULARY_ID","action":"skip"}""", 0)

        enqueue(vocabularyResult("future_status", false))
        val future = repository.vocabularyAction(ROUND_ID, VOCABULARY_ID, VocabularyAction.SKIP).success()
        assertEquals(VocabularyStatus.UNKNOWN, future.status)
        assertFalse(future.isInFlashcard)
    }

    @Test
    fun repositoryPreservesBackendFailureMetadataAndUnauthorizedHandling() = runTest {
        tokenManager.saveToken("test-round-session")
        for ((code, type) in listOf(
            403 to ApiErrorType.ROUND_LOCKED,
            409 to ApiErrorType.ROUND_INCOMPLETE,
            422 to ApiErrorType.VALIDATION_ERROR,
            404 to ApiErrorType.NOT_FOUND,
            401 to ApiErrorType.UNAUTHORIZED,
        )) {
            val field = """{"field":"answer.optionId","message":"Unknown option","code":"INVALID_VALUE"}"""
            val errors = if (code == 422) "[$field]" else "null"
            server.enqueue(MockResponse().setResponseCode(code).setBody(
                """{"status":false,"message":"Error","data":null,"error":{"type":"${type.wireName}","detail":"Backend detail","traceId":"trace-$code","errors":$errors}}"""
            ))

            val result = when (code) {
                409 -> repository.submitRound(ROUND_ID, emptyList())
                422 -> repository.checkAnswer(QUESTION_ID, Answer.MultipleChoice("invalid"))
                else -> repository.getRound(ROUND_ID)
            }

            val error = (result as ApiResult.Failure).error
            assertEquals(type, error.type)
            assertEquals(code, error.httpStatus)
            assertEquals("Backend detail", error.message)
            assertEquals("trace-$code", error.traceId)
            assertEquals(
                if (code == 422) listOf(FieldError("answer.optionId", "Unknown option", "INVALID_VALUE")) else emptyList(),
                error.fieldErrors,
            )
            assertEquals(code == 401, sessionEvents.isSessionExpired)
            assertEquals(if (code == 401) null else "test-round-session", tokenManager.accessToken.first())
        }
        assertEquals(5, server.requestCount)
    }

    @Test
    fun networkFailureAndCancellationRemainDistinct() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val failure = repository.getRound(ROUND_ID) as ApiResult.Failure
        assertEquals(ApiErrorType.NETWORK, failure.error.type)

        val cancellation = CancellationException("test cancellation")
        val cancelledApi = object : RoundApi {
            override suspend fun getRound(roundId: String) = throw cancellation
            override suspend fun submitRound(roundId: String, request: SubmitRoundRequest) = throw cancellation
        }
        try {
            withRoundApi(cancelledApi).getRound(ROUND_ID)
            fail("Expected cancellation to propagate")
        } catch (e: CancellationException) {
            assertSame(cancellation, e)
        }
    }

    private fun enqueue(data: String) {
        server.enqueue(MockResponse().setBody("""{"status":true,"message":"OK","data":$data,"error":null}"""))
    }

    private fun assertPost(path: String, expected: String, discriminators: Int) {
        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("POST", request.method)
        assertEquals(path, request.path)
        val body = request.body.readUtf8()
        assertEquals(json.parseToJsonElement(expected), json.parseToJsonElement(body))
        assertEquals(discriminators, Regex("\"type\"\\s*:").findAll(body).count())
    }

    private fun <T> ApiResult<T>.success(): T = when (this) {
        is ApiResult.Success -> data
        is ApiResult.Failure -> error("Expected success, got $error")
    }

    private fun roundDetail(questions: String, total: Int, number: Int = 2, type: String = "recognition") =
        """{"id":"$ROUND_ID","lessonId":"lesson","roundNumber":$number,"type":"$type","title":"Round","status":"unlocked","stars":0,"bestScorePercent":null,"attempts":0,"totalQuestions":$total,"questions":[$questions]}"""

    private fun roundResult(score: Double, bestScore: Double?, stars: Int, bestStars: Int, results: String) =
        """{"roundId":"$ROUND_ID","totalQuestions":3,"correctCount":2,"scorePercent":$score,
        "passed":${score >= 70},"passThresholdPercent":70,"stars":$stars,"bestStars":$bestStars,"bestScorePercent":$bestScore,
        "isNewBest":false,"attempts":0,"results":[$results],"unlocks":{"nextRoundId":null,"topicCompleted":false,"nextTopicId":null,"levelCompleted":false,"nextLevelId":null},
        "streak":{"current":4,"lastLearningDate":"2026-11-19","learnedToday":false}}"""

    private fun vocabularyResult(status: String, isInFlashcard: Boolean) =
        """{"vocabularyId":"$VOCABULARY_ID","status":"$status","isInFlashcard":$isInFlashcard}"""

    private val questionResult =
        """{"questionId":"$QUESTION_ID","isCorrect":false,"correctAnswer":{"type":"multiple_choice","optionId":"o2"},"explanation":"Backend feedback"}"""

    private val learnCard =
        """{"type":"learn_card","id":"learn","order":1,"prompt":"Learn","imageUrl":null,"audioUrl":null,
        "vocabulary":{"id":"$VOCABULARY_ID","topicId":"topic","levelId":"level","word":"fish","meaning":"cá","partOfSpeech":"noun",
        "phonetic":"/fɪʃ/","example":null,"imageUrl":null,"audioUrl":"$AUDIO","status":"new","isInFlashcard":false}}"""

    private val multipleChoice =
        """{"type":"multiple_choice","id":"multiple","order":2,"prompt":"Pick","imageUrl":null,"audioUrl":null,"options":[{"id":"o2","text":"First"},{"id":"o1","text":"Second"}]}"""

    private val unknownQuestion =
        """{"type":"audio_choice","id":"future","order":3,"prompt":"Listen","imageUrl":"$IMAGE","audioUrl":"$AUDIO","futureField":{"items":[]}}"""

    private val pictureChoice =
        """{"type":"picture_choice","id":"picture","order":4,"prompt":"Pick","imageUrl":null,"audioUrl":null,"options":[{"id":"o2","imageUrl":"$IMAGE"},{"id":"o1","imageUrl":"https://media.example/second.png"}]}"""

    private companion object {
        const val ROUND_ID = "10000000-0000-0000-0000-000000000001"
        const val QUESTION_ID = "10000000-0000-0000-0000-000000000002"
        const val VOCABULARY_ID = "10000000-0000-0000-0000-000000000003"
        const val IMAGE = "https://media.example/fish.png"
        const val AUDIO = "https://media.example/fish.mp3"
    }
}
