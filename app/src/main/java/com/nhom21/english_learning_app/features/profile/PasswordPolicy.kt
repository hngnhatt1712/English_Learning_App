package com.nhom21.english_learning_app.features.profile

/**
 * Pure password validation policy aligned with api-contract section 4.4 and backend schema rules.
 *
 * - Measures length in Unicode code points via [String.codePointCount] to match Python/Pydantic.
 * - Requires 8..64 code points, at least one ASCII letter (`A-Za-z`), and at least one ASCII digit (`0-9`).
 * - Never trims or normalizes whitespace/Unicode characters.
 */
object PasswordPolicy {

    const val MIN_CODE_POINTS = 8
    const val MAX_CODE_POINTS = 64

    const val ERROR_CURRENT_REQUIRED = "Vui lòng nhập mật khẩu hiện tại"
    const val ERROR_CURRENT_TOO_LONG = "Mật khẩu hiện tại không được vượt quá 64 ký tự"
    const val ERROR_CURRENT_INVALID = "Mật khẩu hiện tại không hợp lệ"
    const val ERROR_CURRENT_WRONG = "Mật khẩu hiện tại không đúng"

    const val ERROR_NEW_REQUIRED = "Vui lòng nhập mật khẩu mới"
    const val ERROR_NEW_TOO_SHORT = "Mật khẩu mới phải có tối thiểu 8 ký tự"
    const val ERROR_NEW_TOO_LONG = "Mật khẩu mới không được vượt quá 64 ký tự"
    const val ERROR_NEW_COMPLEXITY = "Mật khẩu mới phải chứa ít nhất 1 chữ cái và 1 chữ số"
    const val ERROR_NEW_FORMAT_OR_SAME = "Mật khẩu mới phải có chữ, số và khác mật khẩu hiện tại"
    const val ERROR_NEW_INVALID = "Mật khẩu mới không hợp lệ"

    const val ERROR_CONFIRM_MISMATCH = "Mật khẩu xác nhận không khớp"

    const val ERROR_VALIDATION_GENERAL = "Thông tin chưa hợp lệ. Vui lòng kiểm tra lại."
    const val ERROR_NETWORK = "Không thể kết nối. Vui lòng thử lại."
    const val ERROR_UNKNOWN = "Đã xảy ra lỗi. Vui lòng thử lại sau."
    const val MESSAGE_SUCCESS = "Đã đổi mật khẩu thành công!"

    /**
     * Counts the number of Unicode code points in [value] without trimming or normalizing.
     */
    fun codePointLength(value: String): Int = value.codePointCount(0, value.length)

    /**
     * Validates the current password field on the client side.
     * Only rejects empty strings; maximum length is delegated to the backend.
     */
    fun validateCurrentPassword(value: String): String? {
        if (value.isEmpty()) {
            return ERROR_CURRENT_REQUIRED
        }
        return null
    }

    /**
     * Validates the new password field according to contract section 4.4 (8..64 Unicode code points,
     * at least one ASCII letter and one ASCII digit, without trimming).
     */
    fun validateNewPassword(value: String): String? {
        val cpCount = codePointLength(value)
        if (cpCount == 0) {
            return ERROR_NEW_REQUIRED
        }
        if (cpCount < MIN_CODE_POINTS) {
            return ERROR_NEW_TOO_SHORT
        }
        if (cpCount > MAX_CODE_POINTS) {
            return ERROR_NEW_TOO_LONG
        }

        var hasAsciiLetter = false
        var hasAsciiDigit = false
        var index = 0
        while (index < value.length) {
            val cp = value.codePointAt(index)
            if ((cp in 'A'.code..'Z'.code) || (cp in 'a'.code..'z'.code)) {
                hasAsciiLetter = true
            } else if (cp in '0'.code..'9'.code) {
                hasAsciiDigit = true
            }
            index += Character.charCount(cp)
        }

        if (!hasAsciiLetter || !hasAsciiDigit) {
            return ERROR_NEW_COMPLEXITY
        }
        return null
    }

    /**
     * Validates that the confirmation password matches the new password verbatim.
     */
    fun validateConfirmPassword(newPassword: String, confirmPassword: String): String? {
        if (confirmPassword != newPassword) {
            return ERROR_CONFIRM_MISMATCH
        }
        return null
    }

    /**
     * Maps server validation error code for `currentPassword` into a localized Vietnamese message.
     */
    fun mapCurrentPasswordCode(code: String): String {
        return when (code) {
            "REQUIRED", "MIN_LENGTH" -> ERROR_CURRENT_REQUIRED
            "MAX_LENGTH" -> ERROR_CURRENT_TOO_LONG
            else -> ERROR_CURRENT_INVALID
        }
    }

    /**
     * Maps server validation error code for `newPassword` into a localized Vietnamese message.
     */
    fun mapNewPasswordCode(code: String): String {
        return when (code) {
            "REQUIRED" -> ERROR_NEW_REQUIRED
            "MIN_LENGTH" -> ERROR_NEW_TOO_SHORT
            "MAX_LENGTH" -> ERROR_NEW_TOO_LONG
            "INVALID_FORMAT" -> ERROR_NEW_FORMAT_OR_SAME
            else -> ERROR_NEW_INVALID
        }
    }
}
