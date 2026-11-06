package com.nhom21.english_learning_app.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One-shot events emitted by [SignupViewModel] to the UI layer (F.2 / D-C5). */
sealed interface SignupEvent {
    /** Emitted once when registration succeeds and the access token has been persisted. */
    data object SignupSuccess : SignupEvent

    /** Optional one-shot message event (D-C9). */
    data class ShowMessage(val message: String) : SignupEvent
}

/**
 * ViewModel for the Signup screen (F.3, D-C2..D-C13).
 *
 * - Receives [AuthRepository] interface via constructor injection (D-C13).
 * - Trims `username` and `email` without lowercasing; preserves `password` verbatim (D-C3).
 * - Validates all 4 fields simultaneously in fixed order (`username` -> `email` -> `password` -> `confirmPassword`)
 *   before making any network request (D-C4).
 * - Ignores duplicate clicks while [SignupUiState.isLoading] is `true` (D-C10).
 * - Clears field-specific errors and `generalError` on field edits; editing `password` or `confirmPassword`
 *   clears `confirmPasswordError` (D-C11).
 * - Maps [ApiResult] per D-C7 without exposing raw server `detail` messages.
 */
@HiltViewModel
class SignupViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignupUiState())
    val uiState: StateFlow<SignupUiState> = _uiState.asStateFlow()

    private val _eventChannel = Channel<SignupEvent>(capacity = Channel.BUFFERED)
    val events: Flow<SignupEvent> = _eventChannel.receiveAsFlow()

    fun onUsernameChange(value: String) {
        _uiState.update {
            it.copy(
                username = value,
                usernameError = null,
                generalError = null,
                errorMessage = null
            )
        }
    }

    fun onEmailChange(value: String) {
        _uiState.update {
            it.copy(
                email = value,
                emailError = null,
                generalError = null,
                errorMessage = null
            )
        }
    }

    fun onPasswordChange(value: String) {
        _uiState.update {
            it.copy(
                password = value,
                passwordError = null,
                confirmPasswordError = null,
                generalError = null,
                errorMessage = null
            )
        }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update {
            it.copy(
                confirmPassword = value,
                confirmPasswordError = null,
                generalError = null,
                errorMessage = null
            )
        }
    }

    fun onTogglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun onToggleConfirmPasswordVisibility() {
        _uiState.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }
    }

    fun onLevelSelect(level: String) {
        _uiState.update {
            it.copy(
                selectedLevel = level,
                generalError = null,
                errorMessage = null
            )
        }
    }

    fun onTermsCheckedChange(isChecked: Boolean) {
        _uiState.update {
            it.copy(
                isTermsAccepted = isChecked,
                generalError = null,
                errorMessage = null
            )
        }
    }

    fun onSignupClick() {
        val current = _uiState.value

        // Step 1: Prevent double-tap while loading (D-C10)
        if (current.isLoading) return

        // Step 2: Client-side validation of all fields in order (D-C3, D-C4)
        val trimmedUsername = current.username.trim()
        val trimmedEmail = current.email.trim()
        val rawPassword = current.password
        val rawConfirm = current.confirmPassword

        val usernameErr: String? = when {
            trimmedUsername.isEmpty() -> ERROR_USERNAME_REQUIRED
            trimmedUsername.length !in USERNAME_MIN..USERNAME_MAX -> ERROR_USERNAME_INVALID
            trimmedUsername.contains('@') -> ERROR_USERNAME_INVALID
            !USERNAME_REGEX.matches(trimmedUsername) -> ERROR_USERNAME_INVALID
            else -> null
        }

        val emailErr: String? = when {
            trimmedEmail.isEmpty() -> ERROR_EMAIL_REQUIRED
            trimmedEmail.length > MAX_EMAIL_LENGTH -> ERROR_EMAIL_INVALID
            !EMAIL_LOOSE_REGEX.matches(trimmedEmail) -> ERROR_EMAIL_INVALID
            else -> null
        }

        val passwordErr: String? = when {
            rawPassword.isEmpty() -> ERROR_PASSWORD_REQUIRED
            rawPassword.length !in PASSWORD_MIN..PASSWORD_MAX -> ERROR_PASSWORD_LENGTH
            !HAS_ASCII_LETTER_REGEX.containsMatchIn(rawPassword) ||
                !HAS_ASCII_DIGIT_REGEX.containsMatchIn(rawPassword) -> ERROR_PASSWORD_COMPLEXITY
            else -> null
        }

        val confirmErr: String? = when {
            rawConfirm.isEmpty() -> ERROR_CONFIRM_REQUIRED
            rawConfirm != rawPassword -> ERROR_CONFIRM_MISMATCH
            else -> null
        }

        if (usernameErr != null || emailErr != null || passwordErr != null || confirmErr != null) {
            _uiState.update {
                it.copy(
                    usernameError = usernameErr,
                    emailError = emailErr,
                    passwordError = passwordErr,
                    confirmPasswordError = confirmErr,
                    generalError = null,
                    errorMessage = null
                )
            }
            return
        }

        // Step 3: Set loading state and clear previous errors
        _uiState.update {
            it.copy(
                isLoading = true,
                usernameError = null,
                emailError = null,
                passwordError = null,
                confirmPasswordError = null,
                generalError = null,
                errorMessage = null
            )
        }

        // Step 4: Execute registration via repository
        viewModelScope.launch {
            val result = authRepository.register(
                username = trimmedUsername,
                email = trimmedEmail,
                password = rawPassword
            )

            // Step 5 & 6: Map result and always reset isLoading = false (D-C7)
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _eventChannel.send(SignupEvent.SignupSuccess)
                }

                is ApiResult.Failure -> {
                    val failure = result.error
                    when (failure.type) {
                        ApiErrorType.EMAIL_TAKEN -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    emailError = ERROR_EMAIL_TAKEN
                                )
                            }
                        }

                        ApiErrorType.USERNAME_TAKEN -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    usernameError = ERROR_USERNAME_TAKEN
                                )
                            }
                        }

                        ApiErrorType.VALIDATION_ERROR -> {
                            var uErr: String? = null
                            var eErr: String? = null
                            var pErr: String? = null
                            var genErr: String? = null

                            for (fe in failure.fieldErrors) {
                                when (fe.field) {
                                    "username" -> uErr = ERROR_USERNAME_INVALID
                                    "email" -> eErr = ERROR_EMAIL_INVALID
                                    "password" -> pErr = ERROR_PASSWORD_COMPLEXITY
                                    else -> genErr = ERROR_VALIDATION
                                }
                            }
                            if (failure.fieldErrors.isEmpty()) {
                                genErr = ERROR_VALIDATION
                            }

                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    usernameError = uErr,
                                    emailError = eErr,
                                    passwordError = pErr,
                                    generalError = genErr,
                                    errorMessage = genErr
                                )
                            }
                        }

                        ApiErrorType.NETWORK -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    generalError = ERROR_NETWORK,
                                    errorMessage = ERROR_NETWORK
                                )
                            }
                        }

                        else -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    generalError = ERROR_SERVER,
                                    errorMessage = ERROR_SERVER
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** Matches backend/app/schemas/types.py USERNAME_MIN = 3, USERNAME_MAX = 30. */
        const val USERNAME_MIN = 3
        const val USERNAME_MAX = 30
        const val MAX_EMAIL_LENGTH = 254
        const val PASSWORD_MIN = 8
        const val PASSWORD_MAX = 64

        /** Matches backend/app/schemas/types.py USERNAME_PATTERN = r"^[A-Za-z0-9_.]+$". */
        private val USERNAME_REGEX = Regex("^[A-Za-z0-9_.]+$")

        /** Loose client-side email check per D-C4: no whitespace, no `<` or `>`, single `@`. */
        private val EMAIL_LOOSE_REGEX = Regex("^[^@\\s<>]+@[^@\\s<>]+$")

        /** ASCII letter & digit checks per D-C4 / backend/app/schemas/auth.py. */
        private val HAS_ASCII_LETTER_REGEX = Regex("[A-Za-z]")
        private val HAS_ASCII_DIGIT_REGEX = Regex("[0-9]")

        // Vietnamese display strings matching strings.xml (D-C6)
        const val ERROR_USERNAME_REQUIRED = "Vui lòng nhập tên đăng nhập"
        const val ERROR_USERNAME_INVALID =
            "Tên đăng nhập gồm 3–30 ký tự: chữ cái, số, dấu chấm hoặc gạch dưới"
        const val ERROR_USERNAME_TAKEN = "Tên đăng nhập đã được sử dụng"

        const val ERROR_EMAIL_REQUIRED = "Vui lòng nhập email"
        const val ERROR_EMAIL_INVALID = "Email không hợp lệ"
        const val ERROR_EMAIL_TAKEN = "Email đã được đăng ký"

        const val ERROR_PASSWORD_REQUIRED = "Vui lòng nhập mật khẩu"
        const val ERROR_PASSWORD_LENGTH = "Mật khẩu phải từ 8 đến 64 ký tự"
        const val ERROR_PASSWORD_COMPLEXITY =
            "Mật khẩu cần có ít nhất một chữ cái và một chữ số"

        const val ERROR_CONFIRM_REQUIRED = "Vui lòng nhập lại mật khẩu"
        const val ERROR_CONFIRM_MISMATCH = "Mật khẩu nhập lại không khớp"

        // Reused from Login error strings (D-C6)
        const val ERROR_NETWORK = "Không kết nối được máy chủ, vui lòng thử lại"
        const val ERROR_SERVER = "Đã xảy ra lỗi, vui lòng thử lại sau"
        const val ERROR_VALIDATION = "Đã xảy ra lỗi, vui lòng thử lại sau"
    }
}

/** Type alias preserving compatibility with existing `SignUpViewModel` references. */
typealias SignUpViewModel = SignupViewModel
