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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Unit tests (T17) cho [AuthRepositoryImpl] & [AuthApi] & [AuthInterceptor]
 * đối với 3 endpoint Quên mật khẩu:
 * - POST /auth/forgot-password
 * - POST /auth/forgot-password/verify-otp
 * - POST /auth/reset-password
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryForgotPasswordTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var appJson: Json
    private lateinit var tokenManager: TokenManager
    private lateinit var sessionEvents: SessionEvents
    private lateinit var errorParser: ApiErrorParser
    private lateinit var authRepository: AuthRepositoryImpl

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        appJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = true
            encodeDefaults = false
            coerceInputValues = true
            classDiscriminator = "type"
        }
        errorParser = ApiErrorParser(appJson)
        tokenManager = TokenManager(InMemoryPreferencesDataStore())
        sessionEvents = SessionEvents()

        val interceptor = AuthInterceptor(tokenManager, sessionEvents, errorParser)
        val okHttpClient = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .addInterceptor(interceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(appJson.asConverterFactory("application/json".toMediaType()))
            .build()

        val authApi = retrofit.create(AuthApi::class.java)
        authRepository = AuthRepositoryImpl(authApi, tokenManager, errorParser)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // 1. requestResetOtp 200 OK
    @Test
    fun `T17_1 requestResetOtp sends POST to auth forgot-password without Authorization header and parses retryAfterSeconds`() = runTest {
        tokenManager.saveToken("existing-token-should-not-be-sent")
        val responseBody = """
            {
              "status": true,
              "message": "If the email is registered, an OTP has been sent.",
              "data": {
                "retryAfterSeconds": 60
              },
              "error": null
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val result = authRepository.requestResetOtp("learner@example.com")

        assertTrue(result is ApiResult.Success)
        assertEquals(60, (result as ApiResult.Success).data.retryAfterSeconds)

        val recorded = mockWebServer.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/auth/forgot-password", recorded.path)
        assertNull("Public endpoint must not attach Authorization header", recorded.getHeader("Authorization"))

        val jsonBody = appJson.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals(setOf("email"), jsonBody.keys)
        assertEquals("learner@example.com", jsonBody["email"]?.jsonPrimitive?.content)
    }

    // 2. verifyResetOtp 200 OK
    @Test
    fun `T17_2 verifyResetOtp sends POST to auth forgot-password verify-otp and parses resetToken and expiresInSeconds`() = runTest {
        tokenManager.saveToken("existing-token-should-not-be-sent")
        val responseBody = """
            {
              "status": true,
              "message": "OTP verified.",
              "data": {
                "resetToken": "rt-opaque-token-123456",
                "expiresInSeconds": 600
              },
              "error": null
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val result = authRepository.verifyResetOtp("learner@example.com", "123456")

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals("rt-opaque-token-123456", data.resetToken)
        assertEquals(600, data.expiresInSeconds)

        val recorded = mockWebServer.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/auth/forgot-password/verify-otp", recorded.path)
        assertNull("Public endpoint must not attach Authorization header", recorded.getHeader("Authorization"))

        val jsonBody = appJson.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals(setOf("email", "otp"), jsonBody.keys)
        assertEquals("learner@example.com", jsonBody["email"]?.jsonPrimitive?.content)
        assertEquals("123456", jsonBody["otp"]?.jsonPrimitive?.content)
    }

    // 3. verifyResetOtp 400 invalid_otp
    @Test
    fun `T17_3 verifyResetOtp 400 invalid_otp maps to ApiErrorType INVALID_OTP and does not clear session`() = runTest {
        tokenManager.saveToken("persisted-token")
        val errorBody = """
            {
              "status": false,
              "message": "Bad Request",
              "data": null,
              "error": {
                "type": "invalid_otp",
                "detail": "Invalid or expired OTP.",
                "traceId": "trace-invalid-otp",
                "errors": null
              }
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(400).setBody(errorBody))

        val result = authRepository.verifyResetOtp("learner@example.com", "000000")

        assertTrue(result is ApiResult.Failure)
        val failure = (result as ApiResult.Failure).error
        assertEquals(ApiErrorType.INVALID_OTP, failure.type)
        assertEquals(400, failure.httpStatus)
        assertEquals("persisted-token", tokenManager.accessToken.first())
        assertFalse(sessionEvents.isSessionExpired)
    }

    // 4. resetPassword 200 OK with data = null
    @Test
    fun `T17_4 resetPassword sends POST to auth reset-password and parses 200 OK with null data`() = runTest {
        tokenManager.saveToken("existing-token-should-not-be-sent")
        val responseBody = """
            {
              "status": true,
              "message": "Password has been reset successfully.",
              "data": null,
              "error": null
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val result = authRepository.resetPassword("rt-opaque-token-123456", "NewPassw0rd1")

        assertTrue(result is ApiResult.Success)

        val recorded = mockWebServer.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/auth/reset-password", recorded.path)
        assertNull("Public endpoint must not attach Authorization header", recorded.getHeader("Authorization"))

        val jsonBody = appJson.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals(setOf("resetToken", "newPassword"), jsonBody.keys)
        assertEquals("rt-opaque-token-123456", jsonBody["resetToken"]?.jsonPrimitive?.content)
        assertEquals("NewPassw0rd1", jsonBody["newPassword"]?.jsonPrimitive?.content)
    }

    // 5. resetPassword 400 invalid_reset_token
    @Test
    fun `T17_5 resetPassword 400 invalid_reset_token maps to ApiErrorType INVALID_RESET_TOKEN and does not clear session`() = runTest {
        tokenManager.saveToken("persisted-token")
        val errorBody = """
            {
              "status": false,
              "message": "Bad Request",
              "data": null,
              "error": {
                "type": "invalid_reset_token",
                "detail": "Invalid or expired reset token.",
                "traceId": "trace-invalid-rt",
                "errors": null
              }
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(400).setBody(errorBody))

        val result = authRepository.resetPassword("expired-token", "NewPassw0rd1")

        assertTrue(result is ApiResult.Failure)
        val failure = (result as ApiResult.Failure).error
        assertEquals(ApiErrorType.INVALID_RESET_TOKEN, failure.type)
        assertEquals(400, failure.httpStatus)
        assertEquals("persisted-token", tokenManager.accessToken.first())
        assertFalse(sessionEvents.isSessionExpired)
    }

    // 6. 422 validation-error & IOException & 500 internal-error across forgot/reset endpoints
    @Test
    fun `T17_6 forgot and reset endpoints map 422 validation-error IOException and 500 internal-error accurately`() = runTest {
        // Case A: 422 validation-error on resetPassword
        val body422 = """
            {
              "status": false,
              "message": "Validation Error",
              "data": null,
              "error": {
                "type": "validation-error",
                "detail": "One or more fields failed validation",
                "traceId": "trace-422",
                "errors": [
                  { "field": "newPassword", "message": "Must contain a digit", "code": "INVALID_FORMAT" }
                ]
              }
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(422).setBody(body422))

        val res422 = authRepository.resetPassword("valid-token", "NoDigitsHere")
        assertTrue(res422 is ApiResult.Failure)
        val err422 = (res422 as ApiResult.Failure).error
        assertEquals(ApiErrorType.VALIDATION_ERROR, err422.type)
        assertEquals(422, err422.httpStatus)
        assertEquals(1, err422.fieldErrors.size)
        assertEquals("newPassword", err422.fieldErrors[0].field)
        assertEquals("INVALID_FORMAT", err422.fieldErrors[0].code)

        // Case B: IOException (network disconnect) on requestResetOtp
        mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        val resNet = authRepository.requestResetOtp("learner@example.com")
        assertTrue(resNet is ApiResult.Failure)
        assertEquals(ApiErrorType.NETWORK, (resNet as ApiResult.Failure).error.type)

        // Case C: 500 internal-error on verifyResetOtp
        val body500 = """
            {
              "status": false,
              "message": "Internal Server Error",
              "data": null,
              "error": {
                "type": "internal-error",
                "detail": "Unexpected server error",
                "traceId": "trace-500",
                "errors": null
              }
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody(body500))
        val res500 = authRepository.verifyResetOtp("learner@example.com", "123456")
        assertTrue(res500 is ApiResult.Failure)
        val err500 = (res500 as ApiResult.Failure).error
        assertEquals(ApiErrorType.INTERNAL_ERROR, err500.type)
        assertEquals(500, err500.httpStatus)
    }
}

