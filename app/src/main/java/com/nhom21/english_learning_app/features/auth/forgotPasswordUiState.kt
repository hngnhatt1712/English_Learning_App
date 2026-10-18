package com.nhom21.english_learning_app.features.auth

/**
 * Các bước trong luồng Quên / Đặt lại mật khẩu.
 */
enum class ForgotPasswordStep {
    REQUEST_CODE,     // Bước 1: Nhập Email/SĐT + CAPTCHA
    VERIFY_OTP,       // Bước 2: Nhập mã OTP xác nhận
    RESET_PASSWORD,   // Bước 3: Đặt lại mật khẩu mới
    SUCCESS           // Bước 4: Hoàn tất thành công
}

/**
 * Trạng thái UI cho màn hình Quên mật khẩu (ForgotPasswordScreen).
 */
data class ForgotPasswordUiState(
    val currentStep: ForgotPasswordStep = ForgotPasswordStep.REQUEST_CODE,
    val emailOrPhone: String = "",
    val captchaInput: String = "",
    val captchaCode: String = "739K",
    val otpInput: String = "",
    val newPassword: String = "",
    val confirmNewPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val resendTimerSeconds: Int = 60,
    val isResendEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
