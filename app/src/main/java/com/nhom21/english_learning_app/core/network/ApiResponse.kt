package com.nhom21.english_learning_app.core.network

import kotlinx.serialization.Serializable

/**
 * Standard API response envelope per api-contract section 3.1.
 * All API responses (except HTTP 204) are wrapped in this envelope.
 */
@Serializable
data class ApiResponse<T>(
    val status: Boolean,
    val message: String,
    val data: T? = null,
    val error: ApiErrorBody? = null
)
