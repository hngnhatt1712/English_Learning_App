package com.nhom21.english_learning_app.features.auth

/**
 * UI State cho màn hình Đăng ký (SignUpScreen) của ứng dụng Owla English.
 */
data class SignUpUiState(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val selectedLevel: String = "",
    val isTermsAccepted: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
