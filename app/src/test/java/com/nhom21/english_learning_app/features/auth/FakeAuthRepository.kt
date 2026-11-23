package com.nhom21.english_learning_app.features.auth

import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiFailure
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.remote.dto.AuthResultDto
import com.nhom21.english_learning_app.data.remote.dto.ForgotPasswordResultDto
import com.nhom21.english_learning_app.data.remote.dto.UserDto
import com.nhom21.english_learning_app.data.remote.dto.VerifyOtpResultDto
import com.nhom21.english_learning_app.data.repository.AuthRepository
import com.nhom21.english_learning_app.data.repository.ChangePasswordResult

/**
 * Fake implementation of [AuthRepository] for unit testing [LoginViewModel].
 *
 * Allows tests to:
 *   1. Set the response that [login] will return via [loginResult].
 *   2. Assert on call counts and received arguments.
 *
 * Per #12: matches the real interface signature exactly; does NOT normalise arguments.
 */
class FakeAuthRepository : AuthRepository {

    /** The result returned by the next [login] call. Defaults to a network error. */
    var loginResult: ApiResult<AuthResultDto> = ApiResult.Failure(
        com.nhom21.english_learning_app.core.network.ApiFailure(
            type = com.nhom21.english_learning_app.core.network.ApiErrorType.NETWORK,
            message = "Not configured"
        )
    )

    /** Total number of times [login] has been called. */
    var loginCallCount: Int = 0
        private set

    /** The identifier argument of the most recent [login] call, or null if never called. */
    var lastLoginIdentifier: String? = null
        private set

    /** The password argument of the most recent [login] call, or null if never called. */
    var lastLoginPassword: String? = null
        private set

    override suspend fun login(identifier: String, password: String): ApiResult<AuthResultDto> {
        loginCallCount++
        lastLoginIdentifier = identifier
        lastLoginPassword = password
        return loginResult
    }

    // ── Unused interface methods — return network error as safe defaults ──────

    /** The result returned by the next [register] call. Defaults to a network error. */
    var registerResult: ApiResult<AuthResultDto> = ApiResult.Failure(
        com.nhom21.english_learning_app.core.network.ApiFailure(
            type = com.nhom21.english_learning_app.core.network.ApiErrorType.NETWORK,
            message = "Not configured"
        )
    )

    /** Total number of times [register] has been called. */
    var registerCallCount: Int = 0
        private set

    /** The username argument of the most recent [register] call, or null if never called. */
    var lastRegisterUsername: String? = null
        private set

    /** The email argument of the most recent [register] call, or null if never called. */
    var lastRegisterEmail: String? = null
        private set

    /** The password argument of the most recent [register] call, or null if never called. */
    var lastRegisterPassword: String? = null
        private set

    override suspend fun register(
        username: String,
        email: String,
        password: String
    ): ApiResult<AuthResultDto> {
        registerCallCount++
        lastRegisterUsername = username
        lastRegisterEmail = email
        lastRegisterPassword = password
        return registerResult
    }

    override suspend fun me(): ApiResult<UserDto> = ApiResult.Failure(
        com.nhom21.english_learning_app.core.network.ApiFailure(
            type = com.nhom21.english_learning_app.core.network.ApiErrorType.UNKNOWN,
            message = "Not implemented in fake"
        )
    )

    override suspend fun logout() = Unit

    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String
    ): ChangePasswordResult = ChangePasswordResult.NetworkError

    // ── Forgot / Reset Password support ─────────────────────────────────────

    var requestResetOtpResult: ApiResult<ForgotPasswordResultDto> =
        ApiResult.Success(ForgotPasswordResultDto(retryAfterSeconds = 60))
    var requestResetOtpAction: (suspend (String) -> ApiResult<ForgotPasswordResultDto>)? = null
    var requestResetOtpCallCount: Int = 0
        private set
    var lastRequestedEmail: String? = null
        private set

    override suspend fun requestResetOtp(email: String): ApiResult<ForgotPasswordResultDto> {
        requestResetOtpCallCount++
        lastRequestedEmail = email
        return requestResetOtpAction?.invoke(email) ?: requestResetOtpResult
    }

    var verifyResetOtpResult: ApiResult<VerifyOtpResultDto> =
        ApiResult.Success(VerifyOtpResultDto(resetToken = "test-reset-token-123456", expiresInSeconds = 600))
    var verifyResetOtpAction: (suspend (String, String) -> ApiResult<VerifyOtpResultDto>)? = null
    var verifyResetOtpCallCount: Int = 0
        private set
    var lastVerifyEmail: String? = null
        private set
    var lastVerifyOtp: String? = null
        private set

    override suspend fun verifyResetOtp(email: String, otp: String): ApiResult<VerifyOtpResultDto> {
        verifyResetOtpCallCount++
        lastVerifyEmail = email
        lastVerifyOtp = otp
        return verifyResetOtpAction?.invoke(email, otp) ?: verifyResetOtpResult
    }

    var resetPasswordResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var resetPasswordAction: (suspend (String, String) -> ApiResult<Unit>)? = null
    var resetPasswordCallCount: Int = 0
        private set
    var lastResetToken: String? = null
        private set
    var lastNewPassword: String? = null
        private set

    override suspend fun resetPassword(resetToken: String, newPassword: String): ApiResult<Unit> {
        resetPasswordCallCount++
        lastResetToken = resetToken
        lastNewPassword = newPassword
        return resetPasswordAction?.invoke(resetToken, newPassword) ?: resetPasswordResult
    }
}
