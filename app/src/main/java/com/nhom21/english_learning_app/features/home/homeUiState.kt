package com.nhom21.english_learning_app.features.home

import com.nhom21.english_learning_app.core.navigation.MainTab

/**
 * Các tab thuộc thanh điều hướng Bottom Navigation Bar.
 */
typealias HomeTab = MainTab

/**
 * UI State cho màn hình Home (HomeScreen) của ứng dụng Owla English.
 */
data class HomeUiState(
    val username: String = "",
    val greetingSubtitle: String = "Hôm nay mình học tiếp nhé!",
    val currentStreak: Int = 1,
    val learnedToday: Boolean = true,

    // Thẻ bài học chính (Current Learning Topic)
    val levelName: String = "Level 1",
    val learningStatus: String = "Đang học",
    val currentTopicName: String = "Food and drinks",
    val topicOrder: Int = 1,
    val currentRound: Int = 0,
    val totalRounds: Int = 5,
    val progressPercent: Float = 0f,

    // Thẻ chỉ số giữa (Middle Stat Tiles)
    val flashcardCount: Int = 24,
    val totalStars: Int = 0,
    val maxStars: Int = 135,

    // Thẻ Ôn tập hôm nay (Review Today)
    val reviewCount: Int = 8,

    // Bottom Navigation & Trạng thái màn hình
    val selectedTab: HomeTab = HomeTab.HOME,
    val hasPracticeNotification: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
