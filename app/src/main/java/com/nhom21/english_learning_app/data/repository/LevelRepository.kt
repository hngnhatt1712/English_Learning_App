package com.nhom21.english_learning_app.data.repository

import com.nhom21.english_learning_app.core.network.ApiErrorParser
import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiFailure
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.remote.api.LevelApi
import com.nhom21.english_learning_app.domain.model.Level
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

interface LevelRepository {
    suspend fun getLevels(): ApiResult<List<Level>>
}

@Singleton
class LevelRepositoryImpl @Inject constructor(
    private val levelApi: LevelApi,
    private val errorParser: ApiErrorParser
) : LevelRepository {
    override suspend fun getLevels(): ApiResult<List<Level>> {
        return try {
            val response = levelApi.getLevels()
            val data = response.data
            if (response.status && data != null) {
                ApiResult.Success(data.items.map { Level(it.id, it.name, it.description, it.orderIndex) })
            } else {
                ApiResult.Failure(ApiFailure(
                    type = ApiErrorType.fromWire(response.error?.type),
                    message = "Unable to load levels",
                    httpStatus = 200
                ))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            ApiResult.Failure(errorParser.parse(e.response()?.errorBody()?.string(), e.code()))
        } catch (_: IOException) {
            ApiResult.Failure(ApiFailure(ApiErrorType.NETWORK, "Network error"))
        } catch (_: SerializationException) {
            ApiResult.Failure(ApiFailure(ApiErrorType.UNKNOWN, "Invalid level response"))
        }
    }
}
