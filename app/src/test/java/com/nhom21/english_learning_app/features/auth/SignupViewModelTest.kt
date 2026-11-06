package com.nhom21.english_learning_app.features.auth

import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiFailure
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.core.network.FieldError
import com.nhom21.english_learning_app.data.remote.dto.AuthResultDto
import com.nhom21.english_learning_app.data.remote.dto.UserDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests for [SignupViewModel] covering the full Matrix G.1 specification.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAuthRepository
    private lateinit var viewModel: SignupViewModel

    private val sampleSuccessResult: ApiResult<AuthResultDto> = ApiResult.Success(
        AuthResultDto(
            accessToken = "eyJ.signup.token",
            tokenType = "Bearer",
            expiresIn = 604800L,
            user = UserDto(
                id = "22222222-2222-2222-2222-222222222222",
                username = "new_user",
                email = "new@example.com",
                selectedLevelId = null,
                currentStreak = 0,
                lastLearningDate = null,
                createdAt = "2026-10-05T00:00:00Z"
            )
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAuthRepository()
        viewModel = SignupViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fillValidInputs(
        username: String = "valid_user_1",
        email: String = "valid@example.com",
        password: String = "Passw0rd1",
        confirmPassword: String = password
    ) {
        viewModel.onUsernameChange(username)
        viewModel.onEmailChange(email)
        viewModel.onPasswordChange(password)
        viewModel.onConfirmPasswordChange(confirmPassword)
    }

    // ── G.1.1: Initial state ────────────────────────────────────────────────

    @Test
    fun `initial state is not loading and has no errors`() {
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("", state.username)
        assertEquals("", state.email)
        assertEquals("", state.password)
        assertEquals("", state.confirmPassword)
        assertNull(state.usernameError)
        assertNull(state.emailError)
        assertNull(state.passwordError)
        assertNull(state.confirmPasswordError)
        assertNull(state.generalError)
    }

    // ── G.1.2: Field edits clear field errors + generalError (D-C11) ─────────

    @Test
    fun `onUsernameChange updates username and clears usernameError and generalError`() = runTest {
        fakeRepository.registerResult = ApiResult.Failure(
            ApiFailure(type = ApiErrorType.NETWORK, message = "net")
        )
        fillValidInputs()
        viewModel.onSignupClick()
        advanceUntilIdle()
        // Trigger usernameError as well
        viewModel.onUsernameChange("")
        viewModel.onSignupClick()

        assertEquals(SignupViewModel.ERROR_USERNAME_REQUIRED, viewModel.uiState.value.usernameError)

        viewModel.onUsernameChange("alice_01")
        val state = viewModel.uiState.value
        assertEquals("alice_01", state.username)
        assertNull(state.usernameError)
        assertNull(state.generalError)
    }

    @Test
    fun `onEmailChange updates email and clears emailError and generalError`() = runTest {
        fakeRepository.registerResult = ApiResult.Failure(
            ApiFailure(type = ApiErrorType.NETWORK, message = "net")
        )
        fillValidInputs()
        viewModel.onSignupClick()
        advanceUntilIdle()

        viewModel.onEmailChange("")
        viewModel.onSignupClick()
        assertEquals(SignupViewModel.ERROR_EMAIL_REQUIRED, viewModel.uiState.value.emailError)

        viewModel.onEmailChange("alice@example.com")
        val state = viewModel.uiState.value
        assertEquals("alice@example.com", state.email)
        assertNull(state.emailError)
        assertNull(state.generalError)
    }

    @Test
    fun `onPasswordChange updates password and clears passwordError confirmPasswordError and generalError`() {
        viewModel.onUsernameChange("alice_01")
        viewModel.onEmailChange("alice@example.com")
        viewModel.onPasswordChange("short")
        viewModel.onConfirmPasswordChange("different")
        viewModel.onSignupClick()

        assertEquals(SignupViewModel.ERROR_PASSWORD_LENGTH, viewModel.uiState.value.passwordError)
        assertEquals(SignupViewModel.ERROR_CONFIRM_MISMATCH, viewModel.uiState.value.confirmPasswordError)

        viewModel.onPasswordChange("ValidPass1")
        val state = viewModel.uiState.value
        assertEquals("ValidPass1", state.password)
        assertNull(state.passwordError)
        assertNull("Editing password must also clear confirmPasswordError (D-C11)", state.confirmPasswordError)
        assertNull(state.generalError)
    }

    @Test
    fun `onConfirmPasswordChange updates confirmPassword and clears confirmPasswordError and generalError`() {
        fillValidInputs(password = "ValidPass1", confirmPassword = "WrongConfirm1")
        viewModel.onSignupClick()
        assertEquals(SignupViewModel.ERROR_CONFIRM_MISMATCH, viewModel.uiState.value.confirmPasswordError)

        viewModel.onConfirmPasswordChange("ValidPass1")
        val state = viewModel.uiState.value
        assertEquals("ValidPass1", state.confirmPassword)
        assertNull(state.confirmPasswordError)
        assertNull(state.generalError)
    }

    // ── G.1.3: Username validation ──────────────────────────────────────────

    @Test
    fun `username empty sets usernameError required`() {
        fillValidInputs(username = "")
        viewModel.onSignupClick()

        assertEquals(SignupViewModel.ERROR_USERNAME_REQUIRED, viewModel.uiState.value.usernameError)
        assertEquals(0, fakeRepository.registerCallCount)
    }

    @Test
    fun `username whitespace only sets usernameError required`() {
        fillValidInputs(username = "    ")
        viewModel.onSignupClick()

        assertEquals(SignupViewModel.ERROR_USERNAME_REQUIRED, viewModel.uiState.value.usernameError)
        assertEquals(0, fakeRepository.registerCallCount)
    }

    @Test
    fun `username 2 chars fails with usernameError invalid`() {
        fillValidInputs(username = "ab")
        viewModel.onSignupClick()

        assertEquals(SignupViewModel.ERROR_USERNAME_INVALID, viewModel.uiState.value.usernameError)
        assertEquals(0, fakeRepository.registerCallCount)
    }

    @Test
    fun `username 3 chars passes username validation`() = runTest {
        fakeRepository.registerResult = sampleSuccessResult
        fillValidInputs(username = "abc")
        viewModel.onSignupClick()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.usernameError)
        assertEquals(1, fakeRepository.registerCallCount)
    }

    @Test
    fun `username 30 chars passes username validation`() = runTest {
        fakeRepository.registerResult = sampleSuccessResult
        val u30 = "a".repeat(30)
        fillValidInputs(username = u30)
        viewModel.onSignupClick()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.usernameError)
        assertEquals(1, fakeRepository.registerCallCount)
    }

    @Test
    fun `username 31 chars fails with usernameError invalid`() {
        val u31 = "a".repeat(31)
        fillValidInputs(username = u31)
        viewModel.onSignupClick()

        assertEquals(SignupViewModel.ERROR_USERNAME_INVALID, viewModel.uiState.value.usernameError)
        assertEquals(0, fakeRepository.registerCallCount)
    }

    @Test
    fun `username containing at sign fails with usernameError invalid`() {
        fillValidInputs(username = "user@name")
        viewModel.onSignupClick()

        assertEquals(SignupViewModel.ERROR_USERNAME_INVALID, viewModel.uiState.value.usernameError)
        assertEquals(0, fakeRepository.registerCallCount)
    }

    @Test
    fun `username containing characters outside pattern fails with usernameError invalid`() {
        fillValidInputs(username = "user-name!")
        viewModel.onSignupClick()

        assertEquals(SignupViewModel.ERROR_USERNAME_INVALID, viewModel.uiState.value.usernameError)
        assertEquals(0, fakeRepository.registerCallCount)
    }

    // ── G.1.4: Email validation ─────────────────────────────────────────────

    @Test
    fun `email empty sets emailError required`() {
        fillValidInputs(email = "")
        viewModel.onSignupClick()

        assertEquals(SignupViewModel.ERROR_EMAIL_REQUIRED, viewModel.uiState.value.emailError)
        assertEquals(0, fakeRepository.registerCallCount)
    }

    @Test
    fun `email with internal whitespace fails with emailError invalid`() {
        fillValidInputs(email = "user name@example.com")
        viewModel.onSignupClick()

        assertEquals(SignupViewModel.ERROR_EMAIL_INVALID, viewModel.uiState.value.emailError)
        assertEquals(0, fakeRepository.registerCallCount)
    }

    @Test
    fun `email missing at sign fails with emailError invalid`() {
        fillValidInputs(email = "userexample.com")
        viewModel.onSignupClick()

        assertEquals(SignupViewModel.ERROR_EMAIL_INVALID, viewModel.uiState.value.emailError)
        assertEquals(0, fakeRepository.registerCallCount)
    }

    @Test
    fun `email containing angle brackets fails with emailError invalid`() {
        fillValidInputs(email = "<user>@example.com")
        viewModel.onSignupClick()

        assertEquals(SignupViewModel.ERROR_EMAIL_INVALID, viewModel.uiState.value.emailError)
        assertEquals(0, fakeRepository.registerCallCount)
    }

    @Test
    fun `email 254 chars passes email validation`() = runTest {
        fakeRepository.registerResult = sampleSuccessResult
        val email254 = "a".repeat(242) + "@example.com" // 242 + 12 = 254
        assertEquals(254, email254.length)
        fillValidInputs(email = email254)
        viewModel.onSignupClick()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.emailError)
        assertEquals(1, fakeRepository.registerCallCount)
    }

    @Test
    fun `email 255 chars fails with emailError invalid`() {
        val email255 = "a".repeat(243) + "@example.com" // 243 + 12 = 255
        assertEquals(255, email255.length)
        fillValidInputs(email = email255)
        viewModel.onSignupClick()

        assertEquals(SignupViewModel.ERROR_EMAIL_INVALID, viewModel.uiState.value.emailError)
        assertEquals(0, fakeRepository.registerCallCount)
    }

    // ── G.1.5: Password validation ──────────────────────────────────────────

    @Test
    fun `password empty sets passwordError required`() {
        fillValidInputs(password = "", confirmPassword = "")
        viewModel.onSignupClick()

        assertEquals(SignupViewModel.ERROR_PASSWORD_REQUIRED, viewModel.uiState.value.passwordError)
        assertEquals(0, fakeRepository.registerCallCount)
    }

    @Test
    fun `password 7 chars fails with passwordError length`() {
        fillValidInputs(password = "Abcde12", confirmPassword = "Abcde12")
        viewModel.onSignupClick()

        assertEquals(SignupViewModel.ERROR_PASSWORD_LENGTH, viewModel.uiState.value.passwordError)
        assertEquals(0, fakeRepository.registerCallCount)
    }

    @Test
    fun `password 8 chars with letter and digit passes password validation`() = runTest {
        fakeRepository.registerResult = sampleSuccessResult
        fillValidInputs(password = "Abcdef12", confirmPassword = "Abcdef12")
        viewModel.onSignupClick()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.passwordError)
        assertEquals(1, fakeRepository.registerCallCount)
    }

    @Test
    fun `password 64 chars with letter and digit passes password validation`() = runTest {
        fakeRepository.registerResult = sampleSuccessResult
        val pw64 = "A1" + "a".repeat(62)
        assertEquals(64, pw64.length)
        fillValidInputs(password = pw64, confirmPassword = pw64)
        viewModel.onSignupClick()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.passwordError)
        assertEquals(1, fakeRepository.registerCallCount)
    }

    @Test
    fun `password 65 chars fails with passwordError length`() {
        val pw65 = "A1" + "a".repeat(63)
        assertEquals(65, pw65.length)
        fillValidInputs(password = pw65, confirmPassword = pw65)
        viewModel.onSignupClick()

        assertEquals(SignupViewModel.ERROR_PASSWORD_LENGTH, viewModel.uiState.value.passwordError)
        assertEquals(0, fakeRepository.registerCallCount)
    }

    @Test
    fun `password letters only fails with passwordError complexity`() {
        fillValidInputs(password = "Abcdefgh", confirmPassword = "Abcdefgh")
        viewModel.onSignupClick()

        assertEquals(SignupViewModel.ERROR_PASSWORD_COMPLEXITY, viewModel.uiState.value.passwordError)
        assertEquals(0, fakeRepository.registerCallCount)
    }

    @Test
    fun `password digits only fails with passwordError complexity`() {
        fillValidInputs(password = "12345678", confirmPassword = "12345678")
        viewModel.onSignupClick()

        assertEquals(SignupViewModel.ERROR_PASSWORD_COMPLEXITY, viewModel.uiState.value.passwordError)
        assertEquals(0, fakeRepository.registerCallCount)
    }

    @Test
    fun `password non-ascii letters with digits fails with passwordError complexity`() {
        fillValidInputs(password = "ăâêôơư1234", confirmPassword = "ăâêôơư1234")
        viewModel.onSignupClick()

        assertEquals(SignupViewModel.ERROR_PASSWORD_COMPLEXITY, viewModel.uiState.value.passwordError)
        assertEquals(0, fakeRepository.registerCallCount)
    }

    // ── G.1.6: Confirm password validation ──────────────────────────────────

    @Test
    fun `confirmPassword empty sets confirmPasswordError required`() {
        fillValidInputs(password = "Passw0rd1", confirmPassword = "")
        viewModel.onSignupClick()

        assertEquals(SignupViewModel.ERROR_CONFIRM_REQUIRED, viewModel.uiState.value.confirmPasswordError)
        assertEquals(0, fakeRepository.registerCallCount)
    }

    @Test
    fun `confirmPassword mismatch sets confirmPasswordError mismatch`() {
        fillValidInputs(password = "Passw0rd1", confirmPassword = "Passw0rd2")
        viewModel.onSignupClick()

        assertEquals(SignupViewModel.ERROR_CONFIRM_MISMATCH, viewModel.uiState.value.confirmPasswordError)
        assertEquals(0, fakeRepository.registerCallCount)
    }

    @Test
    fun `confirmPassword differing only in case fails with confirmPasswordError mismatch`() {
        fillValidInputs(password = "Passw0rd1", confirmPassword = "passw0rd1")
        viewModel.onSignupClick()

        assertEquals(SignupViewModel.ERROR_CONFIRM_MISMATCH, viewModel.uiState.value.confirmPasswordError)
        assertEquals(0, fakeRepository.registerCallCount)
    }

    @Test
    fun `confirmPassword matching password passes validation`() = runTest {
        fakeRepository.registerResult = sampleSuccessResult
        fillValidInputs(password = "Passw0rd1", confirmPassword = "Passw0rd1")
        viewModel.onSignupClick()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.confirmPasswordError)
        assertEquals(1, fakeRepository.registerCallCount)
    }

    // ── G.1.7: Multiple simultaneous errors ─────────────────────────────────

    @Test
    fun `multiple invalid fields show all field errors simultaneously and do not call repository`() {
        viewModel.onUsernameChange("a")
        viewModel.onEmailChange("invalid-email")
        viewModel.onPasswordChange("onlyletters")
        viewModel.onConfirmPasswordChange("mismatch")
        viewModel.onSignupClick()

        val state = viewModel.uiState.value
        assertEquals(SignupViewModel.ERROR_USERNAME_INVALID, state.usernameError)
        assertEquals(SignupViewModel.ERROR_EMAIL_INVALID, state.emailError)
        assertEquals(SignupViewModel.ERROR_PASSWORD_COMPLEXITY, state.passwordError)
        assertEquals(SignupViewModel.ERROR_CONFIRM_MISMATCH, state.confirmPasswordError)
        assertEquals(0, fakeRepository.registerCallCount)
    }

    // ── G.1.8: Trimming & verbatim password (D-C2, D-C3) ────────────────────

    @Test
    fun `valid input sends trimmed non-lowercased username and email and verbatim password to repository`() = runTest {
        fakeRepository.registerResult = sampleSuccessResult
        fillValidInputs(
            username = "  Mixed_User.01  ",
            email = "  Mixed.Email@Example.COM  ",
            password = "  Passw0rd1  ",
            confirmPassword = "  Passw0rd1  "
        )
        viewModel.onSignupClick()
        advanceUntilIdle()

        assertEquals(1, fakeRepository.registerCallCount)
        assertEquals("Mixed_User.01", fakeRepository.lastRegisterUsername)
        assertEquals("Mixed.Email@Example.COM", fakeRepository.lastRegisterEmail)
        assertEquals("  Passw0rd1  ", fakeRepository.lastRegisterPassword)
    }

    // ── G.1.9: isLoading transitions ────────────────────────────────────────

    @Test
    fun `isLoading is true during register call and false after success`() = runTest {
        fakeRepository.registerResult = sampleSuccessResult
        fillValidInputs()

        val loadingStates = mutableListOf<Boolean>()
        val job = launch { viewModel.uiState.collect { loadingStates.add(it.isLoading) } }

        viewModel.onSignupClick()
        advanceUntilIdle()
        job.cancel()

        assertTrue(loadingStates.contains(true))
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `isLoading is true during register call and false after failure`() = runTest {
        fakeRepository.registerResult = ApiResult.Failure(
            ApiFailure(type = ApiErrorType.NETWORK, message = "net")
        )
        fillValidInputs()

        val loadingStates = mutableListOf<Boolean>()
        val job = launch { viewModel.uiState.collect { loadingStates.add(it.isLoading) } }

        viewModel.onSignupClick()
        advanceUntilIdle()
        job.cancel()

        assertTrue(loadingStates.contains(true))
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // ── G.1.10: Double-tap guard (D-C10) ────────────────────────────────────

    @Test
    fun `double tap while loading calls repository register only once`() = runTest {
        fakeRepository.registerResult = sampleSuccessResult
        fillValidInputs()

        viewModel.onSignupClick()
        viewModel.onSignupClick()
        advanceUntilIdle()

        assertEquals(1, fakeRepository.registerCallCount)
    }

    // ── G.1.11: One-shot SignupSuccess event (D-C5) ─────────────────────────

    @Test
    fun `success emits exactly one SignupSuccess and does not replay to second collector`() = runTest {
        fakeRepository.registerResult = sampleSuccessResult
        fillValidInputs()

        val firstCollectorEvents = mutableListOf<SignupEvent>()
        val job1 = launch { viewModel.events.collect { firstCollectorEvents.add(it) } }

        viewModel.onSignupClick()
        advanceUntilIdle()
        job1.cancel()

        assertEquals(1, firstCollectorEvents.size)
        assertTrue(firstCollectorEvents[0] is SignupEvent.SignupSuccess)

        val secondCollectorEvents = mutableListOf<SignupEvent>()
        val job2 = launch { viewModel.events.collect { secondCollectorEvents.add(it) } }
        advanceUntilIdle()
        job2.cancel()

        assertTrue("One-shot event must not replay to second collector", secondCollectorEvents.isEmpty())
    }

    // ── G.1.12: 409 EMAIL_TAKEN & USERNAME_TAKEN (D-C7) ─────────────────────

    @Test
    fun `EMAIL_TAKEN sets emailError and does not emit SignupSuccess`() = runTest {
        fakeRepository.registerResult = ApiResult.Failure(
            ApiFailure(type = ApiErrorType.EMAIL_TAKEN, message = "Email already registered", httpStatus = 409)
        )
        fillValidInputs()

        val events = mutableListOf<SignupEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }

        viewModel.onSignupClick()
        advanceUntilIdle()
        job.cancel()

        val state = viewModel.uiState.value
        assertEquals(SignupViewModel.ERROR_EMAIL_TAKEN, state.emailError)
        assertNull(state.usernameError)
        assertNull(state.generalError)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `USERNAME_TAKEN sets usernameError and does not emit SignupSuccess`() = runTest {
        fakeRepository.registerResult = ApiResult.Failure(
            ApiFailure(type = ApiErrorType.USERNAME_TAKEN, message = "Username already taken", httpStatus = 409)
        )
        fillValidInputs()

        val events = mutableListOf<SignupEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }

        viewModel.onSignupClick()
        advanceUntilIdle()
        job.cancel()

        val state = viewModel.uiState.value
        assertEquals(SignupViewModel.ERROR_USERNAME_TAKEN, state.usernameError)
        assertNull(state.emailError)
        assertNull(state.generalError)
        assertTrue(events.isEmpty())
    }

    // ── G.1.13: 422 VALIDATION_ERROR mapping (D-C7) ─────────────────────────

    @Test
    fun `VALIDATION_ERROR for username sets usernameError`() = runTest {
        fakeRepository.registerResult = ApiResult.Failure(
            ApiFailure(
                type = ApiErrorType.VALIDATION_ERROR,
                message = "Validation Error",
                fieldErrors = listOf(FieldError(field = "username", message = "Raw server msg")),
                httpStatus = 422
            )
        )
        fillValidInputs()
        viewModel.onSignupClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SignupViewModel.ERROR_USERNAME_INVALID, state.usernameError)
        assertNull(state.emailError)
        assertNull(state.passwordError)
        assertNull(state.generalError)
    }

    @Test
    fun `VALIDATION_ERROR for email sets emailError`() = runTest {
        fakeRepository.registerResult = ApiResult.Failure(
            ApiFailure(
                type = ApiErrorType.VALIDATION_ERROR,
                message = "Validation Error",
                fieldErrors = listOf(FieldError(field = "email", message = "Raw server msg")),
                httpStatus = 422
            )
        )
        fillValidInputs()
        viewModel.onSignupClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SignupViewModel.ERROR_EMAIL_INVALID, state.emailError)
        assertNull(state.usernameError)
        assertNull(state.passwordError)
        assertNull(state.generalError)
    }

    @Test
    fun `VALIDATION_ERROR for password sets passwordError`() = runTest {
        fakeRepository.registerResult = ApiResult.Failure(
            ApiFailure(
                type = ApiErrorType.VALIDATION_ERROR,
                message = "Validation Error",
                fieldErrors = listOf(FieldError(field = "password", message = "Raw server msg")),
                httpStatus = 422
            )
        )
        fillValidInputs()
        viewModel.onSignupClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SignupViewModel.ERROR_PASSWORD_COMPLEXITY, state.passwordError)
        assertNull(state.usernameError)
        assertNull(state.emailError)
        assertNull(state.generalError)
    }

    @Test
    fun `VALIDATION_ERROR for unknown field sets generalError`() = runTest {
        fakeRepository.registerResult = ApiResult.Failure(
            ApiFailure(
                type = ApiErrorType.VALIDATION_ERROR,
                message = "Validation Error",
                fieldErrors = listOf(FieldError(field = "body", message = "Field required")),
                httpStatus = 422
            )
        )
        fillValidInputs()
        viewModel.onSignupClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SignupViewModel.ERROR_VALIDATION, state.generalError)
        assertNull(state.usernameError)
        assertNull(state.emailError)
        assertNull(state.passwordError)
    }

    @Test
    fun `VALIDATION_ERROR with empty fieldErrors sets generalError`() = runTest {
        fakeRepository.registerResult = ApiResult.Failure(
            ApiFailure(
                type = ApiErrorType.VALIDATION_ERROR,
                message = "Validation Error",
                fieldErrors = emptyList(),
                httpStatus = 422
            )
        )
        fillValidInputs()
        viewModel.onSignupClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SignupViewModel.ERROR_VALIDATION, state.generalError)
        assertNull(state.usernameError)
        assertNull(state.emailError)
        assertNull(state.passwordError)
    }

    // ── G.1.14: NETWORK & UNKNOWN / 500 errors (D-C7) ───────────────────────

    @Test
    fun `NETWORK failure sets generalError to network message`() = runTest {
        fakeRepository.registerResult = ApiResult.Failure(
            ApiFailure(type = ApiErrorType.NETWORK, message = "SocketTimeout", httpStatus = null)
        )
        fillValidInputs()
        viewModel.onSignupClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SignupViewModel.ERROR_NETWORK, state.generalError)
        assertFalse(state.isLoading)
    }

    @Test
    fun `UNKNOWN failure with httpStatus 500 sets generalError to server message without crashing`() = runTest {
        fakeRepository.registerResult = ApiResult.Failure(
            ApiFailure(type = ApiErrorType.UNKNOWN, message = "Internal server crash", httpStatus = 500)
        )
        fillValidInputs()
        viewModel.onSignupClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SignupViewModel.ERROR_SERVER, state.generalError)
        assertFalse(state.isLoading)
    }

    // ── G.1.15: Retry after error clears error and emits SignupSuccess ──────

    @Test
    fun `retry with success after failure clears previous error and emits SignupSuccess`() = runTest {
        fakeRepository.registerResult = ApiResult.Failure(
            ApiFailure(type = ApiErrorType.EMAIL_TAKEN, message = "Email already registered", httpStatus = 409)
        )
        fillValidInputs()
        viewModel.onSignupClick()
        advanceUntilIdle()
        assertEquals(SignupViewModel.ERROR_EMAIL_TAKEN, viewModel.uiState.value.emailError)

        fakeRepository.registerResult = sampleSuccessResult
        val events = mutableListOf<SignupEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }

        viewModel.onSignupClick()
        advanceUntilIdle()
        job.cancel()

        val state = viewModel.uiState.value
        assertNull(state.emailError)
        assertNull(state.generalError)
        assertEquals(1, events.size)
        assertTrue(events[0] is SignupEvent.SignupSuccess)
    }
}

