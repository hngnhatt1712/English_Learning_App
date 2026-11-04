package com.nhom21.english_learning_app.data.repository

import com.nhom21.english_learning_app.core.network.ApiErrorParser
import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiFailure
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.remote.api.HomeApi
import com.nhom21.english_learning_app.data.remote.dto.HomeDataDto
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface for repository fetching Home dashboard summary.
 */
interface HomeRepository {
    suspend fun getHomeData(): ApiResult<HomeDataDto>
}

/**
 * Implementation of [HomeRepository] retrieving home overview from [HomeApi].
 */
@Singleton
class HomeRepositoryImpl @Inject constructor(
    private val homeApi: HomeApi,
    private val errorParser: ApiErrorParser,
) : HomeRepository {

    override suspend fun getHomeData(): ApiResult<HomeDataDto> {
        return try {
            val response = homeApi.getHome()
            val data = response.data
            if (response.status && (data != null)) {
                ApiResult.Success(data)
            } else {
                ApiResult.Failure(
                    ApiFailure(
                        type = ApiErrorType.UNKNOWN,
                        message = response.message,
                        httpStatus = 200
                    )
                )
            }
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
