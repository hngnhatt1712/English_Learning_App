package com.nhom21.english_learning_app.features.auth

/**
 * Immutable UI state for the Login screen.
 *
 * Existing fields (identifier, password, isPasswordVisible, isLoading) are preserved unchanged.
 * Added per D-B8: identifierError, passwordError, generalError.
 * Note: errorMessage is kept for backward compatibility with existing UI composables that
 * already bind to it; generalError mirrors to errorMessage via the ViewModel.
 */
data class LoginUiState(
    val identifier: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    // Field-level errors per D-B5 / contract section 3.2 errors[].field
    val identifierError: String? = null,
    val passwordError: String? = null,
    // General (non-field) error message — displayed in the AnimatedVisibility block in LoginScreen
    val generalError: String? = null,
    // Legacy field kept so the existing AnimatedVisibility(visible = uiState.errorMessage != null)
    // composable continues to work without layout changes.
    val errorMessage: String? = null
)
