package com.nhom21.english_learning_app.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhom21.english_learning_app.data.repository.AuthRepository
import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Events emitted once by [LoginViewModel] to the UI layer. */
sealed interface LoginEvent {
    /** Signals a successful login. Navigation is handled by the receiver. */
    data object LoginSuccess : LoginEvent
}

/**
 * ViewModel for the Login screen.
 *
 * Owns [LoginUiState] and emits [LoginEvent] via a one-shot [Channel] to prevent
 * re-delivery after screen rotation.
 *
 * Constraints:
 * - D-B2: receives [AuthRepository] interface only; does not touch AuthApi or TokenManager.
 * - D-B3: trims identifier before sending; password is transmitted as-is.
 * - D-B4: ignores [onLoginClick] while [LoginUiState.isLoading] is true.
 * - D-B5: maps every [ApiResult.Failure] type to the correct UI string resource key.
 * - D-B9: clears field-level error + generalError on field value change.
 * - D-B10: does not call me() or navigate after success; token already saved by repository.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    // ── State ────────────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // ── Events ───────────────────────────────────────────────────────────────

    /** One-shot event channel; capacity = 1 so fast producers never block. */
    private val _eventChannel = Channel<LoginEvent>(capacity = Channel.BUFFERED)
    val events = _eventChannel.receiveAsFlow()

    // ── Public API ───────────────────────────────────────────────────────────

    /** Called when the identifier field value changes. Clears its own error and generalError (D-B9). */
    fun onIdentifierChange(value: String) {
        _uiState.update { it.copy(identifier = value, identifierError = null, generalError = null, errorMessage = null) }
    }

    /** Called when the password field value changes. Clears its own error and generalError (D-B9). */
    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, generalError = null, errorMessage = null) }
    }

    /** Toggles password visibility — kept consistent with existing SignUpViewModel pattern. */
    fun onTogglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    /**
     * Initiates the login flow.
     *
     * Step-by-step per F spec:
     * 1. Guard against double-tap (D-B4).
     * 2. Client-side validation (D-B3); abort with field errors if invalid.
     * 3. Set isLoading = true and clear stale errors.
     * 4. Call repository.login(identifier.trim(), password).
     * 5. Map ApiResult to state/event (D-B5).
     * 6. Always set isLoading = false.
     */
    fun onLoginClick() {
        val current = _uiState.value

        // Step 1: Guard — D-B4
        if (current.isLoading) return

        // Step 2: Client-side validation — D-B3
        val trimmedIdentifier = current.identifier.trim()
        var identifierErr: String? = null
        var passwordErr: String? = null

        // Contract 7.2: identifier must not be blank; max 254 chars
        if (trimmedIdentifier.isEmpty()) {
            identifierErr = ERROR_IDENTIFIER_REQUIRED
        } else if (trimmedIdentifier.length > MAX_IDENTIFIER_LENGTH) {
            identifierErr = ERROR_IDENTIFIER_TOO_LONG
        }

        // Contract 4.4 / backend auth.py: password min_length=1 at login (no strength rules at login)
        if (current.password.isEmpty()) {
            passwordErr = ERROR_PASSWORD_REQUIRED
        }

        if (identifierErr != null || passwordErr != null) {
            _uiState.update {
                it.copy(
                    identifierError = identifierErr,
                    passwordError = passwordErr,
                    errorMessage = identifierErr ?: passwordErr
                )
            }
            return
        }

        // Step 3: Start loading, clear stale errors
        _uiState.update {
            it.copy(
                isLoading = true,
                identifierError = null,
                passwordError = null,
                generalError = null,
                errorMessage = null
            )
        }

        // Step 4: Call repository
        viewModelScope.launch {
            val result = authRepository.login(
                identifier = trimmedIdentifier,
                password = current.password   // D-B3: password NOT trimmed
            )

            // Step 5 & 6: Map result, always clear loading
            when (result) {
                is ApiResult.Success -> {
                    // D-B10: token already saved by AuthRepositoryImpl; do not call me()
                    _uiState.update { it.copy(isLoading = false) }
                    _eventChannel.send(LoginEvent.LoginSuccess)
                }

                is ApiResult.Failure -> {
                    val failure = result.error
                    when (failure.type) {
                        // Contract 3.3, mục 401 invalid-credentials:
                        // Show generic message — do NOT reveal whether identifier or password is wrong
                        // A1: must NOT delete session / navigate
                        ApiErrorType.INVALID_CREDENTIALS -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    generalError = ERROR_INVALID_CREDENTIALS,
                                    errorMessage = ERROR_INVALID_CREDENTIALS
                                )
                            }
                        }

                        // Contract 3.3, mục 422 validation-error:
                        // Attach errors to specific fields by errors[].field value
                        ApiErrorType.VALIDATION_ERROR -> {
                            var idErr: String? = null
                            var pwErr: String? = null
                            var generalErr: String? = null

                            for (fe in failure.fieldErrors) {
                                when (fe.field) {
                                    "identifier" -> idErr = fe.message.ifBlank { ERROR_INVALID_CREDENTIALS }
                                    "password" -> pwErr = fe.message.ifBlank { ERROR_INVALID_CREDENTIALS }
                                    else -> generalErr = ERROR_GENERAL
                                }
                            }
                            // If no fieldErrors were provided but type is validation-error, show general
                            if (failure.fieldErrors.isEmpty()) generalErr = ERROR_GENERAL

                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    identifierError = idErr,
                                    passwordError = pwErr,
                                    generalError = generalErr,
                                    errorMessage = generalErr ?: idErr ?: pwErr
                                )
                            }
                        }

                        // Network error
                        ApiErrorType.NETWORK -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    generalError = ERROR_NETWORK,
                                    errorMessage = ERROR_NETWORK
                                )
                            }
                        }

                        // All other types: UNKNOWN, UNAUTHORIZED (shouldn't appear at login),
                        // INTERNAL_ERROR, etc. — show generic retry message
                        else -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    generalError = ERROR_GENERAL,
                                    errorMessage = ERROR_GENERAL
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── String resource keys (resolved to string values; strings.xml holds the actual text) ──
    // These are the raw display strings matching the Vietnamese UI language detected in E4(e).
    // They are defined here as constants to be used in both ViewModel and tests.
    // In a full implementation these would be resolved via Context / StringProvider;
    // for this MVP sprint they are hardcoded strings that mirror strings.xml values exactly.

    companion object {
        /** Contract 7.2: identifier max length from backend auth.py LoginRequest max_length=254 */
        const val MAX_IDENTIFIER_LENGTH = 254

        // Display strings — must match strings.xml values (D-B5, D-B7, UI is Vietnamese per E4-e)
        const val ERROR_IDENTIFIER_REQUIRED = "Vui lòng nhập email hoặc tên đăng nhập"
        const val ERROR_IDENTIFIER_TOO_LONG = "Email hoặc tên đăng nhập quá dài (tối đa 254 ký tự)"
        const val ERROR_PASSWORD_REQUIRED = "Vui lòng nhập mật khẩu"

        /** Contract 3.3 row 401 invalid-credentials: "Hiện 'Tài khoản hoặc mật khẩu không đúng'" */
        const val ERROR_INVALID_CREDENTIALS = "Tài khoản hoặc mật khẩu không đúng"

        /** Contract 3.3: NETWORK synthetic type */
        const val ERROR_NETWORK = "Không kết nối được máy chủ, vui lòng thử lại"

        /** Fallback for UNKNOWN / server errors / unrecognised field in validation-error */
        const val ERROR_GENERAL = "Đã xảy ra lỗi, vui lòng thử lại sau"
    }
}
