package com.nhom21.english_learning_app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Data transfer object representing the user's profile per api-contract section 7.3.
 * Returned by both GET and PUT /api/v1/profile.
 *
 * @property id UUID of the user.
 * @property username Display username.
 * @property email User's email address (read-only at MVP; cannot be changed via profile update).
 * @property level Compact summary of the currently selected level, or null if no level selected yet.
 * @property totalStars Total stars earned across all levels (max 135).
 * @property maxStars Maximum possible stars across all levels (always 135 at MVP).
 * @property currentStreak Current daily learning streak (already adjusted for timezone on server).
 * @property createdAt ISO 8601 UTC timestamp of account creation (e.g. "2026-10-04T10:30:00Z").
 *   Uses the same String type as [UserDto.createdAt] for consistency.
 */
@Serializable
data class ProfileDto(
    val id: String,
    val username: String,
    val email: String,
    val level: LevelBriefDto?,
    val totalStars: Int,
    val maxStars: Int,
    val currentStreak: Int,
    val createdAt: String,
)

