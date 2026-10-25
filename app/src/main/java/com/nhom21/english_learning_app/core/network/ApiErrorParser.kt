package com.nhom21.english_learning_app.core.network

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Robust error parser converting raw response bodies into typed [ApiFailure].
 * Guaranteed to never throw; falls back to [ApiErrorType.UNKNOWN] for non-envelope bodies.
 */
@Singleton
class ApiErrorParser @Inject constructor(
    private val json: Json
) {
    /**
     * Parses the error body string and HTTP status code into an [ApiFailure].
     */
    fun parse(body: String?, httpStatus: Int?): ApiFailure {
        if (body.isNullOrBlank()) {
            return ApiFailure(
                type = ApiErrorType.UNKNOWN,
                message = "Empty error response",
                fieldErrors = emptyList(),
                httpStatus = httpStatus
            )
        }

        try {
            // First attempt: Standard ApiResponse envelope with ApiErrorBody
            val envelope = json.decodeFromString<ApiResponse<Unit>>(body)
            val errorBody = envelope.error
            if (errorBody != null) {
                val errorType = ApiErrorType.fromWire(errorBody.type)
                val message = errorBody.detail ?: envelope.message
                val fieldErrors = errorBody.errors?.mapNotNull { item ->
                    val field = item.field
                    val msg = item.message
                    if (field != null && msg != null) {
                        FieldError(field = field, message = msg)
                    } else null
                } ?: emptyList()

                return ApiFailure(
                    type = errorType,
                    message = message,
                    fieldErrors = fieldErrors,
                    httpStatus = httpStatus
                )
            }

            // Envelope present but error field is null
            if (envelope.message.isNotBlank()) {
                return ApiFailure(
                    type = ApiErrorType.UNKNOWN,
                    message = envelope.message,
                    fieldErrors = emptyList(),
                    httpStatus = httpStatus
                )
            }
        } catch (_: SerializationException) {
            // Fallback parsing below
        } catch (_: IllegalArgumentException) {
            // Fallback parsing below
        }

        // Second attempt: Fallback for FastAPI default error {"detail": "..."}
        try {
            val jsonElement = json.parseToJsonElement(body)
            val detailElement = jsonElement.jsonObject["detail"]?.jsonPrimitive?.content
            if (!detailElement.isNullOrBlank()) {
                return ApiFailure(
                    type = ApiErrorType.UNKNOWN,
                    message = detailElement,
                    fieldErrors = emptyList(),
                    httpStatus = httpStatus
                )
            }
        } catch (_: SerializationException) {
            // Non-JSON content (e.g., HTML, plain text)
        } catch (_: IllegalArgumentException) {
            // Non-JSON content
        } catch (_: IllegalStateException) {
            // Non-object JSON
        }

        // Final fallback: Non-envelope response preserving HTTP status
        return ApiFailure(
            type = ApiErrorType.UNKNOWN,
            message = "Unknown error occurred",
            fieldErrors = emptyList(),
            httpStatus = httpStatus
        )
    }
}
