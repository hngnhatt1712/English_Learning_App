package com.nhom21.english_learning_app.core.network

import kotlinx.serialization.Serializable

/**
 * Generic wrapper for non-paginated list responses per api-contract section 11.2.
 * Used by endpoints that return a fixed small list (e.g. GET /levels).
 * Paginated endpoints use a separate structure with total/page/limit fields.
 *
 * @param T The type of item in the list.
 */
@Serializable
data class ListData<T>(
    val items: List<T>
)

