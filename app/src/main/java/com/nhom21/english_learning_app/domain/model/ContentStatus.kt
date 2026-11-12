package com.nhom21.english_learning_app.domain.model

/**
 * Domain representation of content access/progress status per api-contract section 6.1.
 */
enum class ContentStatus {
    LOCKED,
    UNLOCKED,
    COMPLETED,
    UNKNOWN;

    companion object {
        /**
         * Safely parses a raw status string into [ContentStatus] case-insensitively.
         * Falls back to [UNKNOWN] for unrecognized values or null/blank inputs.
         */
        fun fromRaw(value: String?): ContentStatus {
            if (value.isNullOrBlank()) return UNKNOWN
            return when (value.trim().lowercase()) {
                "locked" -> LOCKED
                "unlocked" -> UNLOCKED
                "completed" -> COMPLETED
                else -> UNKNOWN
            }
        }
    }
}

