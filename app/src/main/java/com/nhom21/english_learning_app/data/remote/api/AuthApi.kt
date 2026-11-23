package com.nhom21.english_learning_app.data.remote.api

import com.nhom21.english_learning_app.core.network.ApiResponse
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
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

/**
 * Retrofit interface for Authentication endpoints per contract section 7.2.
 * All paths are relative without leading slashes per decision D-A2.
 */
interface AuthApi {

    @POST("auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): ApiResponse<AuthResultDto>

    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): ApiResponse<AuthResultDto>

    @GET("auth/me")
    suspend fun me(): ApiResponse<UserDto>

    @POST("auth/logout")
    suspend fun logout(
        @Body request: LogoutRequest
    ): ApiResponse<Unit?>

    /** PUT /api/v1/auth/change-password. Returns a NEW token; the old one is invalidated (contract 4.1). */
    @PUT("auth/change-password")
    suspend fun changePassword(
        @Body request: ChangePasswordRequest
    ): ApiResponse<AuthResultDto>

    /** POST /api/v1/auth/forgot-password. Public endpoint; sends a 6-digit OTP email (contract 7.2 & 16.2.1). */
    @POST("auth/forgot-password")
    suspend fun forgotPassword(
        @Body request: ForgotPasswordRequest
    ): ApiResponse<ForgotPasswordResultDto>

    /** POST /api/v1/auth/forgot-password/verify-otp. Public endpoint; verifies OTP and returns one-time resetToken (contract 7.2 & 16.2.2). */
    @POST("auth/forgot-password/verify-otp")
    suspend fun verifyOtp(
        @Body request: VerifyOtpRequest
    ): ApiResponse<VerifyOtpResultDto>

    /** POST /api/v1/auth/reset-password. Public endpoint; resets password using one-time resetToken (contract 7.2 & 16.2.3). */
    @POST("auth/reset-password")
    suspend fun resetPassword(
        @Body request: ResetPasswordRequest
    ): ApiResponse<Unit?>
}
