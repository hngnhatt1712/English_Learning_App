package com.nhom21.english_learning_app.data.remote.api

import com.nhom21.english_learning_app.core.network.ApiResponse
import com.nhom21.english_learning_app.data.remote.dto.HomeDataDto
import retrofit2.http.GET

/**
 * Retrofit interface for Home dashboard endpoints per contract section 7.9.
 */
interface HomeApi {

    @GET("home")
    suspend fun getHome(): ApiResponse<HomeDataDto>
}
