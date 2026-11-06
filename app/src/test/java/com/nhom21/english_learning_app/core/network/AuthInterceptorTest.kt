package com.nhom21.english_learning_app.core.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthInterceptorTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var mockWebServer: MockWebServer
    private lateinit var tokenManager: TokenManager
    private lateinit var sessionEvents: SessionEvents
    private lateinit var errorParser: ApiErrorParser
    private lateinit var client: OkHttpClient

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
            coerceInputValues = true
        }
        errorParser = ApiErrorParser(json)

        val interceptor = AuthInterceptor(tokenManager, sessionEvents, errorParser)
        client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun intercept_withToken_attachesBearerHeaderToRegularRequest() = testScope.runTest {
        tokenManager.saveToken("valid_bearer_token")
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":true,"message":"OK","data":null,"error":null}"""))

        val request = Request.Builder()
            .url(mockWebServer.url("/api/v1/users/profile"))
            .build()

        client.newCall(request).execute().close()

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("Bearer valid_bearer_token", recordedRequest.getHeader("Authorization"))
    }

    @Test
    fun intercept_withoutToken_doesNotAttachAuthorizationHeader() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":true,"message":"OK","data":null,"error":null}"""))

        val request = Request.Builder()
            .url(mockWebServer.url("/api/v1/users/profile"))
            .build()

        client.newCall(request).execute().close()

        val recordedRequest = mockWebServer.takeRequest()
        assertNull(recordedRequest.getHeader("Authorization"))
    }

    @Test
    fun intercept_authLoginAndRegister_doesNotAttachHeaderEvenWithToken() = testScope.runTest {
        tokenManager.saveToken("existing_token")
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":true,"message":"OK","data":null,"error":null}"""))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":true,"message":"OK","data":null,"error":null}"""))

        // Login request
        val loginRequest = Request.Builder()
            .url(mockWebServer.url("/api/v1/auth/login"))
            .build()
        client.newCall(loginRequest).execute().close()

        val recordedLogin = mockWebServer.takeRequest()
        assertNull(recordedLogin.getHeader("Authorization"))

        // Register request
        val registerRequest = Request.Builder()
            .url(mockWebServer.url("/api/v1/auth/register"))
            .build()
        client.newCall(registerRequest).execute().close()

        val recordedRegister = mockWebServer.takeRequest()
        assertNull(recordedRegister.getHeader("Authorization"))
    }

    @Test
    fun intercept_401Unauthorized_clearsTokenAndNotifiesSessionExpired() = testScope.runTest {
        tokenManager.saveToken("expired_token")
        val errorBody = """
            {
              "status": false,
              "message": "Unauthorized",
              "data": null,
              "error": {
                "type": "unauthorized",
                "detail": "Token expired",
                "traceId": "trace-401",
                "errors": null
              }
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody(errorBody))

        val emittedEvents = mutableListOf<Unit>()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sessionEvents.sessionExpired.collect { emittedEvents.add(it) }
        }

        val request = Request.Builder()
            .url(mockWebServer.url("/api/v1/auth/me"))
            .build()

        client.newCall(request).execute().close()

        // Token must be cleared
        assertNull(tokenManager.accessToken.first())
        // SessionEvents must have emitted to active collector and marked state
        assertTrue(sessionEvents.isSessionExpired)
        assertEquals(1, emittedEvents.size)
        collectJob.cancel()
    }

    @Test
    fun intercept_401InvalidCredentials_keepsTokenAndDoesNotNotifySessionExpired() = testScope.runTest {
        tokenManager.saveToken("persisted_token")
        val errorBody = """
            {
              "status": false,
              "message": "Invalid Credentials",
              "data": null,
              "error": {
                "type": "invalid-credentials",
                "detail": "Wrong password",
                "traceId": "trace-login-fail",
                "errors": null
              }
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody(errorBody))

        val emittedEvents = mutableListOf<Unit>()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sessionEvents.sessionExpired.collect { emittedEvents.add(it) }
        }

        val request = Request.Builder()
            .url(mockWebServer.url("/api/v1/auth/login"))
            .build()

        client.newCall(request).execute().close()

        // Token must NOT be cleared (Contract 4.2)
        assertEquals("persisted_token", tokenManager.accessToken.first())
        // No sessionExpired event
        assertFalse(sessionEvents.isSessionExpired)
        assertEquals(0, emittedEvents.size)
        collectJob.cancel()
    }

    @Test
    fun intercept_403And500_keepsToken() = testScope.runTest {
        tokenManager.saveToken("valid_token")
        mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody("""{"status":false,"message":"Forbidden","data":null,"error":{"type":"forbidden","detail":"Forbidden","traceId":"t1","errors":null}}"""))
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("""{"status":false,"message":"Error","data":null,"error":{"type":"internal-error","detail":"Crash","traceId":"t2","errors":null}}"""))

        val req403 = Request.Builder().url(mockWebServer.url("/api/v1/levels")).build()
        client.newCall(req403).execute().close()
        assertEquals("valid_token", tokenManager.accessToken.first())

        val req500 = Request.Builder().url(mockWebServer.url("/api/v1/topics")).build()
        client.newCall(req500).execute().close()
        assertEquals("valid_token", tokenManager.accessToken.first())
    }

    @Test
    fun intercept_afterPeekBody_responseBodyRemainsFullyReadableByCaller() {
        val sampleBody = """{"status":false,"message":"Unauthorized","data":null,"error":{"type":"unauthorized","detail":"Expired","traceId":"t3","errors":null}}"""
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody(sampleBody))

        val request = Request.Builder()
            .url(mockWebServer.url("/api/v1/auth/me"))
            .build()

        val response = client.newCall(request).execute()
        val readString = response.body?.string()

        assertEquals(sampleBody, readString)
        response.close()
    }
}
