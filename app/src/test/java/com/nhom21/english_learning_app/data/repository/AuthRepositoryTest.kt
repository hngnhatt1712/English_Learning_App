package com.nhom21.english_learning_app.data.repository

import com.nhom21.english_learning_app.core.network.ApiErrorParser
import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.core.network.AuthInterceptor
import com.nhom21.english_learning_app.core.network.InMemoryPreferencesDataStore
import com.nhom21.english_learning_app.core.network.SessionEvents
import com.nhom21.english_learning_app.core.network.TokenManager
import com.nhom21.english_learning_app.data.remote.api.AuthApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var mockWebServer: MockWebServer
    private lateinit var tokenManager: TokenManager
    private lateinit var sessionEvents: SessionEvents
    private lateinit var errorParser: ApiErrorParser
    private lateinit var authRepository: AuthRepositoryImpl

    private val sampleUserJson = """
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "username": "nhat_dao",
          "email": "nhat@example.com",
          "selectedLevelId": null,
          "currentStreak": 3,
          "lastLearningDate": "2026-10-04",
          "createdAt": "2026-10-04T10:30:00Z"
        }
    """.trimIndent()

    private val sampleAuthResultJson = """
        {
          "accessToken": "sample_access_jwt",
          "tokenType": "Bearer",
          "expiresIn": 604800,
          "user": $sampleUserJson
        }
    """.trimIndent()

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val testDataStore = InMemoryPreferencesDataStore()
        tokenManager = TokenManager(testDataStore)
        sessionEvents = SessionEvents()

        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = true
            encodeDefaults = false
            coerceInputValues = true
            classDiscriminator = "type"
        }
        errorParser = ApiErrorParser(json)

        val interceptor = AuthInterceptor(tokenManager, sessionEvents, errorParser)
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val authApi = retrofit.create(AuthApi::class.java)
        authRepository = AuthRepositoryImpl(authApi, tokenManager, errorParser)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun login_success_returnsSuccess_savesToken_verifiesRequestPathAndBody() = testScope.runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":true,"message":"OK","data":$sampleAuthResultJson,"error":null}""")
        )

        val result = authRepository.login("nhat@example.com", "Passw0rdOK")

        assertTrue(result is ApiResult.Success)
        val authResult = (result as ApiResult.Success).data
        assertEquals("sample_access_jwt", authResult.accessToken)
        assertEquals("nhat_dao", authResult.user.username)

        // Verify token saved in TokenManager
        assertEquals("sample_access_jwt", tokenManager.accessToken.first())

        // Verify request received by server
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/auth/login", recordedRequest.path)
        val body = recordedRequest.body.readUtf8()
        assertTrue(body.contains(""""identifier":"nhat@example.com""""))
        assertTrue(body.contains(""""password":"Passw0rdOK""""))
    }

    @Test
    fun login_401InvalidCredentials_returnsFailure_doesNotSaveToken_keepsOldToken() = testScope.runTest {
        tokenManager.saveToken("old_valid_token")
        val errorBody = """
            {
              "status": false,
              "message": "Invalid Credentials",
              "data": null,
              "error": {
                "type": "invalid-credentials",
                "detail": "Tài khoản hoặc mật khẩu không đúng",
                "traceId": "trace-login-fail",
                "errors": null
              }
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody(errorBody))

        val result = authRepository.login("nhat@example.com", "WrongPass")

        assertTrue(result is ApiResult.Failure)
        val failure = (result as ApiResult.Failure).error
        assertEquals(ApiErrorType.INVALID_CREDENTIALS, failure.type)
        assertEquals("Tài khoản hoặc mật khẩu không đúng", failure.message)
        assertEquals(401, failure.httpStatus)

        // Old token must be preserved
        assertEquals("old_valid_token", tokenManager.accessToken.first())
    }

    @Test
    fun register_201Success_returnsSuccess_savesToken_verifiesRequestPathAndBody() = testScope.runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"status":true,"message":"Created","data":$sampleAuthResultJson,"error":null}""")
        )

        val result = authRepository.register("nhat_dao", "nhat@example.com", "Passw0rdOK")

        assertTrue(result is ApiResult.Success)
        val authResult = (result as ApiResult.Success).data
        assertEquals("sample_access_jwt", authResult.accessToken)
        // Verify register saves token per D-C1
        assertEquals("sample_access_jwt", tokenManager.accessToken.first())

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/auth/register", recordedRequest.path)
        val body = recordedRequest.body.readUtf8()
        assertTrue(body.contains(""""username":"nhat_dao""""))
        assertTrue(body.contains(""""email":"nhat@example.com""""))
        assertTrue(body.contains(""""password":"Passw0rdOK""""))
    }

    @Test
    fun register_409EmailTaken_returnsFailureEmailTaken_keepsOldToken() = testScope.runTest {
        tokenManager.saveToken("old_valid_token")
        val errorBody = """
            {
              "status": false,
              "message": "Conflict",
              "data": null,
              "error": {
                "type": "email-taken",
                "detail": "Email already registered",
                "traceId": "trace-email",
                "errors": null
              }
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(409).setBody(errorBody))

        val result = authRepository.register("nhat_dao", "nhat@example.com", "Passw0rdOK")

        assertTrue(result is ApiResult.Failure)
        val failure = (result as ApiResult.Failure).error
        assertEquals(ApiErrorType.EMAIL_TAKEN, failure.type)
        assertEquals(409, failure.httpStatus)
        assertEquals("old_valid_token", tokenManager.accessToken.first())
    }

    @Test
    fun register_409UsernameTaken_returnsFailureUsernameTaken_keepsOldToken() = testScope.runTest {
        tokenManager.saveToken("old_valid_token")
        val errorBody = """
            {
              "status": false,
              "message": "Conflict",
              "data": null,
              "error": {
                "type": "username-taken",
                "detail": "Username already taken",
                "traceId": "trace-user",
                "errors": null
              }
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(409).setBody(errorBody))

        val result = authRepository.register("nhat_dao", "nhat@example.com", "Passw0rdOK")

        assertTrue(result is ApiResult.Failure)
        val failure = (result as ApiResult.Failure).error
        assertEquals(ApiErrorType.USERNAME_TAKEN, failure.type)
        assertEquals(409, failure.httpStatus)
        assertEquals("old_valid_token", tokenManager.accessToken.first())
    }

    @Test
    fun register_422ValidationError_returnsFieldErrors_keepsOldToken() = testScope.runTest {
        tokenManager.saveToken("old_valid_token")
        val errorBody = """
            {
              "status": false,
              "message": "Validation Error",
              "data": null,
              "error": {
                "type": "validation-error",
                "detail": "One or more fields failed validation",
                "traceId": "trace-422",
                "errors": [
                  { "field": "password", "message": "Must be at least 8 characters", "code": "MIN_LENGTH" }
                ]
              }
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(422).setBody(errorBody))

        val result = authRepository.register("u", "bad_email", "123")

        assertTrue(result is ApiResult.Failure)
        val failure = (result as ApiResult.Failure).error
        assertEquals(ApiErrorType.VALIDATION_ERROR, failure.type)
        assertEquals(1, failure.fieldErrors.size)
        assertEquals("password", failure.fieldErrors[0].field)
        assertEquals("old_valid_token", tokenManager.accessToken.first())
    }

    @Test
    fun register_networkDisconnect_returnsFailureNetwork_keepsOldToken() = testScope.runTest {
        tokenManager.saveToken("old_valid_token")
        mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val result = authRepository.register("nhat_dao", "nhat@example.com", "Passw0rdOK")

        assertTrue(result is ApiResult.Failure)
        val failure = (result as ApiResult.Failure).error
        assertEquals(ApiErrorType.NETWORK, failure.type)
        assertNull(failure.httpStatus)
        assertEquals("old_valid_token", tokenManager.accessToken.first())
    }

    @Test
    fun register_500NonEnvelope_returnsFailureUnknownPreservingHttpStatus_keepsOldToken() = testScope.runTest {
        tokenManager.saveToken("old_valid_token")
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"detail": "Crash in register worker"}""")
        )

        val result = authRepository.register("nhat_dao", "nhat@example.com", "Passw0rdOK")

        assertTrue(result is ApiResult.Failure)
        val failure = (result as ApiResult.Failure).error
        assertEquals(ApiErrorType.UNKNOWN, failure.type)
        assertEquals(500, failure.httpStatus)
        assertEquals("old_valid_token", tokenManager.accessToken.first())
    }

    @Test
    fun me_success_returnsUserDto() = testScope.runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":true,"message":"OK","data":$sampleUserJson,"error":null}""")
        )

        val result = authRepository.me()

        assertTrue(result is ApiResult.Success)
        val user = (result as ApiResult.Success).data
        assertEquals("nhat_dao", user.username)
        assertEquals("nhat@example.com", user.email)
        assertEquals(3, user.currentStreak)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/auth/me", recordedRequest.path)
    }

    @Test
    fun me_401Unauthorized_returnsFailureAndClearsToken() = testScope.runTest {
        tokenManager.saveToken("stale_token")
        val errorBody = """
            {
              "status": false,
              "message": "Unauthorized",
              "data": null,
              "error": {
                "type": "unauthorized",
                "detail": "Token expired",
                "traceId": "trace-me-401",
                "errors": null
              }
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody(errorBody))

        val result = authRepository.me()

        assertTrue(result is ApiResult.Failure)
        val failure = (result as ApiResult.Failure).error
        assertEquals(ApiErrorType.UNAUTHORIZED, failure.type)
        // Token must be cleared by interceptor
        assertNull(tokenManager.accessToken.first())
    }

    @Test
    fun networkDisconnect_returnsFailureNetwork() = testScope.runTest {
        mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val result = authRepository.me()

        assertTrue(result is ApiResult.Failure)
        val failure = (result as ApiResult.Failure).error
        assertEquals(ApiErrorType.NETWORK, failure.type)
        assertNull(failure.httpStatus)
    }

    @Test
    fun malformedResponseBody_returnsFailureUnknownWithoutCrashing() = testScope.runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"corrupt_json": true}""")
        )

        val result = authRepository.me()

        assertTrue(result is ApiResult.Failure)
        val failure = (result as ApiResult.Failure).error
        assertEquals(ApiErrorType.UNKNOWN, failure.type)
    }

    @Test
    fun nonEnvelopeServerError_returnsFailureUnknownPreservingHttpStatus() = testScope.runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"detail": "Crash in ASGI worker"}""")
        )

        val result = authRepository.me()

        assertTrue(result is ApiResult.Failure)
        val failure = (result as ApiResult.Failure).error
        assertEquals(ApiErrorType.UNKNOWN, failure.type)
        assertEquals("Crash in ASGI worker", failure.message)
        assertEquals(500, failure.httpStatus)
    }
}
