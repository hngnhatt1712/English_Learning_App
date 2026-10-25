package com.nhom21.english_learning_app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Request payload for POST /auth/register per contract section 7.2.
 */
@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
) {
    override fun toString(): String {
        return "RegisterRequest(username=$username, email=$email, password=REDACTED)"
    }
}

/**
 * Request payload for POST /auth/login per contract section 7.2.
 */
@Serializable
data class LoginRequest(
    val identifier: String,
    val password: String
) {
    override fun toString(): String {
        return "LoginRequest(identifier=$identifier, password=REDACTED)"
    }
}

/**
 * Request payload for POST /auth/logout per contract section 7.2.
 */
@Serializable
data class LogoutRequest(
    val fcmToken: String? = null
)

/**
 * User profile representation returned by auth endpoints per contract section 6.2.
 */
@Serializable
data class UserDto(
    val id: String,
    val username: String,
    val email: String,
    val selectedLevelId: String? = null,
    val currentStreak: Int,
    val lastLearningDate: String? = null,
    val createdAt: String
)

/**
 * Authentication response envelope data per contract section 7.2.
 */
@Serializable
data class AuthResultDto(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val user: UserDto
) {
    override fun toString(): String {
        return "AuthResultDto(accessToken=REDACTED, tokenType=$tokenType, expiresIn=$expiresIn, user=$user)"
    }
}
