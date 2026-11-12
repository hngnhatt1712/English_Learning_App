package com.nhom21.english_learning_app.domain.model

/**
 * Domain representation of activity / round types per api-contract section 6.1.
 */
enum class RoundType {
    LEARN,
    RECOGNITION,
    MATCHING,
    APPLICATION,
    REVIEW,
    UNKNOWN;

    companion object {
        /**
         * Safely parses a raw type string into [RoundType] case-insensitively.
         * Falls back to [UNKNOWN] for unrecognized values or null/blank inputs.
         */
        fun fromRaw(value: String?): RoundType {
            if (value.isNullOrBlank()) return UNKNOWN
            return when (value.trim().lowercase()) {
                "learn" -> LEARN
                "recognition" -> RECOGNITION
                "matching" -> MATCHING
                "application" -> APPLICATION
                "review" -> REVIEW
                else -> UNKNOWN
            }
        }
    }
}

