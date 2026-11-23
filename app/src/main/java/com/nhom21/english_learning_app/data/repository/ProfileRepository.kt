package com.nhom21.english_learning_app.data.repository

import com.nhom21.english_learning_app.core.network.ApiErrorParser
import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiFailure
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.remote.api.ProfileApi
import com.nhom21.english_learning_app.data.remote.dto.UpdateProfileRequest
import com.nhom21.english_learning_app.domain.model.Profile
import com.nhom21.english_learning_app.domain.model.ProfileLevel
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

interface ProfileRepository {
    suspend fun selectLevel(levelId: String): ApiResult<Profile>
}

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val profileApi: ProfileApi,
    private val errorParser: ApiErrorParser
) : ProfileRepository {
    override suspend fun selectLevel(levelId: String): ApiResult<Profile> {
        return try {
            // Default null username is omitted by the existing Json configuration.
            val response = profileApi.updateProfile(UpdateProfileRequest(selectedLevelId = levelId))
            val data = response.data
            if (response.status && data != null) {
                ApiResult.Success(Profile(
                    id = data.id,
                    username = data.username,
                    email = data.email,
                    level = data.level?.let { ProfileLevel(it.id, it.name, it.stars, it.maxStars, it.progressPercent) },
                    totalStars = data.totalStars,
                    maxStars = data.maxStars,
                    currentStreak = data.currentStreak,
                    createdAt = data.createdAt
                ))
            } else {
                ApiResult.Failure(ApiFailure(
                    type = ApiErrorType.fromWire(response.error?.type),
                    message = "Unable to save level",
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
            ApiResult.Failure(ApiFailure(ApiErrorType.UNKNOWN, "Invalid profile response"))
        }
    }
}
