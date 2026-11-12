package com.nhom21.english_learning_app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Request body for POST /api/v1/auth/forgot-password per api-contract section 7.2 & 16.2.1.
 */
@Serializable
data class ForgotPasswordRequest(
    val email: String
) {
    override fun toString(): String = "ForgotPasswordRequest(email=REDACTED)"
}

/**
 * Response data payload for POST /api/v1/auth/forgot-password per api-contract section 7.2 & 16.2.1.
 */
@Serializable
data class ForgotPasswordResultDto(
    val retryAfterSeconds: Int = 60
)

/**
 * Request body for POST /api/v1/auth/forgot-password/verify-otp per api-contract section 7.2 & 16.2.2.
 */
@Serializable
data class VerifyOtpRequest(
    val email: String,
    val otp: String
) {
    override fun toString(): String = "VerifyOtpRequest(email=REDACTED, otp=REDACTED)"
}

/**
 * Response data payload for POST /api/v1/auth/forgot-password/verify-otp per api-contract section 7.2 & 16.2.2.
 */
@Serializable
data class VerifyOtpResultDto(
    val resetToken: String,
    val expiresInSeconds: Int = 600
) {
    override fun toString(): String =
        "VerifyOtpResultDto(resetToken=REDACTED, expiresInSeconds=$expiresInSeconds)"
}

/**
 * Request body for POST /api/v1/auth/reset-password per api-contract section 7.2 & 16.2.3.
 */
@Serializable
data class ResetPasswordRequest(
    val resetToken: String,
    val newPassword: String
) {
    override fun toString(): String =
        "ResetPasswordRequest(resetToken=REDACTED, newPassword=REDACTED)"
}

