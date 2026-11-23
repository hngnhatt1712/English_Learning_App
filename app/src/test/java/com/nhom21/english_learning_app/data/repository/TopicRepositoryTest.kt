package com.nhom21.english_learning_app.data.repository

import com.nhom21.english_learning_app.core.network.ApiErrorParser
import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.remote.api.TopicApi
import com.nhom21.english_learning_app.domain.model.ContentStatus
import com.nhom21.english_learning_app.domain.model.RoundType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class TopicRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var json: Json
    private lateinit var errorParser: ApiErrorParser
    private lateinit var topicApi: TopicApi
    private lateinit var repository: TopicRepositoryImpl

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        json = Json {
            ignoreUnknownKeys = true
            explicitNulls = true
            encodeDefaults = false
            coerceInputValues = true
            classDiscriminator = "type"
        }
        errorParser = ApiErrorParser(json)

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        topicApi = retrofit.create(TopicApi::class.java)
        repository = TopicRepositoryImpl(topicApi, errorParser)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getTopics success returns mapped topic list`() = runTest {
        val responseBody = """
            {
              "status": true,
              "message": "OK",
              "data": {
                "items": [
                  {
                    "id": "t-1",
                    "levelId": "l-1",
                    "name": "Food & Drinks",
                    "description": "Culinary words",
                    "orderIndex": 1,
                    "status": "unlocked",
                    "stars": 5,
                    "maxStars": 15,
                    "roundsCompleted": 2,
                    "roundsTotal": 5,
                    "progressPercent": 40.0,
                    "themeKey": "food"
                  }
                ]
              },
              "error": null
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val result = repository.getTopics("l-1")

        assertTrue(result is ApiResult.Success)
        val topics = (result as ApiResult.Success).data
        assertEquals(1, topics.size)
        assertEquals("t-1", topics[0].id)
        assertEquals("Food & Drinks", topics[0].name)
        assertEquals(ContentStatus.UNLOCKED, topics[0].status)
        assertEquals("food", topics[0].themeKey)

        val recorded = mockWebServer.takeRequest()
        assertEquals("/topics?levelId=l-1", recorded.path)
    }

    @Test
    fun `getTopicDetail success returns mapped topic and lessons`() = runTest {
        val responseBody = """
            {
              "status": true,
              "message": "OK",
              "data": {
                "id": "t-1",
                "levelId": "l-1",
                "name": "Food & Drinks",
                "description": null,
                "orderIndex": 1,
                "status": "unlocked",
                "stars": 0,
                "maxStars": 15,
                "roundsCompleted": 0,
                "roundsTotal": 5,
                "progressPercent": 0.0,
                "themeKey": "default",
                "lessons": [
                  {
                    "id": "lesson-1",
                    "topicId": "t-1",
                    "title": "Lesson 1",
                    "description": null,
                    "orderIndex": 1
                  }
                ]
              },
              "error": null
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val result = repository.getTopicDetail("t-1")

        assertTrue(result is ApiResult.Success)
        val detail = (result as ApiResult.Success).data
        assertEquals("t-1", detail.topic.id)
        assertEquals(1, detail.lessons.size)
        assertEquals("lesson-1", detail.lessons[0].id)
    }

    @Test
    fun `getLesson success returns mapped lesson and rounds`() = runTest {
        val responseBody = """
            {
              "status": true,
              "message": "OK",
              "data": {
                "id": "lesson-1",
                "topicId": "t-1",
                "title": "Lesson 1",
                "description": null,
                "orderIndex": 1,
                "rounds": [
                  {
                    "id": "r-1",
                    "lessonId": "lesson-1",
                    "roundNumber": 1,
                    "type": "learn",
                    "title": "Flashcard learning",
                    "status": "completed",
                    "stars": 3,
                    "bestScorePercent": 100.0,
                    "attempts": 1,
                    "totalQuestions": 8
                  }
                ]
              },
              "error": null
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val result = repository.getLesson("lesson-1")

        assertTrue(result is ApiResult.Success)
        val detail = (result as ApiResult.Success).data
        assertEquals("lesson-1", detail.lesson.id)
        assertEquals(1, detail.rounds.size)
        assertEquals(RoundType.LEARN, detail.rounds[0].type)
        assertEquals(ContentStatus.COMPLETED, detail.rounds[0].status)
    }

    @Test
    fun `getLesson 403 topic-locked returns Failure with TOPIC_LOCKED`() = runTest {
        val errorJson = """
            {
              "status": false,
              "message": "Topic is locked",
              "data": null,
              "error": {
                "type": "topic-locked",
                "detail": "Topic has not been unlocked yet",
                "traceId": "trace-403",
                "errors": null
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody(errorJson))

        val result = repository.getLesson("locked-lesson")

        assertTrue(result is ApiResult.Failure)
        val failure = (result as ApiResult.Failure).error
        assertEquals(ApiErrorType.TOPIC_LOCKED, failure.type)
        assertEquals("Topic has not been unlocked yet", failure.message)
        assertEquals(403, failure.httpStatus)
    }

    @Test
    fun `getTopicDetail 404 not-found returns Failure with NOT_FOUND`() = runTest {
        val errorJson = """
            {
              "status": false,
              "message": "Topic not found",
              "data": null,
              "error": {
                "type": "not-found",
                "detail": "Requested topic not found",
                "traceId": "trace-404",
                "errors": null
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(404).setBody(errorJson))

        val result = repository.getTopicDetail("unknown-id")

        assertTrue(result is ApiResult.Failure)
        val failure = (result as ApiResult.Failure).error
        assertEquals(ApiErrorType.NOT_FOUND, failure.type)
        assertEquals("Requested topic not found", failure.message)
        assertEquals(404, failure.httpStatus)
    }

    @Test
    fun `getTopics 422 validation-error returns Failure with VALIDATION_ERROR and fieldErrors`() = runTest {
        val errorJson = """
            {
              "status": false,
              "message": "Validation Error",
              "data": null,
              "error": {
                "type": "validation-error",
                "detail": "One or more fields failed validation",
                "traceId": "trace-422",
                "errors": [
                  {
                    "field": "levelId",
                    "message": "Field required",
                    "code": "REQUIRED"
                  }
                ]
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(422).setBody(errorJson))

        val result = repository.getTopics("")

        assertTrue(result is ApiResult.Failure)
        val failure = (result as ApiResult.Failure).error
        assertEquals(ApiErrorType.VALIDATION_ERROR, failure.type)
        assertEquals(1, failure.fieldErrors.size)
        assertEquals("levelId", failure.fieldErrors[0].field)
        assertEquals("Field required", failure.fieldErrors[0].message)
    }

    @Test
    fun `network disconnection returns Failure with NETWORK type`() = runTest {
        mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val result = repository.getTopics("level-1")

        assertTrue(result is ApiResult.Failure)
        val failure = (result as ApiResult.Failure).error
        assertEquals(ApiErrorType.NETWORK, failure.type)
    }

    @Test
    fun `cancellationException is not swallowed and rethrown`() = runTest(UnconfinedTestDispatcher()) {
        mockWebServer.enqueue(
            MockResponse()
                .setBodyDelay(2, TimeUnit.SECONDS)
                .setBody("""{"status":true,"message":"OK","data":{"items":[]},"error":null}""")
        )

        var caughtCancellation = false
        val job = launch {
            try {
                repository.getTopics("l-1")
            } catch (e: CancellationException) {
                caughtCancellation = true
                throw e
            }
        }

        job.cancel()
        assertTrue("CancellationException must be rethrown", caughtCancellation)
    }
}

