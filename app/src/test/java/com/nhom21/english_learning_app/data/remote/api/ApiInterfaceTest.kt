package com.nhom21.english_learning_app.data.remote.api

import com.nhom21.english_learning_app.core.network.ApiResponse
import com.nhom21.english_learning_app.core.network.ListData
import com.nhom21.english_learning_app.data.remote.dto.AuthResultDto
import com.nhom21.english_learning_app.data.remote.dto.ContentStatus
import com.nhom21.english_learning_app.data.remote.dto.LoginRequest
import com.nhom21.english_learning_app.data.remote.dto.LogoutRequest
import com.nhom21.english_learning_app.data.remote.dto.ProfileDto
import com.nhom21.english_learning_app.data.remote.dto.RegisterRequest
import com.nhom21.english_learning_app.data.remote.dto.UpdateProfileRequest
import com.nhom21.english_learning_app.data.remote.dto.UserDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Unit tests for [AuthApi], [LevelApi], and [ProfileApi] using MockWebServer.
 *
 * JSON configuration mirrors [NetworkModule.provideJson] exactly (contract 11.1):
 *   ignoreUnknownKeys = true, explicitNulls = true, encodeDefaults = false, coerceInputValues = true.
 *
 * No live server, no backend dependency.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ApiInterfaceTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var mockWebServer: MockWebServer
    private lateinit var json: Json
    private lateinit var authApi: AuthApi
    private lateinit var levelApi: LevelApi
    private lateinit var profileApi: ProfileApi

    // ── Fixture JSON ────────────────────────────────────────────────────────────
    private val userJsonNullLevel = """
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "username": "nhat_dao",
          "email": "nhat@example.com",
          "selectedLevelId": null,
          "currentStreak": 3,
          "lastLearningDate": null,
          "createdAt": "2026-10-04T10:30:00Z"
        }
    """.trimIndent()

    private val authResultJson = """
        {
          "accessToken": "eyJsample",
          "tokenType": "Bearer",
          "expiresIn": 604800,
          "user": $userJsonNullLevel
        }
    """.trimIndent()

    private val levelItemsJson = """
        [
          {
            "id": "aaaa0000-0000-0000-0000-000000000001",
            "name": "Beginner (A1)",
            "description": null,
            "orderIndex": 1,
            "status": "unlocked",
            "stars": 0,
            "maxStars": 45,
            "topicsCompleted": 0,
            "topicsTotal": 3
          },
          {
            "id": "aaaa0000-0000-0000-0000-000000000002",
            "name": "Elementary (A2)",
            "description": "For elementary learners",
            "orderIndex": 2,
            "status": "locked",
            "stars": 0,
            "maxStars": 45,
            "topicsCompleted": 0,
            "topicsTotal": 3
          },
          {
            "id": "aaaa0000-0000-0000-0000-000000000003",
            "name": "Intermediate (B1)",
            "description": null,
            "orderIndex": 3,
            "status": "locked",
            "stars": 0,
            "maxStars": 45,
            "topicsCompleted": 0,
            "topicsTotal": 3
          }
        ]
    """.trimIndent()

    private val levelBriefJson = """
        {
          "id": "aaaa0000-0000-0000-0000-000000000001",
          "name": "Beginner (A1)",
          "stars": 7,
          "maxStars": 45,
          "progressPercent": 15.6
        }
    """.trimIndent()

    private val profileWithLevelJson = """
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "username": "nhat_dao",
          "email": "nhat@example.com",
          "level": $levelBriefJson,
          "totalStars": 7,
          "maxStars": 135,
          "currentStreak": 3,
          "createdAt": "2026-10-04T10:30:00Z"
        }
    """.trimIndent()

    private val profileNoLevelJson = """
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "username": "nhat_dao",
          "email": "nhat@example.com",
          "level": null,
          "totalStars": 0,
          "maxStars": 135,
          "currentStreak": 0,
          "createdAt": "2026-10-04T10:30:00Z"
        }
    """.trimIndent()

    // ── Setup / Teardown ─────────────────────────────────────────────────────────
    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        // Exactly matches NetworkModule.provideJson() per contract 11.1
        json = Json {
            ignoreUnknownKeys = true
            explicitNulls = true
            encodeDefaults = false
            coerceInputValues = true
            classDiscriminator = "type"
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        authApi = retrofit.create(AuthApi::class.java)
        levelApi = retrofit.create(LevelApi::class.java)
        profileApi = retrofit.create(ProfileApi::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // AuthApi Tests
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Test 1: POST /auth/register success.
     * Verifies: HTTP 201, parse AuthResultDto, null nullable fields, request body and path.
     */
    @Test
    fun authApi_register_success_parsesAuthResultDtoAndVerifiesRequest() = testScope.runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"status":true,"message":"Created","data":$authResultJson,"error":null}""")
        )

        val response = authApi.register(
            RegisterRequest(username = "nhat_dao", email = "nhat@example.com", password = "Passw0rdOK")
        )

        // Parse correctness
        assertTrue(response.status)
        val data: AuthResultDto = checkNotNull(response.data)
        assertEquals("eyJsample", data.accessToken)
        assertEquals("Bearer", data.tokenType)
        assertEquals(604800L, data.expiresIn)

        // UserDto with null optional fields
        val user: UserDto = data.user
        assertEquals("11111111-1111-1111-1111-111111111111", user.id)
        assertEquals("nhat_dao", user.username)
        assertEquals("nhat@example.com", user.email)
        assertNull("selectedLevelId must be null", user.selectedLevelId)
        assertEquals(3, user.currentStreak)
        assertNull("lastLearningDate must be null", user.lastLearningDate)
        assertEquals("2026-10-04T10:30:00Z", user.createdAt)

        // Request verification
        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertTrue("Path must end with /auth/register", request.path!!.endsWith("/auth/register"))
        assertEquals("application/json", request.getHeader("Content-Type")?.substringBefore(";"))
        val body = request.body.readUtf8()
        assertTrue(body.contains(""""username":"nhat_dao""""))
        assertTrue(body.contains(""""email":"nhat@example.com""""))
        assertTrue(body.contains(""""password":"Passw0rdOK""""))
    }

    /**
     * Test 2: POST /auth/login success.
     * Verifies: request body contains "identifier" (not "email"), "password"; parse success.
     */
    @Test
    fun authApi_login_success_requestBodyContainsIdentifierField() = testScope.runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":true,"message":"OK","data":$authResultJson,"error":null}""")
        )

        val response = authApi.login(
            LoginRequest(identifier = "nhat@example.com", password = "Passw0rdOK")
        )

        assertTrue(response.status)
        assertNotNull(response.data)

        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.endsWith("/auth/login"))
        val body = request.body.readUtf8()
        assertTrue("Body must contain 'identifier' key", body.contains(""""identifier":"""))
        assertFalse("Body must NOT contain 'email' key", body.contains(""""email":"""))
        assertTrue(body.contains(""""identifier":"nhat@example.com""""))
        assertTrue(body.contains(""""password":"Passw0rdOK""""))
    }

    /**
     * Test 3: GET /auth/me success.
     * Verifies: GET method, path, parse UserDto.
     */
    @Test
    fun authApi_me_success_parsesUserDto() = testScope.runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":true,"message":"OK","data":$userJsonNullLevel,"error":null}""")
        )

        val response = authApi.me()

        assertTrue(response.status)
        val user = checkNotNull(response.data)
        assertEquals("nhat_dao", user.username)
        assertEquals("nhat@example.com", user.email)
        assertEquals(3, user.currentStreak)

        val request = mockWebServer.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.path!!.endsWith("/auth/me"))
    }

    /**
     * Test 4a: POST /auth/logout success with fcmToken = null.
     * Verifies: data:null parses without error; body does NOT contain "fcmToken":null.
     */
    @Test
    fun authApi_logout_success_withNullFcmToken_bodyOmitsFcmTokenField() = testScope.runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":true,"message":"OK","data":null,"error":null}""")
        )

        val response: ApiResponse<Unit?> = authApi.logout(LogoutRequest(fcmToken = null))

        assertTrue("Response status must be true", response.status)
        // data:null must parse without throwing
        assertNull(response.data)
        assertNull(response.error)

        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.endsWith("/auth/logout"))
        val body = request.body.readUtf8()
        // encodeDefaults=false means null fcmToken must NOT appear as "fcmToken":null
        assertFalse(
            "Null fcmToken must be omitted (encodeDefaults=false), not sent as JSON null",
            body.contains(""""fcmToken":null""")
        )
    }

    /**
     * Test 4b: POST /auth/logout with real FCM token.
     * Verifies: body contains the exact fcmToken value.
     */
    @Test
    fun authApi_logout_withFcmToken_bodyContainsFcmTokenValue() = testScope.runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":true,"message":"OK","data":null,"error":null}""")
        )

        authApi.logout(LogoutRequest(fcmToken = "fcm_abc_token_xyz"))

        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(
            "Body must contain the fcmToken value",
            body.contains(""""fcmToken":"fcm_abc_token_xyz"""")
        )
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // LevelApi Tests
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Test 5: GET /levels success.
     * Verifies: parse ListData<LevelDto> with 3 items; enum status parsed correctly;
     * null description accepted; correct orderIndex values.
     */
    @Test
    fun levelApi_getLevels_success_parsesListDataWith3Levels() = testScope.runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"status":true,"message":"OK","data":{"items":$levelItemsJson},"error":null}"""
                )
        )

        val response = levelApi.getLevels()

        assertTrue(response.status)
        val levels = checkNotNull(response.data).items
        assertEquals(3, levels.size)

        // First level: unlocked, null description
        val first = levels[0]
        assertEquals("Beginner (A1)", first.name)
        assertEquals(ContentStatus.UNLOCKED, first.status)
        assertNull("description must be null", first.description)
        assertEquals(1, first.orderIndex)
        assertEquals(0, first.stars)
        assertEquals(45, first.maxStars)
        assertEquals(0, first.topicsCompleted)
        assertEquals(3, first.topicsTotal)

        // Second level: locked, non-null description
        val second = levels[1]
        assertEquals(ContentStatus.LOCKED, second.status)
        assertEquals("For elementary learners", second.description)

        val request = mockWebServer.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.path!!.endsWith("/levels"))
    }

    /**
     * Test 6: GET /levels with unknown status value.
     * Verifies: "archived" maps to ContentStatus.UNKNOWN without crashing (coerceInputValues).
     */
    @Test
    fun levelApi_getLevels_unknownStatusValue_coercesToUnknownWithoutCrashing() = testScope.runTest {
        val unknownStatusJson = """
            [
              {
                "id": "aaaa0000-0000-0000-0000-000000000099",
                "name": "Future Level",
                "description": null,
                "orderIndex": 4,
                "status": "archived",
                "stars": 0,
                "maxStars": 45,
                "topicsCompleted": 0,
                "topicsTotal": 3
              }
            ]
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"status":true,"message":"OK","data":{"items":$unknownStatusJson},"error":null}"""
                )
        )

        val response = levelApi.getLevels()

        // Must parse without exception
        assertTrue(response.status)
        val levels = checkNotNull(response.data).items
        assertEquals(1, levels.size)
        assertEquals(
            "Unknown enum value 'archived' must coerce to UNKNOWN",
            ContentStatus.UNKNOWN,
            levels[0].status
        )
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // ProfileApi Tests
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Test 7: GET /profile with level present.
     * Verifies: parse ProfileDto including nested LevelBriefDto.
     */
    @Test
    fun profileApi_getProfile_withLevel_parsesProfileDtoWithLevelBrief() = testScope.runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"status":true,"message":"OK","data":$profileWithLevelJson,"error":null}"""
                )
        )

        val response: ApiResponse<ProfileDto> = profileApi.getProfile()

        assertTrue(response.status)
        val profile = checkNotNull(response.data)
        assertEquals("11111111-1111-1111-1111-111111111111", profile.id)
        assertEquals("nhat_dao", profile.username)
        assertEquals("nhat@example.com", profile.email)
        assertEquals(7, profile.totalStars)
        assertEquals(135, profile.maxStars)
        assertEquals(3, profile.currentStreak)
        assertEquals("2026-10-04T10:30:00Z", profile.createdAt)

        val level = checkNotNull(profile.level)
        assertEquals("aaaa0000-0000-0000-0000-000000000001", level.id)
        assertEquals("Beginner (A1)", level.name)
        assertEquals(7, level.stars)
        assertEquals(45, level.maxStars)
        assertEquals(15.6, level.progressPercent, 0.001)

        val request = mockWebServer.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.path!!.endsWith("/profile"))
    }

    /**
     * Test 8: GET /profile with level = null.
     * Verifies: ProfileDto.level is null when user has not selected a level yet.
     */
    @Test
    fun profileApi_getProfile_withNullLevel_profileLevelIsNull() = testScope.runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"status":true,"message":"OK","data":$profileNoLevelJson,"error":null}"""
                )
        )

        val response = profileApi.getProfile()

        assertTrue(response.status)
        val profile = checkNotNull(response.data)
        assertNull("level must be null when no level is selected", profile.level)
        assertEquals(0, profile.totalStars)
        assertEquals(0, profile.currentStreak)
    }

    /**
     * Test 9: PUT /profile with only username set.
     * Verifies: request body contains ONLY "username"; selectedLevelId is absent (encodeDefaults=false).
     */
    @Test
    fun profileApi_updateProfile_onlyUsername_bodyContainsOnlyUsernameField() = testScope.runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"status":true,"message":"OK","data":$profileWithLevelJson,"error":null}"""
                )
        )

        profileApi.updateProfile(UpdateProfileRequest(username = "new_username"))

        val request = mockWebServer.takeRequest()
        assertEquals("PUT", request.method)
        assertTrue(request.path!!.endsWith("/profile"))
        val body = request.body.readUtf8()
        assertTrue("Body must contain username", body.contains(""""username":"new_username""""))
        assertFalse(
            "selectedLevelId must be absent (encodeDefaults=false prevents sending null)",
            body.contains(""""selectedLevelId"""")
        )
    }

    /**
     * Test 10: PUT /profile with only selectedLevelId set.
     * Verifies: request body contains ONLY "selectedLevelId"; username is absent.
     */
    @Test
    fun profileApi_updateProfile_onlySelectedLevelId_bodyContainsOnlyLevelIdField() = testScope.runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"status":true,"message":"OK","data":$profileWithLevelJson,"error":null}"""
                )
        )

        profileApi.updateProfile(
            UpdateProfileRequest(selectedLevelId = "aaaa0000-0000-0000-0000-000000000001")
        )

        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(
            "Body must contain selectedLevelId",
            body.contains(""""selectedLevelId":"aaaa0000-0000-0000-0000-000000000001"""")
        )
        assertFalse(
            "username must be absent (encodeDefaults=false prevents sending null)",
            body.contains(""""username"""")
        )
    }

    /**
     * Test 11: PUT /profile with both fields set.
     * Verifies: body contains both fields; HTTP method is PUT; path is correct.
     */
    @Test
    fun profileApi_updateProfile_bothFieldsSet_bodyContainsBothFields() = testScope.runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"status":true,"message":"OK","data":$profileWithLevelJson,"error":null}"""
                )
        )

        profileApi.updateProfile(
            UpdateProfileRequest(
                username = "updated_user",
                selectedLevelId = "aaaa0000-0000-0000-0000-000000000001"
            )
        )

        val request = mockWebServer.takeRequest()
        assertEquals("PUT", request.method)
        assertTrue(request.path!!.endsWith("/profile"))
        val body = request.body.readUtf8()
        assertTrue(body.contains(""""username":"updated_user""""))
        assertTrue(body.contains(""""selectedLevelId":"aaaa0000-0000-0000-0000-000000000001""""))
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Error Parsing Tests (contract section 3.2 & 3.3)
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Test 12a: 401 unauthorized error response parses ApiErrorBody correctly.
     * Verifies: error.type == "unauthorized" is captured in ApiResponse.error.
     */
    @Test
    fun error_401Unauthorized_parsesApiErrorBodyCorrectly() = testScope.runTest {
        val errorBody = """
            {
              "status": false,
              "message": "Unauthorized",
              "data": null,
              "error": {
                "type": "unauthorized",
                "detail": "Token expired",
                "traceId": "9b1f3c2e-6a3d-4f0a-8b58-0c2f4d1d7a11",
                "errors": null
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody(errorBody))

        // Retrofit throws HttpException for non-2xx when using direct ApiResponse<T> return
        val exception = runCatching { profileApi.getProfile() }.exceptionOrNull()
        assertNotNull("Must throw HttpException for 401", exception)
        assertTrue(exception is HttpException)

        // Parse the error body using the same JSON decoder (mirrors ApiErrorParser logic)
        val httpException = exception as HttpException
        val rawBody = httpException.response()?.errorBody()?.string()
        assertNotNull(rawBody)
        val envelope = json.decodeFromString<ApiResponse<Unit>>(rawBody!!)
        assertFalse(envelope.status)
        val error = checkNotNull(envelope.error)
        assertEquals("unauthorized", error.type)
        assertEquals("Token expired", error.detail)
        assertEquals("9b1f3c2e-6a3d-4f0a-8b58-0c2f4d1d7a11", error.traceId)
        assertNull(error.errors)
    }

    /**
     * Test 12b: 409 username-taken error parses error.type correctly.
     */
    @Test
    fun error_409UsernameTaken_parsesErrorTypeCorrectly() = testScope.runTest {
        val errorBody = """
            {
              "status": false,
              "message": "Conflict",
              "data": null,
              "error": {
                "type": "username-taken",
                "detail": "Username already in use",
                "traceId": "abc-123",
                "errors": null
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(409).setBody(errorBody))

        val exception = runCatching {
            profileApi.updateProfile(UpdateProfileRequest(username = "taken"))
        }.exceptionOrNull()

        assertNotNull(exception)
        assertTrue(exception is HttpException)
        val rawBody = (exception as HttpException).response()?.errorBody()?.string()
        assertNotNull(rawBody)
        val envelope = json.decodeFromString<ApiResponse<Unit>>(rawBody!!)
        assertEquals("username-taken", envelope.error?.type)
    }

    /**
     * Test 12c: 422 validation-error parses errors[] with field/message/code.
     */
    @Test
    fun error_422ValidationError_parsesFieldErrorsArray() = testScope.runTest {
        val errorBody = """
            {
              "status": false,
              "message": "Validation Error",
              "data": null,
              "error": {
                "type": "validation-error",
                "detail": "One or more fields failed validation",
                "traceId": "trace-422-test",
                "errors": [
                  { "field": "password", "message": "Must be at least 8 characters", "code": "MIN_LENGTH" }
                ]
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(422).setBody(errorBody))

        val exception = runCatching {
            authApi.register(RegisterRequest("u", "bad@example.com", "123"))
        }.exceptionOrNull()

        assertNotNull(exception)
        assertTrue(exception is HttpException)
        val rawBody = (exception as HttpException).response()?.errorBody()?.string()
        assertNotNull(rawBody)
        val envelope = json.decodeFromString<ApiResponse<Unit>>(rawBody!!)
        assertEquals("validation-error", envelope.error?.type)
        val errors = checkNotNull(envelope.error?.errors)
        assertEquals(1, errors.size)
        assertEquals("password", errors[0].field)
        assertEquals("Must be at least 8 characters", errors[0].message)
        assertEquals("MIN_LENGTH", errors[0].code)
    }
}
