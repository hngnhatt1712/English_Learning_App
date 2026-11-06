package com.nhom21.english_learning_app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Request payload for PUT /api/v1/profile per api-contract section 7.3.
 *
 * Both fields are optional individually, but **at least one must be set** per contract:
 * sending neither field causes the server to return 422 validation-error.
 *
 * **Important serialisation contract (null-omission):**
 * - Fields left as null are **omitted entirely** from the JSON body because the app's
 *   [kotlinx.serialization.json.Json] instance is configured with `encodeDefaults = false`.
 * - Sending an explicit JSON `null` for any field causes a 422 from the server
 *   (neither `username` nor `selectedLevelId` is nullable on the server side).
 * - Callers must never construct this with both fields null.
 *
 * @property username New username (3–30 chars, [A-Za-z0-9_.], no '@'); null = keep current.
 * @property selectedLevelId UUID of the level to select; null = keep current selection.
 */
@Serializable
data class UpdateProfileRequest(
    val username: String? = null,
    val selectedLevelId: String? = null,
)

