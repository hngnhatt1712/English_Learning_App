package com.nhom21.english_learning_app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Data transfer object representing a Level per api-contract section 6.3.
 * Returned as items inside GET /api/v1/levels response.
 *
 * @property id UUID of the level.
 * @property name Human-readable level name (e.g. "Beginner (A1)").
 * @property description Optional description; always present in JSON but may be null.
 * @property orderIndex 1-based display order.
 * @property status Unlock/completion status; defaults to [ContentStatus.UNKNOWN] for future values.
 * @property stars Total stars earned across all topics in this level.
 * @property maxStars Maximum possible stars for this level (topicsTotal × 15).
 * @property topicsCompleted Number of topics completed (≥ 70% on all rounds).
 * @property topicsTotal Total number of topics in this level.
 */
@Serializable
data class LevelDto(
    val id: String,
    val name: String,
    val description: String?,
    val orderIndex: Int,
    val status: ContentStatus = ContentStatus.UNKNOWN,
    val stars: Int,
    val maxStars: Int,
    val topicsCompleted: Int,
    val topicsTotal: Int,
)

