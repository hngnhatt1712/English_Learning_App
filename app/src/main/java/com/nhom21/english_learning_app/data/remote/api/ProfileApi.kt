package com.nhom21.english_learning_app.data.remote.api

import com.nhom21.english_learning_app.core.network.ApiResponse
import com.nhom21.english_learning_app.data.remote.dto.ProfileDto
import com.nhom21.english_learning_app.data.remote.dto.UpdateProfileRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

/**
 * Retrofit interface for Profile endpoints per api-contract section 7.3.
 * All paths are relative without leading slashes per decision D-A2.
 */
interface ProfileApi {

    /**
     * GET /api/v1/profile (🔐 Bearer required).
     * Returns the authenticated user's profile including their selected level summary.
     * [ProfileDto.level] is null when the user has not yet selected a level.
     */
    @GET("profile")
    suspend fun getProfile(): ApiResponse<ProfileDto>

    /**
     * PUT /api/v1/profile (🔐 Bearer required).
     * Partial update: only fields present in the JSON body are changed.
     * At least one of [UpdateProfileRequest.username] or [UpdateProfileRequest.selectedLevelId]
     * must be provided; sending neither returns 422 validation-error.
     * Null fields are omitted from the request body (encodeDefaults = false); sending an
     * explicit null for any field also causes 422 from the server.
     *
     * Possible errors: 409 username-taken, 403 level-locked, 404 not-found, 422 validation-error.
     */
    @PUT("profile")
    suspend fun updateProfile(
        @Body request: UpdateProfileRequest
    ): ApiResponse<ProfileDto>
}

