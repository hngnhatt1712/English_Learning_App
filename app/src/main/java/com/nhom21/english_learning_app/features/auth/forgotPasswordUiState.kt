package com.nhom21.english_learning_app.features.auth

/**
 * Các bước trong luồng Quên / Đặt lại mật khẩu.
 */
enum class ForgotPasswordStep {
    REQUEST_CODE,     // Bước 1: Nhập Email + CAPTCHA
    VERIFY_OTP,       // Bước 2: Nhập mã OTP xác nhận
    RESET_PASSWORD,   // Bước 3: Đặt lại mật khẩu mới
    SUCCESS           // Bước 4: Hoàn tất thành công
}

/**
 * Trạng thái UI cho màn hình Quên mật khẩu (ForgotPasswordScreen).
 *
 * Bảo mật (A-04):
 * - Không chứa `resetToken` trong `UiState` (chỉ lưu nội bộ trong ViewModel).
 * - Ghi đè `toString()` để ẩn các trường nhạy cảm (`emailOrPhone`, `otpInput`, `newPassword`, `confirmNewPassword`).
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
    val resendCooldownSeconds: Int = 0,
    val resendTimerSeconds: Int = resendCooldownSeconds,
    val isResendEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val emailError: String? = null,
    val captchaError: String? = null,
    val otpError: String? = null,
    val newPasswordError: String? = null,
    val confirmPasswordError: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
) {
    override fun toString(): String {
        return "ForgotPasswordUiState(" +
            "currentStep=$currentStep, " +
            "emailOrPhone=REDACTED, " +
            "captchaInput=$captchaInput, " +
            "captchaCode=$captchaCode, " +
            "otpInput=REDACTED, " +
            "newPassword=REDACTED, " +
            "confirmNewPassword=REDACTED, " +
            "isPasswordVisible=$isPasswordVisible, " +
            "isConfirmPasswordVisible=$isConfirmPasswordVisible, " +
            "resendCooldownSeconds=$resendCooldownSeconds, " +
            "resendTimerSeconds=$resendTimerSeconds, " +
            "isResendEnabled=$isResendEnabled, " +
            "isLoading=$isLoading, " +
            "emailError=$emailError, " +
            "captchaError=$captchaError, " +
            "otpError=$otpError, " +
            "newPasswordError=$newPasswordError, " +
            "confirmPasswordError=$confirmPasswordError, " +
            "errorMessage=$errorMessage, " +
            "successMessage=$successMessage" +
            ")"
    }
}
