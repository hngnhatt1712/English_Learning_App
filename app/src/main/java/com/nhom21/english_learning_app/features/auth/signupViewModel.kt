package com.nhom21.english_learning_app.features.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel quản lý logic và trạng thái UI cho màn hình Đăng ký (SignUpScreen).
 * Tích hợp Hilt Injection và gọi AuthRepository xử lý đăng ký tài khoản.
 */
@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    var uiState by mutableStateOf(SignUpUiState())
        private set

    fun onUsernameChange(newValue: String) {
        uiState = uiState.copy(username = newValue, errorMessage = null)
    }

    fun onEmailChange(newValue: String) {
        uiState = uiState.copy(email = newValue, errorMessage = null)
    }

    fun onPasswordChange(newValue: String) {
        uiState = uiState.copy(password = newValue, errorMessage = null)
    }

    fun onConfirmPasswordChange(newValue: String) {
        uiState = uiState.copy(confirmPassword = newValue, errorMessage = null)
    }

    fun onTogglePasswordVisibility() {
        uiState = uiState.copy(isPasswordVisible = !uiState.isPasswordVisible)
    }

    fun onToggleConfirmPasswordVisibility() {
        uiState = uiState.copy(isConfirmPasswordVisible = !uiState.isConfirmPasswordVisible)
    }

    fun onTermsCheckedChange(isChecked: Boolean) {
        uiState = uiState.copy(isTermsAccepted = isChecked, errorMessage = null)
    }

    fun register(onSuccess: () -> Unit) {
        val state = uiState

        if (state.isLoading) return

        if (state.username.isBlank() || state.email.isBlank() || state.password.isBlank() || state.confirmPassword.isBlank()) {
            uiState = state.copy(errorMessage = "Vui lòng điền đầy đủ tất cả các trường!")
            return
        }
        if (!state.email.trim().lowercase().endsWith("@gmail.com")) {
            uiState = state.copy(errorMessage = "Email phải có đuôi @gmail.com!")
            return
        }
        if (state.password != state.confirmPassword) {
            uiState = state.copy(errorMessage = "Mật khẩu xác nhận không khớp!")
            return
        }
        if (!state.isTermsAccepted) {
            uiState = state.copy(errorMessage = "Bạn cần đồng ý với điều khoản sử dụng!")
            return
        }

        uiState = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = authRepository.register(
                username = state.username.trim(),
                email = state.email.trim(),
                password = state.password
            )

            when (result) {
                is ApiResult.Success -> {
                    uiState = uiState.copy(isLoading = false)
                    onSuccess()
                }

                is ApiResult.Failure -> {
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = result.error.message.ifBlank { "Đăng ký thất bại. Vui lòng thử lại sau!" }
                    )
                }
            }
        }
    }
}
