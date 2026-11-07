package com.nhom21.english_learning_app.data.remote.api

import com.nhom21.english_learning_app.core.network.ApiResponse
import com.nhom21.english_learning_app.core.network.ListData
import com.nhom21.english_learning_app.data.remote.dto.LevelDto
import retrofit2.http.GET

/**
 * Retrofit interface for Level endpoints per api-contract section 7.4.
 * All paths are relative without leading slashes per decision D-A2.
 */
interface LevelApi {

    /**
     * GET /api/v1/levels (🔐 Bearer required).
     * Always returns exactly 3 levels ordered by orderIndex; no pagination.
     * If the user has not selected a level yet (selectedLevelId == null),
     * all three levels are returned with status "unlocked".
     */
    @GET("levels")
    suspend fun getLevels(): ApiResponse<ListData<LevelDto>>
}

