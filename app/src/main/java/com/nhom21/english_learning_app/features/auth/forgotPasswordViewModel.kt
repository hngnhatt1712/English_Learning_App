package com.nhom21.english_learning_app.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiFailure
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.repository.AuthRepository
import com.nhom21.english_learning_app.features.profile.PasswordPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * ViewModel quản lý logic và trạng thái UI 4 bước cho màn hình Quên / Đặt lại mật khẩu.
 *
 * Tuân thủ:
 * - A-01..A-09: Gọi 3 API thật (`forgot-password`, `verify-otp`, `reset-password`),
 *   giữ nguyên CAPTCHA ở Bước 1, quản lý `resetToken` chỉ trong bộ nhớ ViewModel,
 *   cooldown đếm ngược theo `retryAfterSeconds`, chống double-tap và dọn dữ liệu nhạy cảm.
 */
@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private var timerDispatcher: CoroutineDispatcher? = null

    constructor(
        authRepository: AuthRepository,
        timerDispatcher: CoroutineDispatcher
    ) : this(authRepository) {
        this.timerDispatcher = timerDispatcher
    }

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    /**
     * One-time reset token returned by verify-otp.
     * Kept strictly in ViewModel memory (never in UiState, SavedStateHandle, DataStore, or logs).
     */
    private var resetToken: String? = null

    private var timerJob: Job? = null

    init {
        generateNewCaptcha()
    }

    /**
     * Internal test helper to verify whether a resetToken is currently held in memory
     * without exposing the token value itself.
     */
    internal fun hasResetTokenForTest(): Boolean = !resetToken.isNullOrBlank()

    fun onEmailOrPhoneChange(newValue: String) {
        _uiState.update {
            it.copy(
                emailOrPhone = newValue,
                emailError = null,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onCaptchaInputChange(newValue: String) {
        _uiState.update {
            it.copy(
                captchaInput = newValue,
                captchaError = null,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onOtpInputChange(newValue: String) {
        _uiState.update {
            it.copy(
                otpInput = newValue,
                otpError = null,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onNewPasswordChange(newValue: String) {
        _uiState.update {
            it.copy(
                newPassword = newValue,
                newPasswordError = null,
                confirmPasswordError = null,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onConfirmNewPasswordChange(newValue: String) {
        _uiState.update {
            it.copy(
                confirmNewPassword = newValue,
                confirmPasswordError = null,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onTogglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun onToggleConfirmPasswordVisibility() {
        _uiState.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }
    }

    fun generateNewCaptcha() {
        val chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val code = (1..4).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        _uiState.update {
            it.copy(
                captchaCode = code,
                captchaInput = "",
                captchaError = null
            )
        }
    }

    // Bước 1: Gửi mã xác nhận (POST /api/v1/auth/forgot-password)
    fun sendVerificationCode() {
        val state = _uiState.value
        if (state.isLoading) return

        val trimmedEmail = state.emailOrPhone.trim()
        val emailErr = validateEmail(trimmedEmail)
        if (emailErr != null) {
            _uiState.update {
                it.copy(
                    emailError = emailErr,
                    errorMessage = emailErr,
                    successMessage = null
                )
            }
            return
        }

        // Giữ nguyên logic CAPTCHA phía client hiện có (A-03)
        if (state.captchaInput.isBlank()) {
            _uiState.update {
                it.copy(
                    captchaError = ERROR_CAPTCHA_REQUIRED,
                    errorMessage = ERROR_CAPTCHA_REQUIRED,
                    successMessage = null
                )
            }
            return
        }
        if (!state.captchaInput.trim().equals(state.captchaCode, ignoreCase = true)) {
            generateNewCaptcha()
            _uiState.update {
                it.copy(
                    captchaError = ERROR_CAPTCHA_INVALID,
                    errorMessage = ERROR_CAPTCHA_INVALID,
                    successMessage = null
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                emailError = null,
                captchaError = null,
                errorMessage = null,
                successMessage = null
            )
        }

        viewModelScope.launch {
            try {
                when (val result = authRepository.requestResetOtp(trimmedEmail)) {
                    is ApiResult.Success -> {
                        val cooldown = result.data.retryAfterSeconds.takeIf { it > 0 }
                            ?: DEFAULT_COOLDOWN_SECONDS
                        _uiState.update {
                            it.copy(
                                emailOrPhone = trimmedEmail,
                                currentStep = ForgotPasswordStep.VERIFY_OTP,
                                otpInput = "",
                                otpError = null,
                                errorMessage = null,
                                successMessage = MSG_OTP_SENT
                            )
                        }
                        startResendTimer(cooldown)
                    }

                    is ApiResult.Failure -> {
                        handleRequestOtpFailure(result.error)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(
                        errorMessage = ERROR_GENERAL,
                        successMessage = null
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    // Đếm ngược gửi lại mã OTP (A-05)
    fun startResendTimer(seconds: Int = DEFAULT_COOLDOWN_SECONDS) {
        timerJob?.cancel()
        val initialSeconds = seconds.coerceAtLeast(0)
        if (initialSeconds == 0) {
            _uiState.update {
                it.copy(
                    resendCooldownSeconds = 0,
                    resendTimerSeconds = 0,
                    isResendEnabled = true
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                resendCooldownSeconds = initialSeconds,
                resendTimerSeconds = initialSeconds,
                isResendEnabled = false
            )
        }

        val launchBlock: suspend kotlinx.coroutines.CoroutineScope.() -> Unit = {
            for (sec in (initialSeconds - 1) downTo 0) {
                delay(1_000L)
                _uiState.update {
                    it.copy(
                        resendCooldownSeconds = sec,
                        resendTimerSeconds = sec,
                        isResendEnabled = (sec == 0)
                    )
                }
            }
        }

        val customDispatcher = timerDispatcher
        timerJob = if (customDispatcher != null) {
            viewModelScope.launch(customDispatcher, block = launchBlock)
        } else {
            viewModelScope.launch(block = launchBlock)
        }
    }

    // Gửi lại OTP khi hết thời gian chờ (A-05, A-08)
    fun resendOtp() {
        val state = _uiState.value
        if (state.isLoading || state.resendCooldownSeconds > 0 || !state.isResendEnabled) {
            return
        }

        val trimmedEmail = state.emailOrPhone.trim()
        val emailErr = validateEmail(trimmedEmail)
        if (emailErr != null) {
            _uiState.update {
                it.copy(
                    emailError = emailErr,
                    errorMessage = emailErr,
                    successMessage = null
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                otpError = null,
                errorMessage = null,
                successMessage = null
            )
        }

        viewModelScope.launch {
            try {
                when (val result = authRepository.requestResetOtp(trimmedEmail)) {
                    is ApiResult.Success -> {
                        val cooldown = result.data.retryAfterSeconds.takeIf { it > 0 }
                            ?: DEFAULT_COOLDOWN_SECONDS
                        _uiState.update {
                            it.copy(
                                otpInput = "",
                                otpError = null,
                                errorMessage = null,
                                successMessage = MSG_OTP_RESENT
                            )
                        }
                        startResendTimer(cooldown)
                    }

                    is ApiResult.Failure -> {
                        handleRequestOtpFailure(result.error)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(
                        errorMessage = ERROR_GENERAL,
                        successMessage = null
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    // Bước 2: Xác nhận mã OTP (POST /api/v1/auth/forgot-password/verify-otp)
    fun verifyOtpCode() {
        val state = _uiState.value
        if (state.isLoading) return

        val trimmedOtp = state.otpInput.trim()
        val otpErr = validateOtp(trimmedOtp)
        if (otpErr != null) {
            _uiState.update {
                it.copy(
                    otpError = otpErr,
                    errorMessage = otpErr,
                    successMessage = null
                )
            }
            return
        }

        val trimmedEmail = state.emailOrPhone.trim()
        _uiState.update {
            it.copy(
                isLoading = true,
                otpError = null,
                errorMessage = null,
                successMessage = null
            )
        }

        viewModelScope.launch {
            try {
                when (val result = authRepository.verifyResetOtp(trimmedEmail, trimmedOtp)) {
                    is ApiResult.Success -> {
                        resetToken = result.data.resetToken
                        timerJob?.cancel()
                        _uiState.update {
                            it.copy(
                                currentStep = ForgotPasswordStep.RESET_PASSWORD,
                                otpInput = "",
                                otpError = null,
                                newPassword = "",
                                confirmNewPassword = "",
                                newPasswordError = null,
                                confirmPasswordError = null,
                                resendCooldownSeconds = 0,
                                resendTimerSeconds = 0,
                                isResendEnabled = false,
                                errorMessage = null,
                                successMessage = null
                            )
                        }
                    }

                    is ApiResult.Failure -> {
                        resetToken = null
                        handleVerifyOtpFailure(result.error)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                resetToken = null
                _uiState.update {
                    it.copy(
                        errorMessage = ERROR_GENERAL,
                        successMessage = null
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    // Bước 3: Đặt lại mật khẩu mới (POST /api/v1/auth/reset-password)
    fun resetPassword(onSuccess: () -> Unit = {}) {
        val state = _uiState.value
        if (state.isLoading) return

        val rawNewPassword = state.newPassword
        val rawConfirmPassword = state.confirmNewPassword

        val newPasswordErr = PasswordPolicy.validateNewPassword(rawNewPassword)
        val confirmErr = when {
            rawConfirmPassword.isEmpty() -> ERROR_CONFIRM_REQUIRED
            else -> PasswordPolicy.validateConfirmPassword(rawNewPassword, rawConfirmPassword)
        }

        if (newPasswordErr != null || confirmErr != null) {
            _uiState.update {
                it.copy(
                    newPasswordError = newPasswordErr,
                    confirmPasswordError = confirmErr,
                    errorMessage = newPasswordErr ?: confirmErr,
                    successMessage = null
                )
            }
            return
        }

        val currentResetToken = resetToken
        if (currentResetToken.isNullOrBlank()) {
            handleExpiredResetSession()
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                newPasswordError = null,
                confirmPasswordError = null,
                errorMessage = null,
                successMessage = null
            )
        }

        viewModelScope.launch {
            try {
                when (val result = authRepository.resetPassword(currentResetToken, rawNewPassword)) {
                    is ApiResult.Success -> {
                        resetToken = null
                        timerJob?.cancel()
                        _uiState.update {
                            it.copy(
                                currentStep = ForgotPasswordStep.SUCCESS,
                                otpInput = "",
                                newPassword = "",
                                confirmNewPassword = "",
                                newPasswordError = null,
                                confirmPasswordError = null,
                                errorMessage = null,
                                successMessage = MSG_RESET_SUCCESS
                            )
                        }
                        onSuccess()
                    }

                    is ApiResult.Failure -> {
                        handleResetPasswordFailure(result.error)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(
                        errorMessage = ERROR_GENERAL,
                        successMessage = null
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Điều hướng quay lại bước trước (A-09, T13):
     * - Từ `VERIFY_OTP` -> về `REQUEST_CODE`, hủy timer, xóa `otpInput` và dữ liệu nhạy cảm.
     * - Từ `RESET_PASSWORD` -> KHÔNG quay về `VERIFY_OTP` (vì OTP đã được tiêu thụ trên backend);
     *   quay thẳng về `REQUEST_CODE` và xóa `resetToken`, mật khẩu và OTP.
     */
    fun goToPreviousStep() {
        val state = _uiState.value
        if (state.isLoading) return

        when (state.currentStep) {
            ForgotPasswordStep.VERIFY_OTP,
            ForgotPasswordStep.RESET_PASSWORD -> {
                timerJob?.cancel()
                resetToken = null
                generateNewCaptcha()
                _uiState.update {
                    it.copy(
                        currentStep = ForgotPasswordStep.REQUEST_CODE,
                        otpInput = "",
                        newPassword = "",
                        confirmNewPassword = "",
                        isPasswordVisible = false,
                        isConfirmPasswordVisible = false,
                        resendCooldownSeconds = 0,
                        resendTimerSeconds = 0,
                        isResendEnabled = false,
                        emailError = null,
                        captchaError = null,
                        otpError = null,
                        newPasswordError = null,
                        confirmPasswordError = null,
                        errorMessage = null,
                        successMessage = null
                    )
                }
            }

            else -> Unit
        }
    }

    /**
     * Dọn toàn bộ dữ liệu nhạy cảm khi người dùng rời màn hình Quên mật khẩu (A-09).
     */
    fun clearSensitiveData() {
        timerJob?.cancel()
        resetToken = null
        _uiState.update {
            it.copy(
                otpInput = "",
                newPassword = "",
                confirmNewPassword = "",
                isPasswordVisible = false,
                isConfirmPasswordVisible = false,
                resendCooldownSeconds = 0,
                resendTimerSeconds = 0,
                isResendEnabled = false,
                otpError = null,
                newPasswordError = null,
                confirmPasswordError = null,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        resetToken = null
        super.onCleared()
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun validateEmail(trimmedEmail: String): String? {
        return when {
            trimmedEmail.isEmpty() -> ERROR_EMAIL_REQUIRED
            trimmedEmail.length > MAX_EMAIL_LENGTH -> ERROR_EMAIL_INVALID
            !EMAIL_REGEX.matches(trimmedEmail) -> ERROR_EMAIL_INVALID
            else -> null
        }
    }

    private fun validateOtp(trimmedOtp: String): String? {
        return when {
            trimmedOtp.isEmpty() -> ERROR_OTP_REQUIRED
            !OTP_REGEX.matches(trimmedOtp) -> ERROR_OTP_FORMAT
            else -> null
        }
    }

    private fun handleRequestOtpFailure(failure: ApiFailure) {
        when (failure.type) {
            ApiErrorType.VALIDATION_ERROR -> {
                var emailErr: String? = null
                for (fe in failure.fieldErrors) {
                    if (fe.field == "email") {
                        emailErr = ERROR_EMAIL_INVALID
                    }
                }
                val msg = emailErr ?: ERROR_GENERAL
                _uiState.update {
                    it.copy(
                        emailError = emailErr,
                        errorMessage = msg,
                        successMessage = null
                    )
                }
            }

            ApiErrorType.NETWORK -> {
                _uiState.update {
                    it.copy(
                        errorMessage = ERROR_NETWORK,
                        successMessage = null
                    )
                }
            }

            ApiErrorType.INTERNAL_ERROR -> {
                _uiState.update {
                    it.copy(
                        errorMessage = ERROR_SERVER,
                        successMessage = null
                    )
                }
            }

            else -> {
                val msg = if (failure.httpStatus == 500) ERROR_SERVER else ERROR_GENERAL
                _uiState.update {
                    it.copy(
                        errorMessage = msg,
                        successMessage = null
                    )
                }
            }
        }
    }

    private fun handleVerifyOtpFailure(failure: ApiFailure) {
        when (failure.type) {
            ApiErrorType.INVALID_OTP -> {
                _uiState.update {
                    it.copy(
                        otpError = ERROR_INVALID_OTP,
                        errorMessage = ERROR_INVALID_OTP,
                        successMessage = null
                    )
                }
            }

            ApiErrorType.VALIDATION_ERROR -> {
                var otpErr: String? = null
                var emailErr: String? = null
                for (fe in failure.fieldErrors) {
                    when (fe.field) {
                        "otp" -> otpErr = ERROR_OTP_FORMAT
                        "email" -> emailErr = ERROR_EMAIL_INVALID
                    }
                }
                val msg = otpErr ?: emailErr ?: ERROR_GENERAL
                _uiState.update {
                    it.copy(
                        otpError = otpErr,
                        emailError = emailErr,
                        errorMessage = msg,
                        successMessage = null
                    )
                }
            }

            ApiErrorType.NETWORK -> {
                _uiState.update {
                    it.copy(
                        errorMessage = ERROR_NETWORK,
                        successMessage = null
                    )
                }
            }

            ApiErrorType.INTERNAL_ERROR -> {
                _uiState.update {
                    it.copy(
                        errorMessage = ERROR_SERVER,
                        successMessage = null
                    )
                }
            }

            else -> {
                val msg = if (failure.httpStatus == 500) ERROR_SERVER else ERROR_GENERAL
                _uiState.update {
                    it.copy(
                        errorMessage = msg,
                        successMessage = null
                    )
                }
            }
        }
    }

    private fun handleResetPasswordFailure(failure: ApiFailure) {
        when (failure.type) {
            ApiErrorType.INVALID_RESET_TOKEN -> {
                handleExpiredResetSession()
            }

            ApiErrorType.VALIDATION_ERROR -> {
                var newPwErr: String? = null
                for (fe in failure.fieldErrors) {
                    if (fe.field == "newPassword") {
                        newPwErr = mapNewPasswordValidationCode(fe.code)
                        break
                    }
                }
                val msg = newPwErr ?: ERROR_GENERAL
                _uiState.update {
                    it.copy(
                        newPasswordError = newPwErr,
                        errorMessage = msg,
                        successMessage = null
                    )
                }
            }

            ApiErrorType.NETWORK -> {
                _uiState.update {
                    it.copy(
                        errorMessage = ERROR_NETWORK,
                        successMessage = null
                    )
                }
            }

            ApiErrorType.INTERNAL_ERROR -> {
                _uiState.update {
                    it.copy(
                        errorMessage = ERROR_SERVER,
                        successMessage = null
                    )
                }
            }

            else -> {
                val msg = if (failure.httpStatus == 500) ERROR_SERVER else ERROR_GENERAL
                _uiState.update {
                    it.copy(
                        errorMessage = msg,
                        successMessage = null
                    )
                }
            }
        }
    }

    private fun handleExpiredResetSession() {
        timerJob?.cancel()
        resetToken = null
        generateNewCaptcha()
        _uiState.update {
            it.copy(
                currentStep = ForgotPasswordStep.REQUEST_CODE,
                otpInput = "",
                newPassword = "",
                confirmNewPassword = "",
                isPasswordVisible = false,
                isConfirmPasswordVisible = false,
                resendCooldownSeconds = 0,
                resendTimerSeconds = 0,
                isResendEnabled = false,
                otpError = null,
                newPasswordError = null,
                confirmPasswordError = null,
                errorMessage = ERROR_INVALID_RESET_TOKEN,
                successMessage = null
            )
        }
    }

    private fun mapNewPasswordValidationCode(code: String?): String {
        return when (code) {
            "REQUIRED" -> PasswordPolicy.ERROR_NEW_REQUIRED
            "MIN_LENGTH" -> PasswordPolicy.ERROR_NEW_TOO_SHORT
            "MAX_LENGTH" -> PasswordPolicy.ERROR_NEW_TOO_LONG
            "INVALID_FORMAT" -> PasswordPolicy.ERROR_NEW_COMPLEXITY
            else -> PasswordPolicy.ERROR_NEW_INVALID
        }
    }

    companion object {
        const val DEFAULT_COOLDOWN_SECONDS = 60
        const val MAX_EMAIL_LENGTH = 254

        private val EMAIL_REGEX = Regex("^[^@\\s<>]+@[^@\\s<>]+\\.[^@\\s<>]+$")
        private val OTP_REGEX = Regex("^\\d{6}$")

        // Thông báo lỗi tiếng Việt chuẩn hóa theo A-06, A-07 & strings.xml
        const val ERROR_EMAIL_REQUIRED = "Vui lòng nhập email"
        const val ERROR_EMAIL_INVALID = "Email không hợp lệ"
        const val ERROR_CAPTCHA_REQUIRED = "Vui lòng nhập mã xác nhận (CAPTCHA)!"
        const val ERROR_CAPTCHA_INVALID = "Mã xác nhận (CAPTCHA) không chính xác!"
        const val ERROR_OTP_REQUIRED = "Vui lòng nhập mã OTP"
        const val ERROR_OTP_FORMAT = "Mã OTP phải gồm đúng 6 chữ số"
        const val ERROR_INVALID_OTP = "Mã không đúng hoặc đã hết hạn. Vui lòng yêu cầu mã mới."
        const val ERROR_INVALID_RESET_TOKEN = "Phiên đặt lại mật khẩu đã hết hạn. Vui lòng thực hiện lại."
        const val ERROR_CONFIRM_REQUIRED = "Vui lòng nhập lại mật khẩu"
        const val ERROR_NETWORK = "Không kết nối được máy chủ"
        const val ERROR_SERVER = "Lỗi hệ thống, vui lòng thử lại"
        const val ERROR_GENERAL = "Đã xảy ra lỗi, vui lòng thử lại sau"

        // Thông báo thành công tiếng Việt
        const val MSG_OTP_SENT = "Mã OTP đã được gửi thành công!"
        const val MSG_OTP_RESENT = "Mã OTP mới đã được gửi lại!"
        const val MSG_RESET_SUCCESS = "Mật khẩu của bạn đã được đặt lại thành công!"
    }
}
