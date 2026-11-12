package com.nhom21.english_learning_app.data.repository

import com.nhom21.english_learning_app.core.network.ApiErrorParser
import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiFailure
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.remote.api.TopicApi
import com.nhom21.english_learning_app.data.remote.mapper.toDomain
import com.nhom21.english_learning_app.domain.model.LessonDetail
import com.nhom21.english_learning_app.domain.model.Topic
import com.nhom21.english_learning_app.domain.model.TopicDetail
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository interface for retrieving topics and lesson learning content.
 */
interface TopicRepository {

    /**
     * Retrieves the list of topics belonging to [levelId].
     */
    suspend fun getTopics(levelId: String): ApiResult<List<Topic>>

    /**
     * Retrieves full topic detail including its lessons for [topicId].
     */
    suspend fun getTopicDetail(topicId: String): ApiResult<TopicDetail>

    /**
     * Retrieves lesson detail and constituent rounds for [lessonId].
     */
    suspend fun getLesson(lessonId: String): ApiResult<LessonDetail>
}

/**
 * Default implementation of [TopicRepository] communicating via [TopicApi].
 */
@Singleton
class TopicRepositoryImpl @Inject constructor(
    private val topicApi: TopicApi,
    private val errorParser: ApiErrorParser
) : TopicRepository {

    override suspend fun getTopics(levelId: String): ApiResult<List<Topic>> {
        return try {
            val response = topicApi.getTopics(levelId = levelId)
            val data = response.data
            if (response.status && data != null) {
                ApiResult.Success(data.items.map { it.toDomain() })
            } else {
                ApiResult.Failure(
                    ApiFailure(
                        type = ApiErrorType.UNKNOWN,
                        message = response.message,
                        httpStatus = 200
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
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

    override suspend fun getTopicDetail(topicId: String): ApiResult<TopicDetail> {
        return try {
            val response = topicApi.getTopicDetail(topicId = topicId)
            val data = response.data
            if (response.status && data != null) {
                ApiResult.Success(data.toDomain())
            } else {
                ApiResult.Failure(
                    ApiFailure(
                        type = ApiErrorType.UNKNOWN,
                        message = response.message,
                        httpStatus = 200
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
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

    override suspend fun getLesson(lessonId: String): ApiResult<LessonDetail> {
        return try {
            val response = topicApi.getLesson(lessonId = lessonId)
            val data = response.data
            if (response.status && data != null) {
                ApiResult.Success(data.toDomain())
            } else {
                ApiResult.Failure(
                    ApiFailure(
                        type = ApiErrorType.UNKNOWN,
                        message = response.message,
                        httpStatus = 200
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
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
}

