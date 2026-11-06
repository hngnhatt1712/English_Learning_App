package com.nhom21.english_learning_app.features.auth

/**
 * Đại diện cho thông tin một trình độ tiếng Anh được lựa chọn.
 */
data class SelectLevelOption(
    val code: String,        // Ví dụ: "A1", "A2", "B1"
    val description: String  // Ví dụ: "I just started learning English"
)

/**
 * UI State cho màn hình Chọn trình độ tiếng Anh (SelectLevelScreen).
 */
data class SelectLevelUiState(
    val selectedLevel: String = "A2", // Mặc định chọn A2 như trong thiết kế mẫu
    val levels: List<SelectLevelOption> = listOf(
        SelectLevelOption("A1", "I just started learning English"),
        SelectLevelOption("A2", "I know some words and phrases"),
        SelectLevelOption("B1", "I can have a simple conversation")
    ),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
