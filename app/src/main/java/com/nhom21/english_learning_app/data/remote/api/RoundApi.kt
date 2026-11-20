package com.nhom21.english_learning_app.data.remote.api

import com.nhom21.english_learning_app.core.network.ApiResponse
import com.nhom21.english_learning_app.data.remote.dto.RoundDetailDto
import com.nhom21.english_learning_app.data.remote.dto.RoundResultDto
import com.nhom21.english_learning_app.data.remote.dto.SubmitRoundRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface RoundApi {
    @GET("rounds/{roundId}")
    suspend fun getRound(@Path("roundId") roundId: String): Response<ApiResponse<RoundDetailDto>>

    @POST("rounds/{roundId}/submit")
    suspend fun submitRound(
        @Path("roundId") roundId: String,
        @Body request: SubmitRoundRequest,
    ): Response<ApiResponse<RoundResultDto>>
}
