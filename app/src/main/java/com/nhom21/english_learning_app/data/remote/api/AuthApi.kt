package com.nhom21.english_learning_app.data.remote.api

import com.nhom21.english_learning_app.core.network.ApiResponse
import com.nhom21.english_learning_app.data.remote.dto.AuthResultDto
import com.nhom21.english_learning_app.data.remote.dto.LoginRequest
import com.nhom21.english_learning_app.data.remote.dto.LogoutRequest
import com.nhom21.english_learning_app.data.remote.dto.RegisterRequest
import com.nhom21.english_learning_app.data.remote.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

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
}
