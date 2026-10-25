package com.nhom21.english_learning_app.core.network

/**
 * Typed result abstraction for API repository calls.
 */
sealed interface ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>
    data class Failure(val error: ApiFailure) : ApiResult<Nothing>
}

/**
 * Structured domain failure representation per decision D-A6.
 */
data class ApiFailure(
    val type: ApiErrorType,
    val message: String,
    val fieldErrors: List<FieldError> = emptyList(),
    val httpStatus: Int? = null
)
