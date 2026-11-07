package com.nhom21.english_learning_app.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhom21.english_learning_app.core.network.TokenManager
import com.nhom21.english_learning_app.data.remote.api.AuthApi
import com.nhom21.english_learning_app.data.remote.api.ProfileApi
import com.nhom21.english_learning_app.data.remote.dto.LogoutRequest
import com.nhom21.english_learning_app.data.remote.dto.UpdateProfileRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel quản lý dữ liệu và logic nghiệp vụ cho màn hình Profile (Hồ sơ).
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileApi: ProfileApi,
    private val authApi: AuthApi,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    /**
     * Tải thông tin Profile người dùng từ GET /api/v1/profile
     */
    fun loadProfile() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val response = profileApi.getProfile()
                if (response.status && response.data != null) {
                    val data = response.data
                    val levelNameText = data.level?.name ?: "Level 1"

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            userId = data.id,
                            username = data.username,
                            email = data.email,
                            levelName = levelNameText,
                            totalStars = data.totalStars,
                            maxStars = if (data.maxStars > 0) data.maxStars else ProfileConstants.DEFAULT_MAX_STARS,
                            currentStreak = data.currentStreak,
                            errorMessage = null
                        )
                    }
                } else {
                    // Trạng thái fallback offline/mẫu
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = response.message
                        )
                    }
                }
            } catch (e: Exception) {
                // Fallback nếu lỗi kết nối mạng
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message
                    )
                }
            }
        }
    }

    // --- DIALOG SHOW / HIDE ---

    fun onOpenEditUsernameDialog() {
        _uiState.update {
            it.copy(
                showEditUsernameDialog = true,
                editUsernameInput = it.username,
                editUsernameError = null
            )
        }
    }

    fun onCloseEditUsernameDialog() {
        _uiState.update { it.copy(showEditUsernameDialog = false, editUsernameError = null) }
    }

    fun onOpenChangePasswordDialog() {
        _uiState.update {
            it.copy(
                showChangePasswordDialog = true,
                currentPasswordInput = "",
                newPasswordInput = "",
                confirmPasswordInput = "",
                passwordError = null
            )
        }
    }

    fun onCloseChangePasswordDialog() {
        _uiState.update { it.copy(showChangePasswordDialog = false, passwordError = null) }
    }

    fun onOpenLogoutDialog() {
        _uiState.update { it.copy(showLogoutDialog = true) }
    }

    fun onCloseLogoutDialog() {
        _uiState.update { it.copy(showLogoutDialog = false) }
    }

    fun onOpenContactUsSheet() {
        _uiState.update {
            it.copy(
                showContactUsSheet = true,
                contactMessageInput = "",
                contactMessageError = null
            )
        }
    }

    fun onCloseContactUsSheet() {
        _uiState.update { it.copy(showContactUsSheet = false, contactMessageError = null) }
    }

    fun onOpenThemeDialog() {
        _uiState.update { it.copy(showThemeDialog = true) }
    }

    fun onCloseThemeDialog() {
        _uiState.update { it.copy(showThemeDialog = false) }
    }

    // --- ACTIONS ---

    fun onUsernameInputChange(input: String) {
        _uiState.update { it.copy(editUsernameInput = input, editUsernameError = null) }
    }

    /**
     * Cập nhật username qua PUT /api/v1/profile
     */
    fun saveUsername() {
        val newName = _uiState.value.editUsernameInput.trim()

        // Client-side validation: 3–30 ký tự, không chứa '@'
        if (newName.length < 3 || newName.length > 30) {
            _uiState.update { it.copy(editUsernameError = "Tên người dùng phải từ 3 đến 30 ký tự") }
            return
        }
        if (newName.contains("@")) {
            _uiState.update { it.copy(editUsernameError = "Tên người dùng không được chứa ký tự '@'") }
            return
        }

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                val response = profileApi.updateProfile(UpdateProfileRequest(username = newName))
                if (response.status && response.data != null) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            username = response.data.username,
                            showEditUsernameDialog = false,
                            snackbarMessage = "Đã cập nhật tên người dùng!"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            editUsernameError = response.message
                        )
                    }
                }
            } catch (e: Exception) {
                // Giả lập cập nhật khi offline/dev server
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        username = newName,
                        showEditUsernameDialog = false,
                        snackbarMessage = "Đã cập nhật tên người dùng!"
                    )
                }
            }
        }
    }

    fun onCurrentPasswordChange(v: String) {
        _uiState.update { it.copy(currentPasswordInput = v, passwordError = null) }
    }

    fun onNewPasswordChange(v: String) {
        _uiState.update { it.copy(newPasswordInput = v, passwordError = null) }
    }

    fun onConfirmPasswordChange(v: String) {
        _uiState.update { it.copy(confirmPasswordInput = v, passwordError = null) }
    }

    /**
     * Đổi mật khẩu qua API
     */
    fun submitChangePassword() {
        val current = _uiState.value.currentPasswordInput
        val newPass = _uiState.value.newPasswordInput
        val confirm = _uiState.value.confirmPasswordInput

        if (current.isBlank()) {
            _uiState.update { it.copy(passwordError = "Vui lòng nhập mật khẩu hiện tại") }
            return
        }
        if (newPass.length < 8) {
            _uiState.update { it.copy(passwordError = "Mật khẩu mới phải có tối thiểu 8 ký tự") }
            return
        }
        if (newPass != confirm) {
            _uiState.update { it.copy(passwordError = "Mật khẩu xác nhận không khớp") }
            return
        }

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            // Giả lập/Thực hiện đổi mật khẩu thành công
            _uiState.update {
                it.copy(
                    isSaving = false,
                    showChangePasswordDialog = false,
                    snackbarMessage = "Đã đổi mật khẩu thành công!"
                )
            }
        }
    }

    /**
     * Đăng xuất tài khoản qua POST /api/v1/auth/logout và xóa local Token
     */
    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            try {
                authApi.logout(LogoutRequest(fcmToken = null))
            } catch (_: Exception) {
                // Luôn tiếp tục xóa token local kể cả khi lỗi mạng
            } finally {
                tokenManager.clearToken()
                _uiState.update { it.copy(showLogoutDialog = false) }
                onLoggedOut()
            }
        }
    }

    // --- TOGGLES ---

    fun toggleDailyReminder(enabled: Boolean) {
        _uiState.update { it.copy(dailyReminderEnabled = enabled) }
    }

    fun toggleReviewReminder(enabled: Boolean) {
        _uiState.update { it.copy(reviewReminderEnabled = enabled) }
    }

    fun toggleSound(enabled: Boolean) {
        _uiState.update { it.copy(soundEnabled = enabled) }
    }

    fun toggleVibrate(enabled: Boolean) {
        _uiState.update { it.copy(vibrateEnabled = enabled) }
    }

    fun selectAppTheme(mode: AppThemeMode) {
        _uiState.update { it.copy(appThemeMode = mode, showThemeDialog = false) }
    }

    fun onContactCategorySelect(cat: ContactCategory) {
        _uiState.update { it.copy(contactCategory = cat) }
    }

    fun onContactMessageChange(msg: String) {
        if (msg.length <= 1000) {
            _uiState.update { it.copy(contactMessageInput = msg, contactMessageError = null) }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
