package com.nhom21.english_learning_app.core.network

import kotlinx.serialization.Serializable

/**
 * Closed enumeration of error types per api-contract section 3.3,
 * plus client-only synthetic errors [NETWORK] and [UNKNOWN].
 */
enum class ApiErrorType(val wireName: String) {
    BAD_REQUEST("bad-request"),
    WRONG_PASSWORD("wrong-password"),
    UNAUTHORIZED("unauthorized"),
    INVALID_CREDENTIALS("invalid-credentials"),
    FORBIDDEN("forbidden"),
    LEVEL_LOCKED("level-locked"),
    TOPIC_LOCKED("topic-locked"),
    ROUND_LOCKED("round-locked"),
    NOT_FOUND("not-found"),
    METHOD_NOT_ALLOWED("method-not-allowed"),
    EMAIL_TAKEN("email-taken"),
    USERNAME_TAKEN("username-taken"),
    ROUND_INCOMPLETE("round-incomplete"),
    PAYLOAD_TOO_LARGE("payload-too-large"),
    VALIDATION_ERROR("validation-error"),
    RATE_LIMITED("rate-limited"),
    INTERNAL_ERROR("internal-error"),
    // Client-side synthetic types
    NETWORK("network"),
    UNKNOWN("unknown");

    companion object {
        /**
         * Safely maps wire string to [ApiErrorType]. Never throws; returns [UNKNOWN] if unmatched.
         */
        fun fromWire(value: String?): ApiErrorType {
            if (value.isNullOrBlank()) return UNKNOWN
            return entries.firstOrNull { it.wireName.equals(value, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

/**
 * Field-level validation error per api-contract section 3.2 (error.errors[]).
 */
data class FieldError(
    val field: String,
    val message: String
)

/**
 * Wire error body structure inside ApiResponse per api-contract section 3.2.
 */
@Serializable
data class ApiErrorBody(
    val type: String? = null,
    val detail: String? = null,
    val traceId: String? = null,
    val errors: List<FieldErrorWire>? = null
)

/**
 * Wire representation of individual field error.
 */
@Serializable
data class FieldErrorWire(
    val field: String? = null,
    val message: String? = null,
    val code: String? = null
)
