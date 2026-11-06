package com.nhom21.english_learning_app.features.auth

/**
 * Immutable UI state for the Signup screen (D-C12).
 *
 * Preserves all existing fields (`username`, `email`, `password`, `confirmPassword`,
 * `isPasswordVisible`, `isConfirmPasswordVisible`, `isTermsAccepted`,
 * `isLoading`, `errorMessage`) and adds field-level and general error fields (`usernameError`,
 * `emailError`, `passwordError`, `confirmPasswordError`, `generalError`).
 * `generalError` and legacy `errorMessage` are kept synchronised by [SignupViewModel] so existing
 * UI composables and Previews continue to work without layout changes.
 */
data class SignUpUiState(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val isTermsAccepted: Boolean = false,
    val isLoading: Boolean = false,
    val usernameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val generalError: String? = null,
    val errorMessage: String? = null
)

/** Type alias matching the canonical `SignupUiState` naming in specification F.2 / D-C12. */
typealias SignupUiState = SignUpUiState
