package com.nhom21.english_learning_app.domain.model

data class Profile(
    val id: String,
    val username: String,
    val email: String,
    val level: ProfileLevel?,
    val totalStars: Int,
    val maxStars: Int,
    val currentStreak: Int,
    val createdAt: String
)

data class ProfileLevel(
    val id: String,
    val name: String,
    val stars: Int,
    val maxStars: Int,
    val progressPercent: Double
)
