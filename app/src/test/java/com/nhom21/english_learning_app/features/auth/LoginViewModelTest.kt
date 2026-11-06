package com.nhom21.english_learning_app.features.auth

import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiFailure
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.core.network.FieldError
import com.nhom21.english_learning_app.data.remote.dto.AuthResultDto
import com.nhom21.english_learning_app.data.remote.dto.UserDto
import com.nhom21.english_learning_app.data.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [LoginViewModel] against [FakeAuthRepository].
 * Covers the full matrix specified in Task B Giai Doan 2 section G.
 * No emulator, no backend, no Hilt — pure JVM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAuthRepository
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAuthRepository()
        viewModel = LoginViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    private val sampleSuccessResult: ApiResult<AuthResultDto> = ApiResult.Success(
        AuthResultDto(
            accessToken = "eyJ.test.token",
            tokenType = "Bearer",
            expiresIn = 604800L,
            user = UserDto(
                id = "11111111-1111-1111-1111-111111111111",
                username = "test_user",
                email = "test@example.com",
                selectedLevelId = null,
                currentStreak = 0,
                lastLearningDate = null,
                createdAt = "2026-10-01T00:00:00Z"
            )
        )
    )

    // ── G: Initialisation ───────────────────────────────────────────────────

    @Test
    fun `initial state is not loading and has no errors`() {
        val state = viewModel.uiState.value
        assertFalse("isLoading should be false initially", state.isLoading)
        assertNull("identifierError should be null initially", state.identifierError)
        assertNull("passwordError should be null initially", state.passwordError)
        assertNull("generalError should be null initially", state.generalError)
        assertEquals("identifier should be empty initially", "", state.identifier)
        assertEquals("password should be empty initially", "", state.password)
    }

    // ── G: Field change clears relevant errors (D-B9) ───────────────────────

    @Test
    fun `onIdentifierChange updates identifier and clears identifierError and generalError`() {
        // Seed some errors first
        viewModel.onLoginClick() // triggers identifierError (empty identifier)
        advanceUntilIdleSync()

        viewModel.onIdentifierChange("user@test.com")

        val state = viewModel.uiState.value
        assertEquals("user@test.com", state.identifier)
        assertNull("identifierError should be cleared", state.identifierError)
        assertNull("generalError should be cleared", state.generalError)
    }

    @Test
    fun `onPasswordChange updates password and clears passwordError and generalError`() {
        // Fill identifier so only password error fires
        viewModel.onIdentifierChange("user@test.com")
        viewModel.onLoginClick()
        advanceUntilIdleSync()

        viewModel.onPasswordChange("Secret1")

        val state = viewModel.uiState.value
        assertEquals("Secret1", state.password)
        assertNull("passwordError should be cleared", state.passwordError)
        assertNull("generalError should be cleared", state.generalError)
    }

    // ── G: Client-side validation (D-B3) ────────────────────────────────────

    @Test
    fun `empty identifier triggers identifierError and does not call repository`() {
        viewModel.onPasswordChange("Secret1")
        viewModel.onLoginClick()
        advanceUntilIdleSync()

        val state = viewModel.uiState.value
        assertNotNull("identifierError must be set", state.identifierError)
        assertEquals(0, fakeRepository.loginCallCount)
        assertFalse(state.isLoading)
    }

    @Test
    fun `whitespace-only identifier triggers identifierError and does not call repository`() {
        viewModel.onIdentifierChange("   ")
        viewModel.onPasswordChange("Secret1")
        viewModel.onLoginClick()
        advanceUntilIdleSync()

        val state = viewModel.uiState.value
        assertNotNull("identifierError must be set for whitespace-only identifier", state.identifierError)
        assertEquals(0, fakeRepository.loginCallCount)
    }

    @Test
    fun `empty password triggers passwordError and does not call repository`() {
        viewModel.onIdentifierChange("user@test.com")
        viewModel.onLoginClick()
        advanceUntilIdleSync()

        val state = viewModel.uiState.value
        assertNotNull("passwordError must be set", state.passwordError)
        assertEquals(0, fakeRepository.loginCallCount)
        assertFalse(state.isLoading)
    }

    // ── G: Repository receives correct arguments (D-B3) ─────────────────────

    @Test
    fun `repository receives trimmed identifier and original password`() = runTest {
        fakeRepository.loginResult = sampleSuccessResult
        viewModel.onIdentifierChange("  user@test.com  ")
        viewModel.onPasswordChange("  Secret1  ") // spaces in password MUST be preserved
        viewModel.onLoginClick()
        advanceUntilIdle()

        assertEquals("identifier must be trimmed", "user@test.com", fakeRepository.lastLoginIdentifier)
        assertEquals("password must NOT be trimmed", "  Secret1  ", fakeRepository.lastLoginPassword)
    }

    // ── G: isLoading behaviour (D-B4) ───────────────────────────────────────

    @Test
    fun `isLoading is true during API call and false after`() = runTest {
        fakeRepository.loginResult = sampleSuccessResult
        viewModel.onIdentifierChange("user@test.com")
        viewModel.onPasswordChange("Secret1")

        // Capture loading states
        val loadingStates = mutableListOf<Boolean>()
        val job = launch { viewModel.uiState.collect { loadingStates.add(it.isLoading) } }

        viewModel.onLoginClick()
        advanceUntilIdle()
        job.cancel()

        assertTrue("isLoading should have been true at some point", loadingStates.contains(true))
        assertFalse("isLoading should be false after completion", viewModel.uiState.value.isLoading)
    }

    @Test
    fun `double-tap while loading calls repository only once`() = runTest {
        fakeRepository.loginResult = sampleSuccessResult
        viewModel.onIdentifierChange("user@test.com")
        viewModel.onPasswordChange("Secret1")

        viewModel.onLoginClick()
        viewModel.onLoginClick() // second tap while loading
        advanceUntilIdle()

        assertEquals("repository.login must be called exactly once", 1, fakeRepository.loginCallCount)
    }

    // ── G: Success (D-B5, D-B6, D-B10) ─────────────────────────────────────

    @Test
    fun `success emits exactly one LoginSuccess event and clears errors`() = runTest {
        fakeRepository.loginResult = sampleSuccessResult
        viewModel.onIdentifierChange("user@test.com")
        viewModel.onPasswordChange("Secret1")

        val events = mutableListOf<LoginEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }

        viewModel.onLoginClick()
        advanceUntilIdle()
        job.cancel()

        assertEquals("Exactly one LoginSuccess event expected", 1, events.size)
        assertTrue(events[0] is LoginEvent.LoginSuccess)
        assertEquals(LoginEvent.LoginSuccess(selectedLevelId = null), events[0])
        assertNull("No identifierError after success", viewModel.uiState.value.identifierError)
        assertNull("No passwordError after success", viewModel.uiState.value.passwordError)
        assertNull("No generalError after success", viewModel.uiState.value.generalError)
    }

    @Test
    fun `login success with null selectedLevelId emits LoginSuccess with null`() = runTest {
        fakeRepository.loginResult = sampleSuccessResult
        viewModel.onIdentifierChange("user@test.com")
        viewModel.onPasswordChange("Secret1")

        val events = mutableListOf<LoginEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }

        viewModel.onLoginClick()
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, events.size)
        val loginSuccess = events[0] as LoginEvent.LoginSuccess
        assertNull("selectedLevelId must be null when user has not chosen a level", loginSuccess.selectedLevelId)
    }

    @Test
    fun `login success with non-null selectedLevelId emits LoginSuccess carrying exact selectedLevelId`() = runTest {
        val expectedLevelId = "11111111-1111-1111-1111-111111111101"
        fakeRepository.loginResult = ApiResult.Success(
            AuthResultDto(
                accessToken = "eyJ.test.token",
                tokenType = "Bearer",
                expiresIn = 604800L,
                user = UserDto(
                    id = "11111111-1111-1111-1111-111111111111",
                    username = "test_user",
                    email = "test@example.com",
                    selectedLevelId = expectedLevelId,
                    currentStreak = 3,
                    lastLearningDate = "2026-10-04",
                    createdAt = "2026-10-01T00:00:00Z"
                )
            )
        )
        viewModel.onIdentifierChange("user@test.com")
        viewModel.onPasswordChange("Secret1")

        val events = mutableListOf<LoginEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }

        viewModel.onLoginClick()
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, events.size)
        val loginSuccess = events[0] as LoginEvent.LoginSuccess
        assertEquals("LoginSuccess must carry the exact selectedLevelId from UserDto", expectedLevelId, loginSuccess.selectedLevelId)
    }

    // ── G: INVALID_CREDENTIALS (contract 3.3 row 401) ───────────────────────

    @Test
    fun `INVALID_CREDENTIALS sets generalError and does not set field errors or emit LoginSuccess`() = runTest {
        fakeRepository.loginResult = ApiResult.Failure(
            ApiFailure(type = ApiErrorType.INVALID_CREDENTIALS, message = "invalid", httpStatus = 401)
        )
        viewModel.onIdentifierChange("user@test.com")
        viewModel.onPasswordChange("WrongPass1")

        val events = mutableListOf<LoginEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }

        viewModel.onLoginClick()
        advanceUntilIdle()
        job.cancel()

        val state = viewModel.uiState.value
        assertNotNull("generalError must be set for INVALID_CREDENTIALS", state.generalError)
        assertEquals(
            "generalError text must be the generic message per A1 / contract 3.3",
            LoginViewModel.ERROR_INVALID_CREDENTIALS,
            state.generalError
        )
        assertNull("No identifierError — must not reveal which field is wrong", state.identifierError)
        assertNull("No passwordError — must not reveal which field is wrong", state.passwordError)
        assertTrue("No LoginSuccess event expected", events.isEmpty())
        assertFalse(state.isLoading)
    }

    // ── G: VALIDATION_ERROR per field (contract 3.2 errors[].field) ──────────

    @Test
    fun `VALIDATION_ERROR with identifier field sets identifierError`() = runTest {
        fakeRepository.loginResult = ApiResult.Failure(
            ApiFailure(
                type = ApiErrorType.VALIDATION_ERROR,
                message = "Validation error",
                fieldErrors = listOf(FieldError(field = "identifier", message = "Identifier too long")),
                httpStatus = 422
            )
        )
        viewModel.onIdentifierChange("user@test.com")
        viewModel.onPasswordChange("Secret1")
        viewModel.onLoginClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull("identifierError must be set", state.identifierError)
        assertNull("passwordError must not be set", state.passwordError)
    }

    @Test
    fun `VALIDATION_ERROR with password field sets passwordError`() = runTest {
        fakeRepository.loginResult = ApiResult.Failure(
            ApiFailure(
                type = ApiErrorType.VALIDATION_ERROR,
                message = "Validation error",
                fieldErrors = listOf(FieldError(field = "password", message = "Password too short")),
                httpStatus = 422
            )
        )
        viewModel.onIdentifierChange("user@test.com")
        viewModel.onPasswordChange("Secret1")
        viewModel.onLoginClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull("passwordError must be set", state.passwordError)
        assertNull("identifierError must not be set", state.identifierError)
    }

    @Test
    fun `VALIDATION_ERROR with unknown field falls back to generalError`() = runTest {
        fakeRepository.loginResult = ApiResult.Failure(
            ApiFailure(
                type = ApiErrorType.VALIDATION_ERROR,
                message = "Validation error",
                fieldErrors = listOf(FieldError(field = "someUnknownField", message = "Bad value")),
                httpStatus = 422
            )
        )
        viewModel.onIdentifierChange("user@test.com")
        viewModel.onPasswordChange("Secret1")
        viewModel.onLoginClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull("generalError must be set for unknown field", state.generalError)
        assertNull("identifierError must not be set", state.identifierError)
        assertNull("passwordError must not be set", state.passwordError)
    }

    // ── G: NETWORK error ─────────────────────────────────────────────────────

    @Test
    fun `NETWORK failure sets generalError with network message`() = runTest {
        fakeRepository.loginResult = ApiResult.Failure(
            ApiFailure(type = ApiErrorType.NETWORK, message = "No connection", httpStatus = null)
        )
        viewModel.onIdentifierChange("user@test.com")
        viewModel.onPasswordChange("Secret1")
        viewModel.onLoginClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LoginViewModel.ERROR_NETWORK, state.generalError)
        assertFalse(state.isLoading)
    }

    // ── G: UNKNOWN / server error ─────────────────────────────────────────────

    @Test
    fun `UNKNOWN error sets general error message and does not crash`() = runTest {
        fakeRepository.loginResult = ApiResult.Failure(
            ApiFailure(type = ApiErrorType.UNKNOWN, message = "Unknown", httpStatus = 500)
        )
        viewModel.onIdentifierChange("user@test.com")
        viewModel.onPasswordChange("Secret1")
        viewModel.onLoginClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LoginViewModel.ERROR_GENERAL, state.generalError)
        assertFalse(state.isLoading)
    }

    @Test
    fun `httpStatus 500 internal error results in general error and does not crash`() = runTest {
        fakeRepository.loginResult = ApiResult.Failure(
            ApiFailure(type = ApiErrorType.INTERNAL_ERROR, message = "Internal error", httpStatus = 500)
        )
        viewModel.onIdentifierChange("user@test.com")
        viewModel.onPasswordChange("Secret1")
        viewModel.onLoginClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LoginViewModel.ERROR_GENERAL, state.generalError)
        assertFalse(state.isLoading)
    }

    // ── G: Retry after error → previous errors cleared ───────────────────────

    @Test
    fun `retry with success after error clears previous error and emits LoginSuccess`() = runTest {
        // First call: error
        fakeRepository.loginResult = ApiResult.Failure(
            ApiFailure(type = ApiErrorType.INVALID_CREDENTIALS, message = "bad", httpStatus = 401)
        )
        viewModel.onIdentifierChange("user@test.com")
        viewModel.onPasswordChange("WrongPass1")
        viewModel.onLoginClick()
        advanceUntilIdle()

        assertNotNull("generalError should be set after first failed attempt", viewModel.uiState.value.generalError)

        // Second call: success
        fakeRepository.loginResult = sampleSuccessResult
        val events = mutableListOf<LoginEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }

        viewModel.onLoginClick()
        advanceUntilIdle()
        job.cancel()

        val state = viewModel.uiState.value
        assertNull("generalError must be cleared on success", state.generalError)
        assertTrue("LoginSuccess must be emitted on retry success", events.isNotEmpty())
        assertTrue(events.any { it is LoginEvent.LoginSuccess })
    }

    // ── I.1: Carryover from Task B (non-replay of LoginSuccess + max-length boundaries) ──

    @Test
    fun `LoginSuccess event is not replayed when events is collected a second time`() = runTest {
        fakeRepository.loginResult = sampleSuccessResult
        viewModel.onIdentifierChange("user@test.com")
        viewModel.onPasswordChange("Secret1")

        val firstEvents = mutableListOf<LoginEvent>()
        val firstJob = launch { viewModel.events.collect { firstEvents.add(it) } }
        viewModel.onLoginClick()
        advanceUntilIdle()
        firstJob.cancel()

        assertEquals(1, firstEvents.size)

        val secondEvents = mutableListOf<LoginEvent>()
        val secondJob = launch { viewModel.events.collect { secondEvents.add(it) } }
        advanceUntilIdle()
        secondJob.cancel()

        assertTrue("LoginSuccess must not be replayed to a second collector", secondEvents.isEmpty())
    }

    @Test
    fun `identifier with 254 characters passes validation and calls repository`() = runTest {
        fakeRepository.loginResult = sampleSuccessResult
        val id254 = "a".repeat(254)
        viewModel.onIdentifierChange(id254)
        viewModel.onPasswordChange("Secret1")
        viewModel.onLoginClick()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.identifierError)
        assertEquals(1, fakeRepository.loginCallCount)
        assertEquals(id254, fakeRepository.lastLoginIdentifier)
    }

    @Test
    fun `identifier with 255 characters triggers identifierError and does not call repository`() = runTest {
        val id255 = "a".repeat(255)
        viewModel.onIdentifierChange(id255)
        viewModel.onPasswordChange("Secret1")
        viewModel.onLoginClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LoginViewModel.ERROR_IDENTIFIER_TOO_LONG, state.identifierError)
        assertEquals(0, fakeRepository.loginCallCount)
        assertFalse(state.isLoading)
    }

    @Test
    fun `password with 64 characters passes validation and calls repository`() = runTest {
        fakeRepository.loginResult = sampleSuccessResult
        val pass64 = "p".repeat(64)
        viewModel.onIdentifierChange("user@test.com")
        viewModel.onPasswordChange(pass64)
        viewModel.onLoginClick()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.passwordError)
        assertEquals(1, fakeRepository.loginCallCount)
        assertEquals(pass64, fakeRepository.lastLoginPassword)
    }

    @Test
    fun `password with 65 characters triggers passwordError and does not call repository`() = runTest {
        val pass65 = "p".repeat(65)
        viewModel.onIdentifierChange("user@test.com")
        viewModel.onPasswordChange(pass65)
        viewModel.onLoginClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LoginViewModel.ERROR_PASSWORD_TOO_LONG, state.passwordError)
        assertEquals(0, fakeRepository.loginCallCount)
        assertFalse(state.isLoading)
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Convenience function to advance the test dispatcher within a non-runTest context.
     * Used for simple synchronous-looking tests.
     */
    private fun advanceUntilIdleSync() {
        testDispatcher.scheduler.advanceUntilIdle()
    }
}
