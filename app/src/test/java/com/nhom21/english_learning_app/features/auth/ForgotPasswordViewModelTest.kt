package com.nhom21.english_learning_app.features.auth

import androidx.lifecycle.viewModelScope
import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiFailure
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.core.network.FieldError
import com.nhom21.english_learning_app.data.remote.dto.ForgotPasswordRequest
import com.nhom21.english_learning_app.data.remote.dto.ForgotPasswordResultDto
import com.nhom21.english_learning_app.data.remote.dto.ResetPasswordRequest
import com.nhom21.english_learning_app.data.remote.dto.VerifyOtpRequest
import com.nhom21.english_learning_app.data.remote.dto.VerifyOtpResultDto
import com.nhom21.english_learning_app.features.profile.PasswordPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests cho [ForgotPasswordViewModel] bao phủ đầy đủ các kịch bản T01 -> T16.
 * Chạy thuần JVM với [StandardTestDispatcher] và [FakeAuthRepository], không phụ thuộc emulator/server.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAuthRepository
    private lateinit var viewModel: ForgotPasswordViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAuthRepository()
        viewModel = ForgotPasswordViewModel(fakeRepository, testDispatcher)
    }

    @After
    fun tearDown() {
        viewModel.clearSensitiveData()
        Dispatchers.resetMain()
    }

    private fun enterValidStep1Inputs(email: String = "learner@example.com") {
        viewModel.onEmailOrPhoneChange(email)
        val currentCaptcha = viewModel.uiState.value.captchaCode
        viewModel.onCaptchaInputChange(currentCaptcha)
    }

    private fun advanceToVerifyOtpStep(
        email: String = "learner@example.com",
        retryAfterSeconds: Int = 60
    ) {
        fakeRepository.requestResetOtpResult = ApiResult.Success(
            ForgotPasswordResultDto(retryAfterSeconds = retryAfterSeconds)
        )
        enterValidStep1Inputs(email)
        viewModel.sendVerificationCode()
        testDispatcher.scheduler.runCurrent()
    }

    private fun advanceToResetPasswordStep(
        email: String = "learner@example.com",
        otp: String = "123456",
        issuedResetToken: String = "one-time-reset-token-abc"
    ) {
        advanceToVerifyOtpStep(email)
        fakeRepository.verifyResetOtpResult = ApiResult.Success(
            VerifyOtpResultDto(resetToken = issuedResetToken, expiresInSeconds = 600)
        )
        viewModel.onOtpInputChange(otp)
        viewModel.verifyOtpCode()
        testDispatcher.scheduler.runCurrent()
    }

    // ── T01: Gửi OTP thành công -> gọi repository đúng, chuyển sang bước OTP, resendCooldownSeconds = retryAfterSeconds ──

    @Test
    fun `T01 sendVerificationCode success calls repository transitions to VERIFY_OTP and sets resendCooldownSeconds`() = runTest(testDispatcher) {
        fakeRepository.requestResetOtpResult = ApiResult.Success(
            ForgotPasswordResultDto(retryAfterSeconds = 60)
        )
        enterValidStep1Inputs("  learner@example.com  ")

        viewModel.sendVerificationCode()
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(1, fakeRepository.requestResetOtpCallCount)
        assertEquals("learner@example.com", fakeRepository.lastRequestedEmail)
        assertEquals(ForgotPasswordStep.VERIFY_OTP, state.currentStep)
        assertEquals(60, state.resendCooldownSeconds)
        assertEquals(60, state.resendTimerSeconds)
        assertFalse(state.isResendEnabled)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(ForgotPasswordViewModel.MSG_OTP_SENT, state.successMessage)
    }

    // ── T02: Email sai định dạng / CAPTCHA sai -> không gọi repository, gán lỗi validation ──

    @Test
    fun `T02 sendVerificationCode with invalid email or captcha does not call repository and sets validation error`() = runTest(testDispatcher) {
        // 1. Empty email
        viewModel.onEmailOrPhoneChange("   ")
        viewModel.onCaptchaInputChange(viewModel.uiState.value.captchaCode)
        viewModel.sendVerificationCode()
        assertEquals(0, fakeRepository.requestResetOtpCallCount)
        assertEquals(ForgotPasswordViewModel.ERROR_EMAIL_REQUIRED, viewModel.uiState.value.emailError)
        assertEquals(ForgotPasswordViewModel.ERROR_EMAIL_REQUIRED, viewModel.uiState.value.errorMessage)
        assertEquals(ForgotPasswordStep.REQUEST_CODE, viewModel.uiState.value.currentStep)

        // 2. Malformed email (no @, no domain dot, angle brackets, internal space, > 254 chars)
        val invalidEmails = listOf(
            "not-an-email",
            "user@",
            "@example.com",
            "user@domain",
            "user name@example.com",
            "<user>@example.com",
            "a".repeat(243) + "@example.com"
        )
        for (badEmail in invalidEmails) {
            viewModel.onEmailOrPhoneChange(badEmail)
            viewModel.onCaptchaInputChange(viewModel.uiState.value.captchaCode)
            viewModel.sendVerificationCode()
            assertEquals("Must not call repository for $badEmail", 0, fakeRepository.requestResetOtpCallCount)
            assertEquals(ForgotPasswordViewModel.ERROR_EMAIL_INVALID, viewModel.uiState.value.emailError)
            assertEquals(ForgotPasswordViewModel.ERROR_EMAIL_INVALID, viewModel.uiState.value.errorMessage)
        }

        // 3. Empty CAPTCHA
        viewModel.onEmailOrPhoneChange("valid@example.com")
        viewModel.onCaptchaInputChange("   ")
        viewModel.sendVerificationCode()
        assertEquals(0, fakeRepository.requestResetOtpCallCount)
        assertEquals(ForgotPasswordViewModel.ERROR_CAPTCHA_REQUIRED, viewModel.uiState.value.captchaError)
        assertEquals(ForgotPasswordViewModel.ERROR_CAPTCHA_REQUIRED, viewModel.uiState.value.errorMessage)

        // 4. Wrong CAPTCHA regenerates CAPTCHA and does not call repository
        val oldCaptcha = viewModel.uiState.value.captchaCode
        viewModel.onCaptchaInputChange("ZZZZ")
        viewModel.sendVerificationCode()
        assertEquals(0, fakeRepository.requestResetOtpCallCount)
        assertEquals(ForgotPasswordViewModel.ERROR_CAPTCHA_INVALID, viewModel.uiState.value.errorMessage)
        assertEquals("", viewModel.uiState.value.captchaInput)
    }

    // ── T03: Lỗi mạng khi gửi OTP -> giữ ở bước 1, hiển thị thông báo mạng, giữ nguyên email đã nhập ──

    @Test
    fun `T03 sendVerificationCode on network error stays on REQUEST_CODE shows network error and preserves email`() = runTest(testDispatcher) {
        fakeRepository.requestResetOtpResult = ApiResult.Failure(
            ApiFailure(type = ApiErrorType.NETWORK, message = "Unable to resolve host", httpStatus = null)
        )
        enterValidStep1Inputs("learner@example.com")

        viewModel.sendVerificationCode()
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(1, fakeRepository.requestResetOtpCallCount)
        assertEquals(ForgotPasswordStep.REQUEST_CODE, state.currentStep)
        assertEquals("learner@example.com", state.emailOrPhone)
        assertEquals(ForgotPasswordViewModel.ERROR_NETWORK, state.errorMessage)
        assertFalse(state.isLoading)
    }

    // ── T04: Đếm ngược cooldown bằng thời gian ảo: về 0 mới cho gửi lại, không gọi API khi đang đếm, không tạo 2 coroutine đếm ngược ──

    @Test
    fun `T04 cooldown counts down with virtual time blocks resend until zero and cancels previous timer without duplicate coroutines`() = runTest(testDispatcher) {
        advanceToVerifyOtpStep(retryAfterSeconds = 60)

        assertEquals(60, viewModel.uiState.value.resendCooldownSeconds)
        assertFalse(viewModel.uiState.value.isResendEnabled)
        assertEquals(1, fakeRepository.requestResetOtpCallCount)

        // Advance 25 seconds -> 35 seconds left, resend still blocked
        advanceTimeBy(25_000L)
        runCurrent()
        assertEquals(35, viewModel.uiState.value.resendCooldownSeconds)
        assertFalse(viewModel.uiState.value.isResendEnabled)

        // Clicking resendOtp while cooldown > 0 must NOT call repository
        viewModel.resendOtp()
        runCurrent()
        assertEquals(1, fakeRepository.requestResetOtpCallCount)

        // Restarting timer mid-flight cancels the first timer (no double-decrement)
        viewModel.startResendTimer(10)
        assertEquals(10, viewModel.uiState.value.resendCooldownSeconds)
        advanceTimeBy(1_000L)
        runCurrent()
        // If two coroutines were running, it would have decremented twice; must be exactly 9
        assertEquals(9, viewModel.uiState.value.resendCooldownSeconds)

        // Advance remaining 9 seconds -> reaches 0 and enables resend
        advanceTimeBy(9_000L)
        runCurrent()
        assertEquals(0, viewModel.uiState.value.resendCooldownSeconds)
        assertEquals(0, viewModel.uiState.value.resendTimerSeconds)
        assertTrue(viewModel.uiState.value.isResendEnabled)

        // Now resendOtp is allowed and restarts cooldown
        fakeRepository.requestResetOtpResult = ApiResult.Success(
            ForgotPasswordResultDto(retryAfterSeconds = 60)
        )
        viewModel.resendOtp()
        runCurrent()
        assertEquals(2, fakeRepository.requestResetOtpCallCount)
        assertEquals(60, viewModel.uiState.value.resendCooldownSeconds)
        assertFalse(viewModel.uiState.value.isResendEnabled)
        assertEquals(ForgotPasswordViewModel.MSG_OTP_RESENT, viewModel.uiState.value.successMessage)
    }

    // ── T05: Chống bấm đúp khi isLoading = true ở cả 3 bước -> chỉ gọi API 1 lần, chỉ chuyển bước 1 lần ──

    @Test
    fun `T05 double tap while isLoading is true invokes repository only once across all 3 steps`() = runTest(testDispatcher) {
        // Step 1: sendVerificationCode double-tap
        val step1Gate = CompletableDeferred<ApiResult<ForgotPasswordResultDto>>()
        fakeRepository.requestResetOtpAction = { step1Gate.await() }
        enterValidStep1Inputs("learner@example.com")

        viewModel.sendVerificationCode()
        viewModel.sendVerificationCode()
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)
        assertEquals(1, fakeRepository.requestResetOtpCallCount)

        step1Gate.complete(ApiResult.Success(ForgotPasswordResultDto(retryAfterSeconds = 60)))
        runCurrent()
        assertEquals(ForgotPasswordStep.VERIFY_OTP, viewModel.uiState.value.currentStep)
        assertFalse(viewModel.uiState.value.isLoading)

        // Step 2: verifyOtpCode double-tap
        val step2Gate = CompletableDeferred<ApiResult<VerifyOtpResultDto>>()
        fakeRepository.verifyResetOtpAction = { _, _ -> step2Gate.await() }
        viewModel.onOtpInputChange("123456")

        viewModel.verifyOtpCode()
        viewModel.verifyOtpCode()
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)
        assertEquals(1, fakeRepository.verifyResetOtpCallCount)

        step2Gate.complete(ApiResult.Success(VerifyOtpResultDto(resetToken = "token-xyz", expiresInSeconds = 600)))
        runCurrent()
        assertEquals(ForgotPasswordStep.RESET_PASSWORD, viewModel.uiState.value.currentStep)
        assertFalse(viewModel.uiState.value.isLoading)

        // Step 3: resetPassword double-tap
        val step3Gate = CompletableDeferred<ApiResult<Unit>>()
        fakeRepository.resetPasswordAction = { _, _ -> step3Gate.await() }
        viewModel.onNewPasswordChange("ValidPass1")
        viewModel.onConfirmNewPasswordChange("ValidPass1")

        var successCount = 0
        viewModel.resetPassword { successCount++ }
        viewModel.resetPassword { successCount++ }
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)
        assertEquals(1, fakeRepository.resetPasswordCallCount)

        step3Gate.complete(ApiResult.Success(Unit))
        runCurrent()
        assertEquals(ForgotPasswordStep.SUCCESS, viewModel.uiState.value.currentStep)
        assertEquals(1, successCount)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // ── T06: OTP không phải 6 chữ số -> không gọi API, hiển thị lỗi validation ──

    @Test
    fun `T06 verifyOtpCode with non-6-digit OTP does not call repository and sets validation error`() = runTest(testDispatcher) {
        advanceToVerifyOtpStep()

        // Empty OTP
        viewModel.onOtpInputChange("   ")
        viewModel.verifyOtpCode()
        assertEquals(0, fakeRepository.verifyResetOtpCallCount)
        assertEquals(ForgotPasswordViewModel.ERROR_OTP_REQUIRED, viewModel.uiState.value.otpError)
        assertEquals(ForgotPasswordViewModel.ERROR_OTP_REQUIRED, viewModel.uiState.value.errorMessage)

        // Invalid formats: < 6 digits, > 6 digits, non-digits
        val invalidOtps = listOf("1", "1234", "12345", "1234567", "12345a", "abcdef", "12 345")
        for (badOtp in invalidOtps) {
            viewModel.onOtpInputChange(badOtp)
            viewModel.verifyOtpCode()
            assertEquals("Must not call repository for OTP '$badOtp'", 0, fakeRepository.verifyResetOtpCallCount)
            assertEquals(ForgotPasswordViewModel.ERROR_OTP_FORMAT, viewModel.uiState.value.otpError)
            assertEquals(ForgotPasswordViewModel.ERROR_OTP_FORMAT, viewModel.uiState.value.errorMessage)
            assertEquals(ForgotPasswordStep.VERIFY_OTP, viewModel.uiState.value.currentStep)
        }
    }

    // ── T07: Xác nhận OTP thành công -> lưu resetToken trong ViewModel, chuyển sang bước mật khẩu mới, không lưu resetToken ra ngoài ──

    @Test
    fun `T07 verifyOtpCode success stores resetToken in ViewModel memory transitions to RESET_PASSWORD and clears otpInput`() = runTest(testDispatcher) {
        advanceToVerifyOtpStep("learner@example.com")
        fakeRepository.verifyResetOtpResult = ApiResult.Success(
            VerifyOtpResultDto(resetToken = "secret-reset-token-999", expiresInSeconds = 600)
        )
        viewModel.onOtpInputChange("654321")

        viewModel.verifyOtpCode()
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(1, fakeRepository.verifyResetOtpCallCount)
        assertEquals("learner@example.com", fakeRepository.lastVerifyEmail)
        assertEquals("654321", fakeRepository.lastVerifyOtp)
        assertEquals(ForgotPasswordStep.RESET_PASSWORD, state.currentStep)
        assertTrue(viewModel.hasResetTokenForTest())
        assertEquals("", state.otpInput)
        assertFalse(state.toString().contains("secret-reset-token-999"))
    }

    // ── T08: Xác nhận OTP thất bại với invalid_otp -> giữ ở bước OTP, hiển thị thông báo tiếng Việt đúng, không có resetToken ──

    @Test
    fun `T08 verifyOtpCode failure with INVALID_OTP stays on VERIFY_OTP shows Vietnamese error and has no resetToken`() = runTest(testDispatcher) {
        advanceToVerifyOtpStep("learner@example.com")
        fakeRepository.verifyResetOtpResult = ApiResult.Failure(
            ApiFailure(
                type = ApiErrorType.INVALID_OTP,
                message = "Invalid or expired OTP.",
                httpStatus = 400
            )
        )
        viewModel.onOtpInputChange("000000")

        viewModel.verifyOtpCode()
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(ForgotPasswordStep.VERIFY_OTP, state.currentStep)
        assertFalse(viewModel.hasResetTokenForTest())
        assertEquals(ForgotPasswordViewModel.ERROR_INVALID_OTP, state.otpError)
        assertEquals(ForgotPasswordViewModel.ERROR_INVALID_OTP, state.errorMessage)
        assertFalse(state.isLoading)
    }

    // ── T09: Mật khẩu mới không hợp lệ / không khớp -> dùng chung PasswordPolicy, không gọi API ──

    @Test
    fun `T09 resetPassword validates newPassword and confirmNewPassword using shared PasswordPolicy without calling API`() = runTest(testDispatcher) {
        advanceToResetPasswordStep()

        // 1. Empty newPassword
        viewModel.onNewPasswordChange("")
        viewModel.onConfirmNewPasswordChange("")
        viewModel.resetPassword()
        assertEquals(0, fakeRepository.resetPasswordCallCount)
        assertEquals(PasswordPolicy.ERROR_NEW_REQUIRED, viewModel.uiState.value.newPasswordError)

        // 2. Too short (< 8 code points)
        viewModel.onNewPasswordChange("Abcde12")
        viewModel.onConfirmNewPasswordChange("Abcde12")
        viewModel.resetPassword()
        assertEquals(0, fakeRepository.resetPasswordCallCount)
        assertEquals(PasswordPolicy.ERROR_NEW_TOO_SHORT, viewModel.uiState.value.newPasswordError)

        // 3. Too long (> 64 code points)
        val pass65 = "A1" + "b".repeat(63)
        viewModel.onNewPasswordChange(pass65)
        viewModel.onConfirmNewPasswordChange(pass65)
        viewModel.resetPassword()
        assertEquals(0, fakeRepository.resetPasswordCallCount)
        assertEquals(PasswordPolicy.ERROR_NEW_TOO_LONG, viewModel.uiState.value.newPasswordError)

        // 4. Missing digit
        viewModel.onNewPasswordChange("OnlyLetters")
        viewModel.onConfirmNewPasswordChange("OnlyLetters")
        viewModel.resetPassword()
        assertEquals(0, fakeRepository.resetPasswordCallCount)
        assertEquals(PasswordPolicy.ERROR_NEW_COMPLEXITY, viewModel.uiState.value.newPasswordError)

        // 5. Missing ASCII letter (only digits or Vietnamese diacritics + digits)
        viewModel.onNewPasswordChange("12345678")
        viewModel.onConfirmNewPasswordChange("12345678")
        viewModel.resetPassword()
        assertEquals(0, fakeRepository.resetPasswordCallCount)
        assertEquals(PasswordPolicy.ERROR_NEW_COMPLEXITY, viewModel.uiState.value.newPasswordError)

        // 6. Empty confirm password
        viewModel.onNewPasswordChange("ValidPass1")
        viewModel.onConfirmNewPasswordChange("")
        viewModel.resetPassword()
        assertEquals(0, fakeRepository.resetPasswordCallCount)
        assertEquals(ForgotPasswordViewModel.ERROR_CONFIRM_REQUIRED, viewModel.uiState.value.confirmPasswordError)

        // 7. Mismatched confirm password
        viewModel.onNewPasswordChange("ValidPass1")
        viewModel.onConfirmNewPasswordChange("ValidPass2")
        viewModel.resetPassword()
        assertEquals(0, fakeRepository.resetPasswordCallCount)
        assertEquals(PasswordPolicy.ERROR_CONFIRM_MISMATCH, viewModel.uiState.value.confirmPasswordError)
    }

    // ── T10: Đặt lại mật khẩu thành công -> gọi repository đúng, chuyển sang màn thành công, xóa resetToken và mật khẩu ──

    @Test
    fun `T10 resetPassword success calls repository transitions to SUCCESS and clears resetToken and passwords`() = runTest(testDispatcher) {
        advanceToResetPasswordStep(
            email = "learner@example.com",
            otp = "123456",
            issuedResetToken = "issued-reset-token-777"
        )
        fakeRepository.resetPasswordResult = ApiResult.Success(Unit)

        // Include leading/trailing spaces to verify password is NOT trimmed
        viewModel.onNewPasswordChange("  NewPassw0rd1  ")
        viewModel.onConfirmNewPasswordChange("  NewPassw0rd1  ")

        var callbackInvoked = false
        viewModel.resetPassword { callbackInvoked = true }
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(1, fakeRepository.resetPasswordCallCount)
        assertEquals("issued-reset-token-777", fakeRepository.lastResetToken)
        assertEquals("  NewPassw0rd1  ", fakeRepository.lastNewPassword)
        assertTrue(callbackInvoked)
        assertEquals(ForgotPasswordStep.SUCCESS, state.currentStep)
        assertFalse(viewModel.hasResetTokenForTest())
        assertEquals("", state.newPassword)
        assertEquals("", state.confirmNewPassword)
        assertEquals("", state.otpInput)
        assertNull(state.errorMessage)
        assertEquals(ForgotPasswordViewModel.MSG_RESET_SUCCESS, state.successMessage)
    }

    // ── T11: Đặt lại mật khẩu thất bại với invalid_reset_token -> xóa resetToken, quay về bước 1, hiển thị thông báo hết hạn ──

    @Test
    fun `T11 resetPassword failure with INVALID_RESET_TOKEN clears resetToken and passwords and returns to REQUEST_CODE`() = runTest(testDispatcher) {
        advanceToResetPasswordStep(issuedResetToken = "expired-reset-token")
        fakeRepository.resetPasswordResult = ApiResult.Failure(
            ApiFailure(
                type = ApiErrorType.INVALID_RESET_TOKEN,
                message = "Invalid or expired reset token.",
                httpStatus = 400
            )
        )
        viewModel.onNewPasswordChange("NewPassw0rd1")
        viewModel.onConfirmNewPasswordChange("NewPassw0rd1")

        viewModel.resetPassword()
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(ForgotPasswordStep.REQUEST_CODE, state.currentStep)
        assertFalse(viewModel.hasResetTokenForTest())
        assertEquals("", state.newPassword)
        assertEquals("", state.confirmNewPassword)
        assertEquals("", state.otpInput)
        assertEquals(ForgotPasswordViewModel.ERROR_INVALID_RESET_TOKEN, state.errorMessage)
        assertFalse(state.isLoading)
    }

    // ── T12: Lỗi HTTP 422 khi đặt lại mật khẩu -> hiển thị lỗi field/validation, không crash, giữ ở bước 3 để thử lại ──

    @Test
    fun `T12 resetPassword 422 validation error maps field error stays on RESET_PASSWORD and preserves resetToken for retry`() = runTest(testDispatcher) {
        advanceToResetPasswordStep(issuedResetToken = "valid-token-for-retry")
        fakeRepository.resetPasswordResult = ApiResult.Failure(
            ApiFailure(
                type = ApiErrorType.VALIDATION_ERROR,
                message = "Validation Error",
                fieldErrors = listOf(
                    FieldError(
                        field = "newPassword",
                        message = "String should have at most 64 characters",
                        code = "MAX_LENGTH"
                    )
                ),
                httpStatus = 422
            )
        )
        viewModel.onNewPasswordChange("NewPassw0rd1")
        viewModel.onConfirmNewPasswordChange("NewPassw0rd1")

        viewModel.resetPassword()
        runCurrent()

        var state = viewModel.uiState.value
        assertEquals(ForgotPasswordStep.RESET_PASSWORD, state.currentStep)
        assertTrue(viewModel.hasResetTokenForTest())
        assertEquals(PasswordPolicy.ERROR_NEW_TOO_LONG, state.newPasswordError)
        assertEquals(PasswordPolicy.ERROR_NEW_TOO_LONG, state.errorMessage)
        assertFalse(state.isLoading)

        // Retry with valid password succeeds using the preserved resetToken
        fakeRepository.resetPasswordResult = ApiResult.Success(Unit)
        viewModel.onNewPasswordChange("ValidRetry1")
        viewModel.onConfirmNewPasswordChange("ValidRetry1")
        viewModel.resetPassword()
        runCurrent()

        state = viewModel.uiState.value
        assertEquals(ForgotPasswordStep.SUCCESS, state.currentStep)
        assertEquals("valid-token-for-retry", fakeRepository.lastResetToken)
        assertFalse(viewModel.hasResetTokenForTest())
    }

    // ── T13: Điều hướng quay lại -> xóa OTP, mật khẩu, resetToken; từ bước 3 quay về bước 1 (không về bước 2) ──

    @Test
    fun `T13 goToPreviousStep from VERIFY_OTP and RESET_PASSWORD clears sensitive data and from step 3 goes directly to step 1`() = runTest(testDispatcher) {
        // Case A: From VERIFY_OTP -> REQUEST_CODE
        advanceToVerifyOtpStep()
        viewModel.onOtpInputChange("123")
        viewModel.goToPreviousStep()

        var state = viewModel.uiState.value
        assertEquals(ForgotPasswordStep.REQUEST_CODE, state.currentStep)
        assertEquals("", state.otpInput)
        assertEquals(0, state.resendCooldownSeconds)
        assertFalse(viewModel.hasResetTokenForTest())

        // Case B: From RESET_PASSWORD -> goes directly to REQUEST_CODE (NOT VERIFY_OTP) and clears resetToken & passwords
        advanceToResetPasswordStep(issuedResetToken = "token-to-be-cleared")
        viewModel.onNewPasswordChange("SecretPass1")
        viewModel.onConfirmNewPasswordChange("SecretPass1")
        assertTrue(viewModel.hasResetTokenForTest())

        viewModel.goToPreviousStep()

        state = viewModel.uiState.value
        assertNotEquals(ForgotPasswordStep.VERIFY_OTP, state.currentStep)
        assertEquals(ForgotPasswordStep.REQUEST_CODE, state.currentStep)
        assertFalse(viewModel.hasResetTokenForTest())
        assertEquals("", state.otpInput)
        assertEquals("", state.newPassword)
        assertEquals("", state.confirmNewPassword)
        assertNull(state.errorMessage)
    }

    // ── T14: isLoading luôn trở về false sau thành công, lỗi HTTP, lỗi mạng, timeout hoặc ngoại lệ ──

    @Test
    fun `T14 isLoading always resets to false after success HTTP error network error or unexpected exception`() = runTest(testDispatcher) {
        // 1. Step 1 HTTP 500 error
        fakeRepository.requestResetOtpResult = ApiResult.Failure(
            ApiFailure(type = ApiErrorType.INTERNAL_ERROR, message = "Crash", httpStatus = 500)
        )
        enterValidStep1Inputs("learner@example.com")
        viewModel.sendVerificationCode()
        runCurrent()
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(ForgotPasswordViewModel.ERROR_SERVER, viewModel.uiState.value.errorMessage)

        // 2. Step 2 Network error
        advanceToVerifyOtpStep("learner@example.com")
        fakeRepository.verifyResetOtpResult = ApiResult.Failure(
            ApiFailure(type = ApiErrorType.NETWORK, message = "Timeout", httpStatus = null)
        )
        viewModel.onOtpInputChange("123456")
        viewModel.verifyOtpCode()
        runCurrent()
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(ForgotPasswordViewModel.ERROR_NETWORK, viewModel.uiState.value.errorMessage)

        // 3. Step 3 Network error
        advanceToResetPasswordStep()
        fakeRepository.resetPasswordResult = ApiResult.Failure(
            ApiFailure(type = ApiErrorType.NETWORK, message = "Timeout", httpStatus = null)
        )
        viewModel.onNewPasswordChange("ValidPass1")
        viewModel.onConfirmNewPasswordChange("ValidPass1")
        viewModel.resetPassword()
        runCurrent()
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(ForgotPasswordViewModel.ERROR_NETWORK, viewModel.uiState.value.errorMessage)
    }

    // ── T15: Ngoại lệ bất ngờ từ repository -> không crash, isLoading = false, thông báo chung không lộ chi tiết kỹ thuật ──

    @Test
    fun `T15 unexpected exception from repository does not crash resets isLoading and hides internal exception details`() = runTest(testDispatcher) {
        // Step 1 unexpected exception
        fakeRepository.requestResetOtpAction = {
            throw RuntimeException("Database connection pool exhausted at 10.0.0.12")
        }
        enterValidStep1Inputs("learner@example.com")
        viewModel.sendVerificationCode()
        runCurrent()

        var state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(ForgotPasswordViewModel.ERROR_GENERAL, state.errorMessage)
        assertFalse(state.errorMessage!!.contains("Database"))
        assertFalse(state.errorMessage!!.contains("10.0.0.12"))

        // Step 2 unexpected exception
        fakeRepository.requestResetOtpAction = null
        advanceToVerifyOtpStep("learner@example.com")
        fakeRepository.verifyResetOtpAction = { _, _ ->
            throw IllegalStateException("Secret internal stack trace")
        }
        viewModel.onOtpInputChange("123456")
        viewModel.verifyOtpCode()
        runCurrent()

        state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(ForgotPasswordViewModel.ERROR_GENERAL, state.errorMessage)
        assertFalse(state.errorMessage!!.contains("Secret"))

        // Step 3 unexpected exception
        fakeRepository.verifyResetOtpAction = null
        advanceToResetPasswordStep()
        fakeRepository.resetPasswordAction = { _, _ ->
            throw RuntimeException("NullPointerException in native crypto")
        }
        viewModel.onNewPasswordChange("ValidPass1")
        viewModel.onConfirmNewPasswordChange("ValidPass1")
        viewModel.resetPassword()
        runCurrent()

        state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(ForgotPasswordViewModel.ERROR_GENERAL, state.errorMessage)
        assertFalse(state.errorMessage!!.contains("NullPointerException"))
    }

    @Test
    fun `T15b coroutine cancellation rethrows CancellationException and resets isLoading`() = runTest(testDispatcher) {
        var caughtCancellation: CancellationException? = null
        fakeRepository.requestResetOtpAction = {
            try {
                awaitCancellation()
            } catch (e: CancellationException) {
                caughtCancellation = e
                throw e
            }
        }
        enterValidStep1Inputs("learner@example.com")
        viewModel.sendVerificationCode()
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.viewModelScope.cancel()
        runCurrent()

        assertNotNull(caughtCancellation)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // ── T16: Bảo mật dữ liệu -> uiState.toString() và DTO toString() không chứa resetToken, mật khẩu, OTP, email ──

    @Test
    fun `T16 uiState and DTO toString redact resetToken passwords otpInput and email`() = runTest(testDispatcher) {
        advanceToResetPasswordStep(
            email = "sensitive.user@example.com",
            otp = "987654",
            issuedResetToken = "super-secret-reset-token-value"
        )
        viewModel.onNewPasswordChange("MySecretPass123")
        viewModel.onConfirmNewPasswordChange("MySecretPass123")

        val uiStateDump = viewModel.uiState.value.toString()
        assertFalse(uiStateDump.contains("super-secret-reset-token-value"))
        assertFalse(uiStateDump.contains("MySecretPass123"))
        assertFalse(uiStateDump.contains("987654"))
        assertFalse(uiStateDump.contains("sensitive.user@example.com"))
        assertTrue(uiStateDump.contains("REDACTED"))

        // Also check DTO toString() redaction
        val req1 = ForgotPasswordRequest(email = "sensitive.user@example.com").toString()
        val req2 = VerifyOtpRequest(email = "sensitive.user@example.com", otp = "987654").toString()
        val res2 = VerifyOtpResultDto(resetToken = "super-secret-reset-token-value", expiresInSeconds = 600).toString()
        val req3 = ResetPasswordRequest(
            resetToken = "super-secret-reset-token-value",
            newPassword = "MySecretPass123"
        ).toString()

        assertFalse(req1.contains("sensitive.user@example.com"))
        assertFalse(req2.contains("sensitive.user@example.com"))
        assertFalse(req2.contains("987654"))
        assertFalse(res2.contains("super-secret-reset-token-value"))
        assertFalse(req3.contains("super-secret-reset-token-value"))
        assertFalse(req3.contains("MySecretPass123"))
    }
}

