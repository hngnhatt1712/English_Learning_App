package com.nhom21.english_learning_app.data.repository

import com.nhom21.english_learning_app.core.network.ApiErrorParser
import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiResponse
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.core.network.AuthInterceptor
import com.nhom21.english_learning_app.core.network.InMemoryPreferencesDataStore
import com.nhom21.english_learning_app.core.network.NetworkModule
import com.nhom21.english_learning_app.core.network.SessionEvents
import com.nhom21.english_learning_app.core.network.TokenManager
import com.nhom21.english_learning_app.data.remote.api.LevelApi
import com.nhom21.english_learning_app.data.remote.api.ProfileApi
import com.nhom21.english_learning_app.data.remote.dto.ProfileDto
import com.nhom21.english_learning_app.data.remote.dto.UpdateProfileRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class LevelProfileRepositoryTest {
    private val server = MockWebServer()
    private val json = NetworkModule.provideJson()
    private val parser = ApiErrorParser(json)
    private val tokenManager = TokenManager(InMemoryPreferencesDataStore())
    private val sessionEvents = SessionEvents()
    private val levelId = "10000000-0000-0000-0000-000000000001"
    private lateinit var levels: LevelRepositoryImpl
    private lateinit var profiles: ProfileRepositoryImpl

    @Before
    fun setUp() {
        server.start()
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenManager, sessionEvents, parser))
            .retryOnConnectionFailure(false)
            .readTimeout(1, TimeUnit.SECONDS)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        levels = LevelRepositoryImpl(retrofit.create(LevelApi::class.java), parser)
        profiles = ProfileRepositoryImpl(retrofit.create(ProfileApi::class.java), parser)
    }

    @After
    fun tearDown() { server.shutdown() }

    @Test
    fun `GET uses authenticated levels path and maps nullable description and locked status without filtering`() = runTest {
        tokenManager.saveToken(TEST_SESSION)
        server.enqueue(MockResponse().setBody(levelEnvelope))
        val result = levels.getLevels() as ApiResult.Success
        assertEquals(1, result.data.size)
        assertEquals(levelId, result.data.single().id)
        assertEquals("Backend level", result.data.single().name)
        assertNull(result.data.single().description)
        assertEquals(7, result.data.single().orderIndex)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/levels", request.path)
        assertTrue(request.getHeader("Authorization") == "Bearer $TEST_SESSION")
    }

    @Test
    fun `PUT sends only selectedLevelId with auth and maps server profile`() = runTest {
        tokenManager.saveToken(TEST_SESSION)
        server.enqueue(MockResponse().setBody(profileEnvelope))
        val result = profiles.selectLevel(levelId) as ApiResult.Success
        assertEquals(levelId, result.data.level?.id)
        assertEquals("Confirmed name", result.data.level?.name)
        assertEquals("learner", result.data.username)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/profile", request.path)
        assertTrue(request.getHeader("Authorization") == "Bearer $TEST_SESSION")
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(setOf("selectedLevelId"), body.keys)
        assertEquals(levelId, body["selectedLevelId"]!!.jsonPrimitive.content)
        assertTrue(tokenManager.accessToken.first() == TEST_SESSION)
    }

    @Test
    fun `empty list and null profile level are parsed without fabricated values`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":true,"message":"OK","data":{"items":[]},"error":null}"""))
        assertTrue((levels.getLevels() as ApiResult.Success).data.isEmpty())
        server.enqueue(MockResponse().setBody(profileEnvelope.replace(
            """{"id":"$levelId","name":"Confirmed name","stars":0,"maxStars":45,"progressPercent":0.0}""", "null")))
        assertNull((profiles.selectLevel(levelId) as ApiResult.Success).data.level)
    }

    @Test
    fun `both APIs parse error types and only unauthorized clears session`() = runTest {
        val cases = listOf(
            401 to ApiErrorType.UNAUTHORIZED,
            401 to ApiErrorType.INVALID_CREDENTIALS,
            403 to ApiErrorType.LEVEL_LOCKED,
            404 to ApiErrorType.NOT_FOUND,
            422 to ApiErrorType.VALIDATION_ERROR,
            500 to ApiErrorType.INTERNAL_ERROR,
            503 to ApiErrorType.UNKNOWN
        )
        for ((status, type) in cases) {
            for (isProfile in listOf(false, true)) {
                tokenManager.saveToken(TEST_SESSION)
                sessionEvents.resetSession()
                server.enqueue(MockResponse().setResponseCode(status).setBody(errorEnvelope(type)))
                val result = if (isProfile) profiles.selectLevel(levelId) else levels.getLevels()
                val error = (result as ApiResult.Failure).error
                assertEquals(type, error.type)
                assertEquals(status, error.httpStatus)
                if (type == ApiErrorType.VALIDATION_ERROR) {
                    assertEquals("selectedLevelId", error.fieldErrors.single().field)
                    assertEquals("INVALID_VALUE", error.fieldErrors.single().code)
                }
                assertEquals(type == ApiErrorType.UNAUTHORIZED, sessionEvents.isSessionExpired)
                if (type == ApiErrorType.UNAUTHORIZED) assertNull(tokenManager.accessToken.first())
                else assertTrue(tokenManager.accessToken.first() == TEST_SESSION)
                server.takeRequest()
            }
        }
    }

    @Test
    fun `invalid envelopes and malformed bodies fail safely for both APIs`() = runTest {
        for (body in listOf("not json", """{"status":true,"message":"OK","data":null,"error":null}""")) {
            server.enqueue(MockResponse().setBody(body))
            assertEquals(ApiErrorType.UNKNOWN, (levels.getLevels() as ApiResult.Failure).error.type)
            server.enqueue(MockResponse().setBody(body))
            assertEquals(ApiErrorType.UNKNOWN, (profiles.selectLevel(levelId) as ApiResult.Failure).error.type)
        }
        server.enqueue(MockResponse().setBody(errorEnvelope(ApiErrorType.LEVEL_LOCKED)))
        assertEquals(ApiErrorType.LEVEL_LOCKED, (profiles.selectLevel(levelId) as ApiResult.Failure).error.type)
    }

    @Test
    fun `timeout and network failure preserve token and allow retry`() = runTest {
        tokenManager.saveToken(TEST_SESSION)
        server.enqueue(MockResponse().setBody(levelEnvelope).setBodyDelay(2, TimeUnit.SECONDS))
        assertEquals(ApiErrorType.NETWORK, (levels.getLevels() as ApiResult.Failure).error.type)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertEquals(ApiErrorType.NETWORK, (profiles.selectLevel(levelId) as ApiResult.Failure).error.type)
        server.enqueue(MockResponse().setBody(profileEnvelope))
        assertTrue(profiles.selectLevel(levelId) is ApiResult.Success)
        assertTrue(tokenManager.accessToken.first() == TEST_SESSION)
        assertFalse(sessionEvents.isSessionExpired)
    }

    @Test
    fun `coroutine cancellation propagates through both repositories`() = runTest {
        val levelApi = object : LevelApi {
            override suspend fun getLevels(): Nothing = throw CancellationException("cancelled")
        }
        val profileApi = object : ProfileApi {
            override suspend fun getProfile(): ApiResponse<ProfileDto> = error("not used")
            override suspend fun updateProfile(request: UpdateProfileRequest): Nothing = throw CancellationException("cancelled")
        }
        try {
            LevelRepositoryImpl(levelApi, parser).getLevels()
            fail("Expected cancellation")
        } catch (_: CancellationException) { /* Expected. */ }
        try {
            ProfileRepositoryImpl(profileApi, parser).selectLevel(levelId)
            fail("Expected cancellation")
        } catch (_: CancellationException) { /* Expected. */ }
    }

    private val levelEnvelope get() = """{"status":true,"message":"OK","data":{"items":[
        {"id":"$levelId","name":"Backend level","description":null,"orderIndex":7,"status":"locked",
        "stars":0,"maxStars":45,"topicsCompleted":0,"topicsTotal":3,"futureField":true}]},"error":null}"""

    private val profileEnvelope get() = """{"status":true,"message":"OK","data":{
        "id":"20000000-0000-0000-0000-000000000001","username":"learner","email":"learner@example.test",
        "level":{"id":"$levelId","name":"Confirmed name","stars":0,"maxStars":45,"progressPercent":0.0},
        "totalStars":0,"maxStars":135,"currentStreak":0,"createdAt":"2026-11-13T00:00:00Z"},"error":null}"""

    private fun errorEnvelope(type: ApiErrorType) = """{"status":false,"message":"Error","data":null,
        "error":{"type":"${type.wireName}","detail":"Unavailable","traceId":"trace-test",
        "errors":${if (type == ApiErrorType.VALIDATION_ERROR) """[{"field":"selectedLevelId","message":"Invalid selection","code":"INVALID_VALUE"}]""" else "null"}}}"""

    private companion object {
        const val TEST_SESSION = "local-test-session"
    }
}
