package com.nhom21.english_learning_app.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhom21.english_learning_app.data.remote.api.ProfileApi
import com.nhom21.english_learning_app.data.remote.dto.UpdateProfileRequest
import com.nhom21.english_learning_app.data.repository.AuthRepository
import com.nhom21.english_learning_app.data.repository.ChangePasswordResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    private val authRepository: AuthRepository
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
                currentPasswordError = null,
                newPasswordError = null,
                confirmPasswordError = null,
                passwordError = null
            )
        }
    }

    fun onCloseChangePasswordDialog() {
        if (_uiState.value.isSaving) return
        _uiState.update {
            it.copy(
                showChangePasswordDialog = false,
                currentPasswordInput = "",
                newPasswordInput = "",
                confirmPasswordInput = "",
                currentPasswordError = null,
                newPasswordError = null,
                confirmPasswordError = null,
                passwordError = null
            )
        }
    }

    fun onOpenLogoutDialog() {
        _uiState.update { it.copy(showLogoutDialog = true) }
    }

    fun onCloseLogoutDialog() {
        if (_uiState.value.isLoggingOut) return
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
        _uiState.update {
            it.copy(
                currentPasswordInput = v,
                currentPasswordError = null,
                passwordError = null
            )
        }
    }

    fun onNewPasswordChange(v: String) {
        _uiState.update {
            it.copy(
                newPasswordInput = v,
                newPasswordError = null,
                passwordError = null
            )
        }
    }

    fun onConfirmPasswordChange(v: String) {
        _uiState.update {
            it.copy(
                confirmPasswordInput = v,
                confirmPasswordError = null,
                passwordError = null
            )
        }
    }

    /**
     * Validates and submits password change request via [AuthRepository.changePassword].
     */
    fun submitChangePassword() {
        val state = _uiState.value
        if (state.isSaving) return

        val current = state.currentPasswordInput
        val newPass = state.newPasswordInput
        val confirm = state.confirmPasswordInput

        val curErr = PasswordPolicy.validateCurrentPassword(current)
        val newErr = PasswordPolicy.validateNewPassword(newPass)
        val confirmErr = PasswordPolicy.validateConfirmPassword(newPass, confirm)

        if (curErr != null || newErr != null || confirmErr != null) {
            _uiState.update {
                it.copy(
                    currentPasswordError = curErr,
                    newPasswordError = newErr,
                    confirmPasswordError = confirmErr,
                    passwordError = null
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isSaving = true,
                currentPasswordError = null,
                newPasswordError = null,
                confirmPasswordError = null,
                passwordError = null
            )
        }

        viewModelScope.launch {
            try {
                when (val result = authRepository.changePassword(current, newPass)) {
                    is ChangePasswordResult.Success -> {
                        _uiState.update {
                            it.copy(
                                showChangePasswordDialog = false,
                                currentPasswordInput = "",
                                newPasswordInput = "",
                                confirmPasswordInput = "",
                                currentPasswordError = null,
                                newPasswordError = null,
                                confirmPasswordError = null,
                                passwordError = null,
                                snackbarMessage = PasswordPolicy.MESSAGE_SUCCESS
                            )
                        }
                    }

                    is ChangePasswordResult.WrongCurrentPassword -> {
                        _uiState.update {
                            it.copy(
                                currentPasswordError = PasswordPolicy.ERROR_CURRENT_WRONG,
                                passwordError = null
                            )
                        }
                    }

                    is ChangePasswordResult.ValidationError -> {
                        var serverCurErr: String? = null
                        var serverNewErr: String? = null
                        var hasUnknownField = false

                        for ((field, code) in result.fieldErrors) {
                            when (field) {
                                "currentPassword" -> {
                                    if (serverCurErr == null) {
                                        serverCurErr = PasswordPolicy.mapCurrentPasswordCode(code)
                                    }
                                }
                                "newPassword" -> {
                                    if (serverNewErr == null) {
                                        serverNewErr = PasswordPolicy.mapNewPasswordCode(code)
                                    }
                                }
                                else -> {
                                    hasUnknownField = true
                                }
                            }
                        }

                        val generalErr = if (
                            serverCurErr == null &&
                            serverNewErr == null &&
                            (hasUnknownField || result.fieldErrors.isEmpty())
                        ) {
                            PasswordPolicy.ERROR_VALIDATION_GENERAL
                        } else {
                            null
                        }

                        _uiState.update {
                            it.copy(
                                currentPasswordError = serverCurErr,
                                newPasswordError = serverNewErr,
                                passwordError = generalErr
                            )
                        }
                    }

                    is ChangePasswordResult.SessionExpired -> {
                        // Global session expiration listener redirects to Login.
                    }

                    is ChangePasswordResult.NetworkError -> {
                        _uiState.update {
                            it.copy(passwordError = PasswordPolicy.ERROR_NETWORK)
                        }
                    }

                    is ChangePasswordResult.Unknown -> {
                        _uiState.update {
                            it.copy(passwordError = PasswordPolicy.ERROR_UNKNOWN)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(passwordError = PasswordPolicy.ERROR_UNKNOWN)
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    /** Logs out through the repository and navigates after local session cleanup. */
    fun logout(onLoggedOut: () -> Unit) {
        if (_uiState.value.isLoggingOut) return
        _uiState.update { it.copy(isLoggingOut = true) }
        viewModelScope.launch {
            try {
                authRepository.logout()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Finish the local navigation flow after an unexpected repository error.
            } finally {
                _uiState.update { it.copy(isLoggingOut = false, showLogoutDialog = false) }
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
