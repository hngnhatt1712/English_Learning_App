package com.nhom21.english_learning_app.data.remote.api

import com.nhom21.english_learning_app.core.network.ApiResponse
import com.nhom21.english_learning_app.data.remote.dto.CheckAnswerRequest
import com.nhom21.english_learning_app.data.remote.dto.QuestionResultDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface QuestionApi {
    @POST("questions/{questionId}/check")
    suspend fun checkAnswer(
        @Path("questionId") questionId: String,
        @Body request: CheckAnswerRequest,
    ): Response<ApiResponse<QuestionResultDto>>
}
