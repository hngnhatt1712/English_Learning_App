package com.nhom21.english_learning_app.data.repository

import com.nhom21.english_learning_app.core.network.ApiErrorParser
import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiFailure
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.core.network.TokenManager
import com.nhom21.english_learning_app.data.remote.api.AuthApi
import com.nhom21.english_learning_app.data.remote.dto.AuthResultDto
import com.nhom21.english_learning_app.data.remote.dto.ChangePasswordRequest
import com.nhom21.english_learning_app.data.remote.dto.ForgotPasswordRequest
import com.nhom21.english_learning_app.data.remote.dto.ForgotPasswordResultDto
import com.nhom21.english_learning_app.data.remote.dto.LoginRequest
import com.nhom21.english_learning_app.data.remote.dto.LogoutRequest
import com.nhom21.english_learning_app.data.remote.dto.RegisterRequest
import com.nhom21.english_learning_app.data.remote.dto.ResetPasswordRequest
import com.nhom21.english_learning_app.data.remote.dto.UserDto
import com.nhom21.english_learning_app.data.remote.dto.VerifyOtpRequest
import com.nhom21.english_learning_app.data.remote.dto.VerifyOtpResultDto
import kotlinx.serialization.SerializationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of PUT /api/v1/auth/change-password per contract sections 3.3, 4.1, and 7.2.
 */
sealed interface ChangePasswordResult {
    data object Success : ChangePasswordResult
    data object WrongCurrentPassword : ChangePasswordResult
    data class ValidationError(val fieldErrors: Map<String, String>) : ChangePasswordResult
    data object SessionExpired : ChangePasswordResult
    data object NetworkError : ChangePasswordResult
    data class Unknown(val message: String?) : ChangePasswordResult
}

/**
 * Authentication repository interface per decisions D-A11 to D-A13 and D-C1.
 */
interface AuthRepository {
    /**
     * Registers a new user account and persists the returned access token on success (D-C1).
     */
    suspend fun register(username: String, email: String, password: String): ApiResult<AuthResultDto>
    suspend fun login(identifier: String, password: String): ApiResult<AuthResultDto>
    suspend fun me(): ApiResult<UserDto>
    suspend fun logout()
    suspend fun changePassword(currentPassword: String, newPassword: String): ChangePasswordResult
    suspend fun requestResetOtp(email: String): ApiResult<ForgotPasswordResultDto>
    suspend fun verifyResetOtp(email: String, otp: String): ApiResult<VerifyOtpResultDto>
    suspend fun resetPassword(resetToken: String, newPassword: String): ApiResult<Unit>
}

/**
 * Implementation of [AuthRepository] coordinating network calls, token persistence, and error mapping.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val tokenManager: TokenManager,
    private val errorParser: ApiErrorParser
) : AuthRepository {

    private companion object {
        private const val LOGOUT_TIMEOUT_MS = 5_000L
    }

    override suspend fun register(username: String, email: String, password: String): ApiResult<AuthResultDto> {
        return try {
            val response = authApi.register(RegisterRequest(username = username, email = email, password = password))
            val data = response.data
            if (response.status && data != null) {
                // Decision D-C1: Persist token on register success before returning (signup = auto-login)
                tokenManager.saveToken(data.accessToken)
                ApiResult.Success(data)
            } else {
                ApiResult.Failure(
                    ApiFailure(
                        type = ApiErrorType.UNKNOWN,
                        message = response.message,
                        httpStatus = 201
                    )
                )
            }
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string()
            ApiResult.Failure(errorParser.parse(body, e.code()))
        } catch (e: IOException) {
            ApiResult.Failure(
                ApiFailure(
                    type = ApiErrorType.NETWORK,
                    message = e.message ?: "Network error",
                    httpStatus = null
                )
            )
        } catch (e: SerializationException) {
            ApiResult.Failure(
                ApiFailure(
                    type = ApiErrorType.UNKNOWN,
                    message = e.message ?: "Serialization error",
                    httpStatus = null
                )
            )
        }
    }

    override suspend fun login(identifier: String, password: String): ApiResult<AuthResultDto> {
        return try {
            val response = authApi.login(LoginRequest(identifier = identifier, password = password))
            val data = response.data
            if (response.status && data != null) {
                // Decision D-A11: Persist token on login success before returning
                tokenManager.saveToken(data.accessToken)
                ApiResult.Success(data)
            } else {
                ApiResult.Failure(
                    ApiFailure(
                        type = ApiErrorType.UNKNOWN,
                        message = response.message,
                        httpStatus = 200
                    )
                )
            }
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string()
            ApiResult.Failure(errorParser.parse(body, e.code()))
        } catch (e: IOException) {
            ApiResult.Failure(
                ApiFailure(
                    type = ApiErrorType.NETWORK,
                    message = e.message ?: "Network error",
                    httpStatus = null
                )
            )
        } catch (e: SerializationException) {
            ApiResult.Failure(
                ApiFailure(
                    type = ApiErrorType.UNKNOWN,
                    message = e.message ?: "Serialization error",
                    httpStatus = null
                )
            )
        }
    }

    override suspend fun me(): ApiResult<UserDto> {
        return try {
            val response = authApi.me()
            val data = response.data
            if (response.status && data != null) {
                ApiResult.Success(data)
            } else {
                ApiResult.Failure(
                    ApiFailure(
                        type = ApiErrorType.UNKNOWN,
                        message = response.message,
                        httpStatus = 200
                    )
                )
            }
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string()
            ApiResult.Failure(errorParser.parse(body, e.code()))
        } catch (e: IOException) {
            ApiResult.Failure(
                ApiFailure(
                    type = ApiErrorType.NETWORK,
                    message = e.message ?: "Network error",
                    httpStatus = null
                )
            )
        } catch (e: SerializationException) {
            ApiResult.Failure(
                ApiFailure(
                    type = ApiErrorType.UNKNOWN,
                    message = e.message ?: "Serialization error",
                    httpStatus = null
                )
            )
        }
    }

    /**
     * Performs a best-effort server logout and always clears the local access token.
     * After timeout, the request may be cancelled before the server removes the FCM token;
     * this is acceptable under the API contract.
     */
    override suspend fun logout() {
        try {
            withTimeoutOrNull(LOGOUT_TIMEOUT_MS) {
                authApi.logout(LogoutRequest(fcmToken = null))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Server logout is best-effort; local logout must still complete.
        } finally {
            withContext(NonCancellable) {
                tokenManager.clearToken()
            }
        }
    }

    /**
     * Changes the current user's password and persists the newly issued access token before returning [ChangePasswordResult.Success].
     */
    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String
    ): ChangePasswordResult {
        return try {
            val response = authApi.changePassword(
                ChangePasswordRequest(
                    currentPassword = currentPassword,
                    newPassword = newPassword
                )
            )
            val data = response.data
            if (response.status && data != null && data.accessToken.isNotBlank()) {
                tokenManager.saveToken(data.accessToken)
                ChangePasswordResult.Success
            } else {
                ChangePasswordResult.Unknown(response.message.takeIf { it.isNotBlank() })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string()
            val failure = errorParser.parse(body, e.code())
            when {
                e.code() == 400 && failure.type == ApiErrorType.WRONG_PASSWORD -> {
                    ChangePasswordResult.WrongCurrentPassword
                }
                e.code() == 422 || failure.type == ApiErrorType.VALIDATION_ERROR -> {
                    val map = LinkedHashMap<String, String>()
                    for (fe in failure.fieldErrors) {
                        if (!map.containsKey(fe.field)) {
                            map[fe.field] = fe.code ?: ""
                        }
                    }
                    ChangePasswordResult.ValidationError(map)
                }
                e.code() == 401 && failure.type == ApiErrorType.UNAUTHORIZED -> {
                    ChangePasswordResult.SessionExpired
                }
                else -> {
                    ChangePasswordResult.Unknown(failure.message)
                }
            }
        } catch (_: IOException) {
            ChangePasswordResult.NetworkError
        } catch (e: SerializationException) {
            ChangePasswordResult.Unknown(e.message)
        } catch (e: Exception) {
            ChangePasswordResult.Unknown(e.message)
        }
    }

    override suspend fun requestResetOtp(email: String): ApiResult<ForgotPasswordResultDto> {
        return try {
            val response = authApi.forgotPassword(ForgotPasswordRequest(email = email))
            val data = response.data
            if (response.status && data != null) {
                ApiResult.Success(data)
            } else {
                ApiResult.Failure(
                    ApiFailure(
                        type = ApiErrorType.UNKNOWN,
                        message = response.message,
                        httpStatus = 200
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string()
            ApiResult.Failure(errorParser.parse(body, e.code()))
        } catch (e: IOException) {
            ApiResult.Failure(
                ApiFailure(
                    type = ApiErrorType.NETWORK,
                    message = e.message ?: "Network error",
                    httpStatus = null
                )
            )
        } catch (e: SerializationException) {
            ApiResult.Failure(
                ApiFailure(
                    type = ApiErrorType.UNKNOWN,
                    message = e.message ?: "Serialization error",
                    httpStatus = null
                )
            )
        } catch (e: Exception) {
            ApiResult.Failure(
                ApiFailure(
                    type = ApiErrorType.UNKNOWN,
                    message = e.message ?: "Unknown error",
                    httpStatus = null
                )
            )
        }
    }

    override suspend fun verifyResetOtp(email: String, otp: String): ApiResult<VerifyOtpResultDto> {
        return try {
            val response = authApi.verifyOtp(VerifyOtpRequest(email = email, otp = otp))
            val data = response.data
            if (response.status && data != null && data.resetToken.isNotBlank()) {
                ApiResult.Success(data)
            } else {
                ApiResult.Failure(
                    ApiFailure(
                        type = ApiErrorType.UNKNOWN,
                        message = response.message,
                        httpStatus = 200
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string()
            ApiResult.Failure(errorParser.parse(body, e.code()))
        } catch (e: IOException) {
            ApiResult.Failure(
                ApiFailure(
                    type = ApiErrorType.NETWORK,
                    message = e.message ?: "Network error",
                    httpStatus = null
                )
            )
        } catch (e: SerializationException) {
            ApiResult.Failure(
                ApiFailure(
                    type = ApiErrorType.UNKNOWN,
                    message = e.message ?: "Serialization error",
                    httpStatus = null
                )
            )
        } catch (e: Exception) {
            ApiResult.Failure(
                ApiFailure(
                    type = ApiErrorType.UNKNOWN,
                    message = e.message ?: "Unknown error",
                    httpStatus = null
                )
            )
        }
    }

    override suspend fun resetPassword(resetToken: String, newPassword: String): ApiResult<Unit> {
        return try {
            val response = authApi.resetPassword(
                ResetPasswordRequest(resetToken = resetToken, newPassword = newPassword)
            )
            if (response.status) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure(
                    ApiFailure(
                        type = ApiErrorType.UNKNOWN,
                        message = response.message,
                        httpStatus = 200
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string()
            ApiResult.Failure(errorParser.parse(body, e.code()))
        } catch (e: IOException) {
            ApiResult.Failure(
                ApiFailure(
                    type = ApiErrorType.NETWORK,
                    message = e.message ?: "Network error",
                    httpStatus = null
                )
            )
        } catch (e: SerializationException) {
            ApiResult.Failure(
                ApiFailure(
                    type = ApiErrorType.UNKNOWN,
                    message = e.message ?: "Serialization error",
                    httpStatus = null
                )
            )
        } catch (e: Exception) {
            ApiResult.Failure(
                ApiFailure(
                    type = ApiErrorType.UNKNOWN,
                    message = e.message ?: "Unknown error",
                    httpStatus = null
                )
            )
        }
    }
}
