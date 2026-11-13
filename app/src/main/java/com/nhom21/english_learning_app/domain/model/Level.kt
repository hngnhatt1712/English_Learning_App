package com.nhom21.english_learning_app.domain.model

/** Display data for level selection. Access rules remain on the server. */
data class Level(
    val id: String,
    val name: String,
    val description: String?,
    val orderIndex: Int
)
