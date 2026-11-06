package com.nhom21.english_learning_app.features.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * ViewModel quản lý logic và trạng thái UI 4 bước cho màn hình Quên / Đặt lại mật khẩu.
 */
class ForgotPasswordViewModel : ViewModel() {

    var uiState by mutableStateOf(ForgotPasswordUiState())
        private set

    private var timerJob: Job? = null

    init {
        generateNewCaptcha()
    }

    fun onEmailOrPhoneChange(newValue: String) {
        uiState = uiState.copy(emailOrPhone = newValue, errorMessage = null, successMessage = null)
    }

    fun onCaptchaInputChange(newValue: String) {
        uiState = uiState.copy(captchaInput = newValue, errorMessage = null, successMessage = null)
    }

    fun onOtpInputChange(newValue: String) {
        uiState = uiState.copy(otpInput = newValue, errorMessage = null, successMessage = null)
    }

    fun onNewPasswordChange(newValue: String) {
        uiState = uiState.copy(newPassword = newValue, errorMessage = null, successMessage = null)
    }

    fun onConfirmNewPasswordChange(newValue: String) {
        uiState = uiState.copy(confirmNewPassword = newValue, errorMessage = null, successMessage = null)
    }

    fun onTogglePasswordVisibility() {
        uiState = uiState.copy(isPasswordVisible = !uiState.isPasswordVisible)
    }

    fun onToggleConfirmPasswordVisibility() {
        uiState = uiState.copy(isConfirmPasswordVisible = !uiState.isConfirmPasswordVisible)
    }

    fun generateNewCaptcha() {
        val chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val code = (1..4).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        uiState = uiState.copy(captchaCode = code, captchaInput = "", errorMessage = null)
    }

    // Bước 1: Gửi mã xác nhận
    fun sendVerificationCode() {
        val state = uiState
        if (state.emailOrPhone.isBlank()) {
            uiState = state.copy(errorMessage = "Vui lòng nhập email hoặc số điện thoại!")
            return
        }
        if (state.captchaInput.isBlank()) {
            uiState = state.copy(errorMessage = "Vui lòng nhập mã xác nhận (CAPTCHA)!")
            return
        }
        if (!state.captchaInput.trim().equals(state.captchaCode, ignoreCase = true)) {
            uiState = state.copy(errorMessage = "Mã xác nhận (CAPTCHA) không chính xác!")
            generateNewCaptcha()
            return
        }

        uiState = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            delay(1200) // Giả lập network delay
            uiState = uiState.copy(
                isLoading = false,
                currentStep = ForgotPasswordStep.VERIFY_OTP,
                successMessage = "Mã OTP đã được gửi thành công!"
            )
            startResendTimer()
        }
    }

    // Bước 2: Xác nhận mã OTP
    fun verifyOtpCode() {
        val state = uiState
        if (state.otpInput.isBlank()) {
            uiState = state.copy(errorMessage = "Vui lòng nhập mã OTP!")
            return
        }
        if (state.otpInput.trim().length < 4) {
            uiState = state.copy(errorMessage = "Mã OTP phải có đủ 4-6 chữ số!")
            return
        }

        uiState = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            delay(1200)
            uiState = uiState.copy(
                isLoading = false,
                currentStep = ForgotPasswordStep.RESET_PASSWORD,
                errorMessage = null
            )
        }
    }

    // Đếm ngược gửi lại mã OTP
    fun startResendTimer() {
        timerJob?.cancel()
        uiState = uiState.copy(resendTimerSeconds = 60, isResendEnabled = false)
        timerJob = viewModelScope.launch {
            for (sec in 59 downTo 0) {
                delay(1000)
                uiState = uiState.copy(resendTimerSeconds = sec)
            }
            uiState = uiState.copy(isResendEnabled = true)
        }
    }

    fun resendOtp() {
        if (!uiState.isResendEnabled) return
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            delay(1000)
            uiState = uiState.copy(
                isLoading = false,
                successMessage = "Mã OTP mới đã được gửi lại!"
            )
            startResendTimer()
        }
    }

    // Bước 3: Đặt lại mật khẩu mới
    fun resetPassword(onSuccess: () -> Unit = {}) {
        val state = uiState
        if (state.newPassword.isBlank() || state.confirmNewPassword.isBlank()) {
            uiState = state.copy(errorMessage = "Vui lòng nhập đầy đủ mật khẩu mới!")
            return
        }
        if (state.newPassword.length < 8) {
            uiState = state.copy(errorMessage = "Mật khẩu mới phải có tối thiểu 8 ký tự!")
            return
        }
        if (state.newPassword != state.confirmNewPassword) {
            uiState = state.copy(errorMessage = "Mật khẩu xác nhận không khớp!")
            return
        }

        uiState = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            delay(1500)
            uiState = uiState.copy(
                isLoading = false,
                currentStep = ForgotPasswordStep.SUCCESS,
                successMessage = "Mật khẩu của bạn đã được đặt lại thành công!"
            )
            onSuccess()
        }
    }

    // Quay lại bước trước
    fun goToPreviousStep() {
        when (uiState.currentStep) {
            ForgotPasswordStep.VERIFY_OTP -> {
                uiState = uiState.copy(currentStep = ForgotPasswordStep.REQUEST_CODE, errorMessage = null)
            }
            ForgotPasswordStep.RESET_PASSWORD -> {
                uiState = uiState.copy(currentStep = ForgotPasswordStep.VERIFY_OTP, errorMessage = null)
            }
            else -> {}
        }
    }
}
