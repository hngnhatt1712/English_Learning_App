package com.nhom21.english_learning_app.data.repository

import com.nhom21.english_learning_app.core.network.ApiErrorParser
import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiFailure
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.core.network.TokenManager
import com.nhom21.english_learning_app.data.remote.api.AuthApi
import com.nhom21.english_learning_app.data.remote.dto.AuthResultDto
import com.nhom21.english_learning_app.data.remote.dto.LoginRequest
import com.nhom21.english_learning_app.data.remote.dto.LogoutRequest
import com.nhom21.english_learning_app.data.remote.dto.RegisterRequest
import com.nhom21.english_learning_app.data.remote.dto.UserDto
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authentication repository interface per decisions D-A11 to D-A13.
 */
interface AuthRepository {
    suspend fun register(username: String, email: String, password: String): ApiResult<AuthResultDto>
    suspend fun login(identifier: String, password: String): ApiResult<AuthResultDto>
    suspend fun me(): ApiResult<UserDto>
    suspend fun logout(fcmToken: String? = null): ApiResult<Unit>
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

    override suspend fun register(username: String, email: String, password: String): ApiResult<AuthResultDto> {
        return try {
            val response = authApi.register(RegisterRequest(username = username, email = email, password = password))
            val data = response.data
            if (response.status && data != null) {
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

    override suspend fun logout(fcmToken: String?): ApiResult<Unit> {
        var serverResult: ApiResult<Unit>? = null
        try {
            val response = authApi.logout(LogoutRequest(fcmToken = fcmToken))
            serverResult = if (response.status) {
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
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string()
            serverResult = ApiResult.Failure(errorParser.parse(body, e.code()))
        } catch (e: IOException) {
            serverResult = ApiResult.Failure(
                ApiFailure(
                    type = ApiErrorType.NETWORK,
                    message = e.message ?: "Network error",
                    httpStatus = null
                )
            )
        } catch (e: SerializationException) {
            serverResult = ApiResult.Failure(
                ApiFailure(
                    type = ApiErrorType.UNKNOWN,
                    message = e.message ?: "Serialization error",
                    httpStatus = null
                )
            )
        } finally {
            // Decision D-A12: Guarantee token is cleared locally even on server error or network disconnect
            tokenManager.clearToken()
        }
        return serverResult ?: ApiResult.Success(Unit)
    }
}
