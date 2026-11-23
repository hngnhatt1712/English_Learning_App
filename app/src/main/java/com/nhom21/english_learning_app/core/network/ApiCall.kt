package com.nhom21.english_learning_app.core.network

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerializationException
import retrofit2.Response
import java.io.IOException

/** Executes one Retrofit call and maps the envelope before returning to the caller. */
internal suspend fun <D : Any, T> ApiErrorParser.callApi(
    call: suspend () -> Response<ApiResponse<D>>,
    map: (D) -> T,
): ApiResult<T> {
    return try {
        val response = call()
        if (!response.isSuccessful) {
            return ApiResult.Failure(parse(response.errorBody()?.string(), response.code()))
        }

        val envelope = response.body()
        val data = envelope?.data
        if (envelope?.status == true && data != null) {
            ApiResult.Success(map(data))
        } else {
            val error = envelope?.error
            val failure = if (error != null) {
                fromEnvelope(error, envelope.message, response.code())
            } else {
                ApiFailure(
                    type = ApiErrorType.UNKNOWN,
                    message = if (envelope?.status == false) envelope.message else "Missing response data",
                    httpStatus = response.code(),
                )
            }
            ApiResult.Failure(failure)
        }
    } catch (_: IOException) {
        currentCoroutineContext().ensureActive()
        ApiResult.Failure(ApiFailure(ApiErrorType.NETWORK, "Network error"))
    } catch (_: SerializationException) {
        ApiResult.Failure(ApiFailure(ApiErrorType.UNKNOWN, "Invalid response"))
    }
}
