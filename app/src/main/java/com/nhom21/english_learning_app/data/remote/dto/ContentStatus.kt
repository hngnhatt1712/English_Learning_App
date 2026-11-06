package com.nhom21.english_learning_app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the unlock/access status of a learning content item
 * (Level, Topic, Round) per api-contract section 6.1.
 *
 * [UNKNOWN] is used as a safe fallback when the server returns an unrecognised
 * value; requires [Json.coerceInputValues] = true per contract section 11.3.
 */
@Serializable
enum class ContentStatus {
    @SerialName("locked")
    LOCKED,

    @SerialName("unlocked")
    UNLOCKED,

    @SerialName("completed")
    COMPLETED,

    /** Fallback for future enum values added by the server (contract 11.3). */
    UNKNOWN
}

