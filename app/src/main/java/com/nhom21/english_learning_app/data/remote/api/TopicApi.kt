package com.nhom21.english_learning_app.data.remote.api

import com.nhom21.english_learning_app.core.network.ApiResponse
import com.nhom21.english_learning_app.core.network.ListData
import com.nhom21.english_learning_app.data.remote.dto.LessonDetailDto
import com.nhom21.english_learning_app.data.remote.dto.TopicDetailDto
import com.nhom21.english_learning_app.data.remote.dto.TopicDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for Topics and Lessons endpoints per api-contract section 7.4.
 * Base URL already includes the "/api/v1/" prefix; all paths are relative without leading slashes.
 * Authorization Bearer token is attached automatically by [AuthInterceptor].
 */
interface TopicApi {

    /**
     * GET /api/v1/topics?levelId={levelId} (🔐 Bearer required).
     * Retrieves topics belonging to the specified level, ordered by orderIndex.
     * If the level is locked, all topics in the response are returned with status "locked".
     *
     * @param levelId Required UUID of the level.
     * @return [ApiResponse] containing [ListData] of [TopicDto].
     * Possible errors:
     * - 401: unauthorized (session expired / missing token)
     * - 404: not-found (levelId does not exist)
     * - 422: validation-error (missing or invalid levelId format)
     */
    @GET("topics")
    suspend fun getTopics(
        @Query("levelId") levelId: String
    ): ApiResponse<ListData<TopicDto>>

    /**
     * GET /api/v1/topics/{id} (🔐 Bearer required).
     * Retrieves topic details including its associated lessons (MVP has exactly 1 lesson).
     *
     * @param topicId UUID of the target topic.
     * @return [ApiResponse] containing [TopicDetailDto].
     * Possible errors:
     * - 401: unauthorized (session expired / missing token)
     * - 404: not-found (topicId does not exist)
     * - 422: validation-error (invalid UUID format)
     */
    @GET("topics/{id}")
    suspend fun getTopicDetail(
        @Path("id") topicId: String
    ): ApiResponse<TopicDetailDto>

    /**
     * GET /api/v1/lessons/{id} (🔐 Bearer required).
     * Retrieves lesson details including its 5 constituent rounds and their status.
     *
     * @param lessonId UUID of the target lesson.
     * @return [ApiResponse] containing [LessonDetailDto].
     * Possible errors:
     * - 401: unauthorized (session expired / missing token)
     * - 403: topic-locked (parent topic has not been unlocked yet)
     * - 404: not-found (lessonId does not exist)
     * - 422: validation-error (invalid UUID format)
     */
    @GET("lessons/{id}")
    suspend fun getLesson(
        @Path("id") lessonId: String
    ): ApiResponse<LessonDetailDto>
}

