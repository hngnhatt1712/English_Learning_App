package com.nhom21.english_learning_app.data.remote.api

import com.nhom21.english_learning_app.core.network.ApiResponse
import com.nhom21.english_learning_app.data.remote.dto.VocabularyActionRequest
import com.nhom21.english_learning_app.data.remote.dto.VocabularyActionResultDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface VocabularyApi {
    @POST("rounds/{roundId}/vocabulary-action")
    suspend fun vocabularyAction(
        @Path("roundId") roundId: String,
        @Body request: VocabularyActionRequest,
    ): Response<ApiResponse<VocabularyActionResultDto>>
}
