package com.nhom21.english_learning_app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Request payload for PUT /api/v1/auth/change-password per contract section 7.2.
 */
@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
) {
    override fun toString(): String {
        return "ChangePasswordRequest(currentPassword=REDACTED, newPassword=REDACTED)"
    }
}

