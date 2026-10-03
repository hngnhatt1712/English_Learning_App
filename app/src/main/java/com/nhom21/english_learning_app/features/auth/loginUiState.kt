package com.nhom21.english_learning_app.features.auth

/**
 * UI State cho màn hình Login của ứng dụng Owla English.
 * Định nghĩa tối thiểu các trường cần thiết phục vụ dựng UI và Preview theo yêu cầu.
 */
data class LoginUiState(
    val identifier: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
