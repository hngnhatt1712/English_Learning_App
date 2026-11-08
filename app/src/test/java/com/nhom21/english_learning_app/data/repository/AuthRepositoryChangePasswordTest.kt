package com.nhom21.english_learning_app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.nhom21.english_learning_app.core.network.ApiErrorParser
import com.nhom21.english_learning_app.core.network.ApiResponse
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.core.network.AuthInterceptor
import com.nhom21.english_learning_app.core.network.InMemoryPreferencesDataStore
import com.nhom21.english_learning_app.core.network.SessionEvents
import com.nhom21.english_learning_app.core.network.TokenManager
import com.nhom21.english_learning_app.data.remote.api.AuthApi
import com.nhom21.english_learning_app.data.remote.dto.AuthResultDto
import com.nhom21.english_learning_app.data.remote.dto.ChangePasswordRequest
import com.nhom21.english_learning_app.data.remote.dto.LoginRequest
import com.nhom21.english_learning_app.data.remote.dto.LogoutRequest
import com.nhom21.english_learning_app.data.remote.dto.RegisterRequest
import com.nhom21.english_learning_app.data.remote.dto.UserDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryChangePasswordTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var appJson: Json
    private lateinit var errorParser: ApiErrorParser

    private val sampleUserDto = UserDto(
        id = "11111111-1111-1111-1111-111111111111",
        username = "nhat_dao",
        email = "nhat@example.com",
        selectedLevelId = null,
        currentStreak = 3,
        lastLearningDate = "2026-10-04",
        createdAt = "2026-10-04T10:30:00Z"
    )

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
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // 1. AuthApi.changePassword with MockWebServer & app Json
    @Test
    fun `AuthApi changePassword sends PUT to auth change-password with camelCase fields and parses 200 AuthResultDto`() = runTest {
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(appJson.asConverterFactory("application/json".toMediaType()))
            .build()
        val authApi = retrofit.create(AuthApi::class.java)

        val responseJson = """
            {
              "status": true,
              "message": "Password changed",
              "data": {
                "accessToken": "fresh-jwt-token",
                "tokenType": "Bearer",
                "expiresIn": 604800,
                "user": $sampleUserJson
              },
              "error": null
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val response = authApi.changePassword(
            ChangePasswordRequest(
                currentPassword = "OldPassword1",
                newPassword = "NewPassword2"
            )
        )

        val recorded = mockWebServer.takeRequest()
        assertEquals("PUT", recorded.method)
        assertEquals("/auth/change-password", recorded.path)

        val parsedBody = appJson.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals(setOf("currentPassword", "newPassword"), parsedBody.keys)
        assertEquals("OldPassword1", parsedBody["currentPassword"]?.jsonPrimitive?.content)
        assertEquals("NewPassword2", parsedBody["newPassword"]?.jsonPrimitive?.content)

        assertTrue(response.status)
        assertNotNull(response.data)
        assertEquals("fresh-jwt-token", response.data?.accessToken)
        assertEquals("Bearer", response.data?.tokenType)
        assertEquals(604800L, response.data?.expiresIn)
        assertEquals("nhat_dao", response.data?.user?.username)
    }

    // 2. AuthRepository.changePassword() success saves new token BEFORE returning
    @Test
    fun `changePassword success saves new accessToken into TokenManager before returning Success`() = runTest {
        val writeGate = CompletableDeferred<Unit>()
        val gatedDataStore = GatedPreferencesDataStore(writeGate)
        val tokenManager = TokenManager(gatedDataStore)
        // Initial save is ungated
        writeGate.complete(Unit)
        tokenManager.saveToken("old-token")

        val secondWriteGate = CompletableDeferred<Unit>()
        gatedDataStore.gate = secondWriteGate

        val fakeApi = FakeAuthApi {
            ApiResponse(
                status = true,
                message = "Password changed",
                data = AuthResultDto(
                    accessToken = "new-token-after-change",
                    tokenType = "Bearer",
                    expiresIn = 604800,
                    user = sampleUserDto
                )
            )
        }
        val repository = AuthRepositoryImpl(fakeApi, tokenManager, errorParser)

        val deferredResult = async {
            repository.changePassword("OldPassword1", "NewPassword2")
        }
        runCurrent()

        // While DataStore write is still suspended, changePassword must not have returned yet
        assertFalse(deferredResult.isCompleted)

        secondWriteGate.complete(Unit)
        val result = deferredResult.await()

        assertEquals(ChangePasswordResult.Success, result)
        assertEquals("new-token-after-change", tokenManager.currentTokenBlocking())
        assertEquals("new-token-after-change", tokenManager.accessToken.first())
        assertEquals(
            ChangePasswordRequest(currentPassword = "OldPassword1", newPassword = "NewPassword2"),
            fakeApi.lastChangePasswordRequest
        )
    }

    // 3. HTTP 400 wrong-password -> WrongCurrentPassword, does not overwrite token
    @Test
    fun `changePassword 400 wrong-password returns WrongCurrentPassword and preserves old token`() = runTest {
        val dataStore = CountingPreferencesDataStore()
        val tokenManager = TokenManager(dataStore)
        tokenManager.saveToken("old-token")
        dataStore.resetUpdateCount()

        val errorBody = """
            {
              "status": false,
              "message": "Wrong Password",
              "data": null,
              "error": {
                "type": "wrong-password",
                "detail": "Current password is incorrect",
                "traceId": "trace-wrong-pw",
                "errors": null
              }
            }
        """.trimIndent()
        val fakeApi = FakeAuthApi {
            throw HttpException(Response.error<ApiResponse<AuthResultDto>>(400, errorBody.toResponseBody()))
        }
        val repository = AuthRepositoryImpl(fakeApi, tokenManager, errorParser)

        val result = repository.changePassword("WrongOld1", "NewPassword2")

        assertEquals(ChangePasswordResult.WrongCurrentPassword, result)
        assertEquals(0, dataStore.updateCount)
        assertEquals("old-token", tokenManager.accessToken.first())
    }

    // 4. HTTP 422 validation-error with errors[] -> ValidationError(field -> code), duplicate field keeps first error
    @Test
    fun `changePassword 422 with errors maps field to code keeping first error per field and preserves old token`() = runTest {
        val dataStore = CountingPreferencesDataStore()
        val tokenManager = TokenManager(dataStore)
        tokenManager.saveToken("old-token")
        dataStore.resetUpdateCount()

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
                  { "field": "newPassword", "message": "Must be at least 8 characters", "code": "MIN_LENGTH" },
                  { "field": "newPassword", "message": "Must contain a digit", "code": "INVALID_FORMAT" },
                  { "field": "currentPassword", "message": "Field is required", "code": "REQUIRED" }
                ]
              }
            }
        """.trimIndent()
        val fakeApi = FakeAuthApi {
            throw HttpException(Response.error<ApiResponse<AuthResultDto>>(422, errorBody.toResponseBody()))
        }
        val repository = AuthRepositoryImpl(fakeApi, tokenManager, errorParser)

        val result = repository.changePassword("", "short")

        assertTrue(result is ChangePasswordResult.ValidationError)
        val validationError = result as ChangePasswordResult.ValidationError
        assertEquals(
            mapOf(
                "newPassword" to "MIN_LENGTH",
                "currentPassword" to "REQUIRED"
            ),
            validationError.fieldErrors
        )
        assertEquals(0, dataStore.updateCount)
        assertEquals("old-token", tokenManager.accessToken.first())
    }

    // 5. HTTP 422 validation-error without errors[] -> ValidationError(emptyMap())
    @Test
    fun `changePassword 422 without errors returns ValidationError with emptyMap and preserves old token`() = runTest {
        val dataStore = CountingPreferencesDataStore()
        val tokenManager = TokenManager(dataStore)
        tokenManager.saveToken("old-token")
        dataStore.resetUpdateCount()

        val errorBody = """
            {
              "status": false,
              "message": "Validation Error",
              "data": null,
              "error": {
                "type": "validation-error",
                "detail": "Validation failed",
                "traceId": "trace-422-empty",
                "errors": null
              }
            }
        """.trimIndent()
        val fakeApi = FakeAuthApi {
            throw HttpException(Response.error<ApiResponse<AuthResultDto>>(422, errorBody.toResponseBody()))
        }
        val repository = AuthRepositoryImpl(fakeApi, tokenManager, errorParser)

        val result = repository.changePassword("OldPassword1", "NewPassword2")

        assertEquals(ChangePasswordResult.ValidationError(emptyMap()), result)
        assertEquals(0, dataStore.updateCount)
        assertEquals("old-token", tokenManager.accessToken.first())
    }

    // 6. HTTP 401 unauthorized -> SessionExpired, repository does not overwrite or clear token directly
    @Test
    fun `changePassword 401 unauthorized returns SessionExpired without repository writing or clearing token`() = runTest {
        val dataStore = CountingPreferencesDataStore()
        val tokenManager = TokenManager(dataStore)
        tokenManager.saveToken("old-token")
        dataStore.resetUpdateCount()

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
        val fakeApi = FakeAuthApi {
            throw HttpException(Response.error<ApiResponse<AuthResultDto>>(401, errorBody.toResponseBody()))
        }
        val repository = AuthRepositoryImpl(fakeApi, tokenManager, errorParser)

        val result = repository.changePassword("OldPassword1", "NewPassword2")

        assertEquals(ChangePasswordResult.SessionExpired, result)
        assertEquals(0, dataStore.updateCount)
        assertEquals("old-token", tokenManager.accessToken.first())
    }

    // 7. IOException -> NetworkError, does not overwrite token
    @Test
    fun `changePassword IOException returns NetworkError and preserves old token`() = runTest {
        val dataStore = CountingPreferencesDataStore()
        val tokenManager = TokenManager(dataStore)
        tokenManager.saveToken("old-token")
        dataStore.resetUpdateCount()

        val fakeApi = FakeAuthApi { throw IOException("Socket closed") }
        val repository = AuthRepositoryImpl(fakeApi, tokenManager, errorParser)

        val result = repository.changePassword("OldPassword1", "NewPassword2")

        assertEquals(ChangePasswordResult.NetworkError, result)
        assertEquals(0, dataStore.updateCount)
        assertEquals("old-token", tokenManager.accessToken.first())
    }

    // 8. HTTP 200 with data == null or blank accessToken -> Unknown, does not overwrite token
    @Test
    fun `changePassword 200 with null data or blank accessToken returns Unknown and preserves old token`() = runTest {
        val dataStore = CountingPreferencesDataStore()
        val tokenManager = TokenManager(dataStore)
        tokenManager.saveToken("old-token")
        dataStore.resetUpdateCount()

        var responseToReturn = ApiResponse<AuthResultDto>(
            status = true,
            message = "OK",
            data = null
        )
        val fakeApi = FakeAuthApi { responseToReturn }
        val repository = AuthRepositoryImpl(fakeApi, tokenManager, errorParser)

        val nullDataResult = repository.changePassword("OldPassword1", "NewPassword2")
        assertTrue(nullDataResult is ChangePasswordResult.Unknown)
        assertEquals(0, dataStore.updateCount)
        assertEquals("old-token", tokenManager.accessToken.first())

        responseToReturn = ApiResponse(
            status = true,
            message = "OK",
            data = AuthResultDto(
                accessToken = "   ",
                tokenType = "Bearer",
                expiresIn = 604800,
                user = sampleUserDto
            )
        )
        val blankTokenResult = repository.changePassword("OldPassword1", "NewPassword2")
        assertTrue(blankTokenResult is ChangePasswordResult.Unknown)
        assertEquals(0, dataStore.updateCount)
        assertEquals("old-token", tokenManager.accessToken.first())
    }

    // 9. Coroutine cancellation -> CancellationException is rethrown
    @Test
    fun `changePassword coroutine cancellation rethrows CancellationException`() = runTest {
        val dataStore = CountingPreferencesDataStore()
        val tokenManager = TokenManager(dataStore)
        tokenManager.saveToken("old-token")
        dataStore.resetUpdateCount()

        val fakeApi = FakeAuthApi { awaitCancellation() }
        val repository = AuthRepositoryImpl(fakeApi, tokenManager, errorParser)

        val job = async { repository.changePassword("OldPassword1", "NewPassword2") }
        runCurrent()
        job.cancelAndJoin()

        assertEquals(0, dataStore.updateCount)
        assertEquals("old-token", tokenManager.accessToken.first())
        try {
            job.await()
            fail("Expected CancellationException to be rethrown")
        } catch (_: CancellationException) {
            // Expected
        }
    }

    // 10. AuthInterceptor + OkHttpClient + MockWebServer single client: changePassword sends old-token, next me() sends new-token
    @Test
    fun `next authenticated request on same OkHttpClient uses new token after changePassword succeeds`() = runTest {
        val tokenManager = TokenManager(InMemoryPreferencesDataStore())
        tokenManager.saveToken("old-token")
        val sessionEvents = SessionEvents()
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
        val repository = AuthRepositoryImpl(authApi, tokenManager, errorParser)

        val changePwResponse = """
            {
              "status": true,
              "message": "Password changed",
              "data": {
                "accessToken": "new-token",
                "tokenType": "Bearer",
                "expiresIn": 604800,
                "user": $sampleUserJson
              },
              "error": null
            }
        """.trimIndent()
        val meResponse = """
            {
              "status": true,
              "message": "OK",
              "data": $sampleUserJson,
              "error": null
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(changePwResponse))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(meResponse))

        val changeResult = repository.changePassword("OldPassword1", "NewPassword2")
        assertEquals(ChangePasswordResult.Success, changeResult)

        val meResult = repository.me()
        assertTrue(meResult is ApiResult.Success)

        val firstRequest = mockWebServer.takeRequest()
        assertEquals("/auth/change-password", firstRequest.path)
        assertEquals("Bearer old-token", firstRequest.getHeader("Authorization"))

        val secondRequest = mockWebServer.takeRequest()
        assertEquals("/auth/me", secondRequest.path)
        assertEquals("Bearer new-token", secondRequest.getHeader("Authorization"))
    }

    // 11. When changePassword fails (400, 422, IOException), next request on same OkHttpClient still sends old-token
    @Test
    fun `next authenticated request on same OkHttpClient still sends old-token when changePassword fails with 400 422 or IOException`() = runTest {
        val tokenManager = TokenManager(InMemoryPreferencesDataStore())
        tokenManager.saveToken("old-token")
        val sessionEvents = SessionEvents()
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
        val repository = AuthRepositoryImpl(authApi, tokenManager, errorParser)

        val meResponse = """{"status":true,"message":"OK","data":$sampleUserJson,"error":null}"""

        // Case A: 400 wrong-password
        val body400 = """
            {
              "status": false,
              "message": "Wrong Password",
              "data": null,
              "error": {
                "type": "wrong-password",
                "detail": "Current password is incorrect",
                "traceId": "trace-400",
                "errors": null
              }
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(400).setBody(body400))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(meResponse))

        assertEquals(ChangePasswordResult.WrongCurrentPassword, repository.changePassword("BadOld12", "NewPassword2"))
        assertTrue(repository.me() is ApiResult.Success)
        assertEquals("Bearer old-token", mockWebServer.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer old-token", mockWebServer.takeRequest().getHeader("Authorization"))

        // Case B: 422 validation-error
        val body422 = """
            {
              "status": false,
              "message": "Validation Error",
              "data": null,
              "error": {
                "type": "validation-error",
                "detail": "Invalid",
                "traceId": "trace-422",
                "errors": [{ "field": "newPassword", "message": "Invalid", "code": "INVALID_FORMAT" }]
              }
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(422).setBody(body422))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(meResponse))

        assertTrue(repository.changePassword("OldPassword1", "bad") is ChangePasswordResult.ValidationError)
        assertTrue(repository.me() is ApiResult.Success)
        assertEquals("Bearer old-token", mockWebServer.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer old-token", mockWebServer.takeRequest().getHeader("Authorization"))

        // Case C: IOException (disconnect)
        mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(meResponse))

        assertEquals(ChangePasswordResult.NetworkError, repository.changePassword("OldPassword1", "NewPassword2"))
        assertTrue(repository.me() is ApiResult.Success)
        mockWebServer.takeRequest() // failed change-password attempt
        assertEquals("Bearer old-token", mockWebServer.takeRequest().getHeader("Authorization"))
    }

    private class FakeAuthApi(
        private val changePasswordBlock: suspend (ChangePasswordRequest) -> ApiResponse<AuthResultDto>
    ) : AuthApi {
        var lastChangePasswordRequest: ChangePasswordRequest? = null
            private set

        override suspend fun changePassword(request: ChangePasswordRequest): ApiResponse<AuthResultDto> {
            lastChangePasswordRequest = request
            return changePasswordBlock(request)
        }

        override suspend fun register(request: RegisterRequest): ApiResponse<AuthResultDto> = error("Unused")
        override suspend fun login(request: LoginRequest): ApiResponse<AuthResultDto> = error("Unused")
        override suspend fun me(): ApiResponse<UserDto> = error("Unused")
        override suspend fun logout(request: LogoutRequest): ApiResponse<Unit?> = error("Unused")
    }

    private class CountingPreferencesDataStore : DataStore<Preferences> {
        private val backing = InMemoryPreferencesDataStore()
        override val data: Flow<Preferences> = backing.data
        var updateCount = 0
            private set

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            updateCount++
            return backing.updateData(transform)
        }

        fun resetUpdateCount() {
            updateCount = 0
        }
    }

    private class GatedPreferencesDataStore(
        var gate: CompletableDeferred<Unit>
    ) : DataStore<Preferences> {
        private val backing = InMemoryPreferencesDataStore()
        override val data: Flow<Preferences> = backing.data

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            gate.await()
            return backing.updateData(transform)
        }
    }
}

