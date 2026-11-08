package com.nhom21.english_learning_app.features.profile

import com.nhom21.english_learning_app.BuildConfig
import com.nhom21.english_learning_app.features.home.HomeTab

/**
 * Hằng số cấu hình chung cho Hồ sơ cá nhân (nguồn sự thật duy nhất).
 */
object ProfileConstants {
    const val ADMIN_EMAIL = "daonhat1712@gmail.com"
    const val DEFAULT_MAX_STARS = 135
}

/**
 * Lựa chọn chế độ giao diện
 */
enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * Loại phản hồi khi gửi Liên hệ & Góp ý
 */
enum class ContactCategory(val label: String) {
    FEEDBACK("Góp ý"),
    BUG_REPORT("Báo lỗi"),
    OTHER("Khác")
}

/**
 * UI State cho màn hình Profile (Hồ sơ cá nhân) của Owla English.
 * Tất cả số liệu hiển thị được lấy trực tiếp từ State và không hardcode/tự bịa.
 */
data class ProfileUiState(
    // Thông tin cá nhân (Header Card)
    val userId: String = "",
    val username: String = "",
    val email: String = "",
    val levelName: String = "Level 1",
    val totalStars: Int = 0,
    val maxStars: Int = ProfileConstants.DEFAULT_MAX_STARS,
    val currentStreak: Int = 0,
    val adminEmail: String = ProfileConstants.ADMIN_EMAIL,

    // Thông tin phiên bản kỹ thuật
    val appVersionName: String = BuildConfig.VERSION_NAME,
    val appVersionCode: Int = BuildConfig.VERSION_CODE,

    // Cài đặt thông báo & hệ thống
    val dailyReminderEnabled: Boolean = true,
    val reminderTime: String = "20:00",
    val reviewReminderEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,

    // Trạng thái Dialog / BottomSheet
    val showEditUsernameDialog: Boolean = false,
    val showChangePasswordDialog: Boolean = false,
    val showLogoutDialog: Boolean = false,
    val showContactUsSheet: Boolean = false,
    val showThemeDialog: Boolean = false,

    // Form data & Validation
    val editUsernameInput: String = "",
    val editUsernameError: String? = null,

    val currentPasswordInput: String = "",
    val newPasswordInput: String = "",
    val confirmPasswordInput: String = "",
    val currentPasswordError: String? = null,
    val newPasswordError: String? = null,
    val confirmPasswordError: String? = null,
    val passwordError: String? = null,

    val contactCategory: ContactCategory = ContactCategory.FEEDBACK,
    val contactMessageInput: String = "",
    val contactMessageError: String? = null,

    // State điều khiển
    val selectedTab: HomeTab = HomeTab.PROFILE,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isLoggingOut: Boolean = false,
    val snackbarMessage: String? = null,
    val errorMessage: String? = null
) {
    val currentPassword: String
        get() = currentPasswordInput

    val newPassword: String
        get() = newPasswordInput

    val confirmPassword: String
        get() = confirmPasswordInput

    override fun toString(): String {
        return "ProfileUiState(userId=$userId, username=$username, email=REDACTED, " +
            "showChangePasswordDialog=$showChangePasswordDialog, " +
            "currentPassword=REDACTED, newPassword=REDACTED, confirmPassword=REDACTED, " +
            "currentPasswordError=$currentPasswordError, newPasswordError=$newPasswordError, " +
            "confirmPasswordError=$confirmPasswordError, passwordError=$passwordError, " +
            "isSaving=$isSaving, isLoggingOut=$isLoggingOut)"
    }
}
