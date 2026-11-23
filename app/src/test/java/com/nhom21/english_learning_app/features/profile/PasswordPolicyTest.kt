package com.nhom21.english_learning_app.features.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordPolicyTest {

    private val emoji = "\uD83D\uDE00" // U+1F600: 1 Unicode code point, 2 UTF-16 code units

    // 13(a): "a1" + 6 non-BMP emoji -> 8 code points (UTF-16 length == 14) -> valid
    @Test
    fun `8 Unicode code points with 6 non-BMP emoji and UTF-16 length 14 is valid`() {
        val password = "a1" + emoji.repeat(6)
        assertEquals(14, password.length)
        assertEquals(8, PasswordPolicy.codePointLength(password))
        assertNull(PasswordPolicy.validateNewPassword(password))
    }

    // 13(b): "a1" + 62 non-BMP emoji -> 64 code points (UTF-16 length == 126) -> valid
    @Test
    fun `64 Unicode code points with 62 non-BMP emoji and UTF-16 length 126 is valid`() {
        val password = "a1" + emoji.repeat(62)
        assertEquals(126, password.length)
        assertEquals(64, PasswordPolicy.codePointLength(password))
        assertNull(PasswordPolicy.validateNewPassword(password))
    }

    // 13(c): "a1" + 63 non-BMP emoji -> 65 code points -> invalid (ERROR_NEW_TOO_LONG)
    @Test
    fun `65 Unicode code points with 63 non-BMP emoji is rejected as too long`() {
        val password = "a1" + emoji.repeat(63)
        assertEquals(128, password.length)
        assertEquals(65, PasswordPolicy.codePointLength(password))
        assertEquals(PasswordPolicy.ERROR_NEW_TOO_LONG, PasswordPolicy.validateNewPassword(password))
    }

    // 13(d): 7 code points -> invalid (ERROR_NEW_TOO_SHORT)
    @Test
    fun `7 Unicode code points is rejected as too short`() {
        val ascii7 = "Abcde12"
        assertEquals(7, PasswordPolicy.codePointLength(ascii7))
        assertEquals(PasswordPolicy.ERROR_NEW_TOO_SHORT, PasswordPolicy.validateNewPassword(ascii7))

        val emoji7 = "a1" + emoji.repeat(5)
        assertEquals(12, emoji7.length)
        assertEquals(7, PasswordPolicy.codePointLength(emoji7))
        assertEquals(PasswordPolicy.ERROR_NEW_TOO_SHORT, PasswordPolicy.validateNewPassword(emoji7))
    }

    // 14: Missing digit -> error; missing letter -> error; 8 and 64 ASCII chars with letter + digit -> valid;
    // leading/trailing spaces are not trimmed and count toward code point length
    @Test
    fun `missing digit or missing letter is rejected and 8 or 64 ASCII chars with letter and digit are valid`() {
        assertEquals(
            PasswordPolicy.ERROR_NEW_COMPLEXITY,
            PasswordPolicy.validateNewPassword("OnlyLetters")
        )
        assertEquals(
            PasswordPolicy.ERROR_NEW_COMPLEXITY,
            PasswordPolicy.validateNewPassword("12345678")
        )

        val valid8 = "Abcdefg1"
        assertEquals(8, PasswordPolicy.codePointLength(valid8))
        assertNull(PasswordPolicy.validateNewPassword(valid8))

        val valid64 = "A1" + "b".repeat(62)
        assertEquals(64, PasswordPolicy.codePointLength(valid64))
        assertNull(PasswordPolicy.validateNewPassword(valid64))
    }

    @Test
    fun `leading and trailing whitespace is preserved and counted toward length`() {
        // 6 visible chars + 2 spaces = 8 code points -> valid
        val padded8 = " a1bcde "
        assertEquals(8, PasswordPolicy.codePointLength(padded8))
        assertNull(PasswordPolicy.validateNewPassword(padded8))

        // 64 visible chars + 1 trailing space = 65 code points -> too long
        val padded65 = "a1" + "b".repeat(62) + " "
        assertEquals(65, PasswordPolicy.codePointLength(padded65))
        assertEquals(PasswordPolicy.ERROR_NEW_TOO_LONG, PasswordPolicy.validateNewPassword(padded65))

        // Whitespace-only password of length 8 is not trimmed to empty; fails complexity instead of required
        val spaces8 = "        "
        assertEquals(8, PasswordPolicy.codePointLength(spaces8))
        assertEquals(PasswordPolicy.ERROR_NEW_COMPLEXITY, PasswordPolicy.validateNewPassword(spaces8))
    }

    // 15: Boundary cases for letter/digit per backend ASCII definition (A1 B-3(a), A2 S10)
    @Test
    fun `non-ASCII letters or non-ASCII digits do not satisfy ASCII letter and digit rules`() {
        // Cyrillic letters + ASCII digit -> rejected (missing ASCII letter)
        assertEquals(
            PasswordPolicy.ERROR_NEW_COMPLEXITY,
            PasswordPolicy.validateNewPassword("ЖЗЙКЛМН1")
        )
        // ASCII letters + Arabic-Indic digit (\u0663 = '٣') -> rejected (missing ASCII digit)
        assertEquals(
            PasswordPolicy.ERROR_NEW_COMPLEXITY,
            PasswordPolicy.validateNewPassword("Abcdefgh\u0663")
        )
        // Boundary ASCII letters ('A', 'Z', 'a', 'z') and digits ('0', '9') -> valid
        assertNull(PasswordPolicy.validateNewPassword("AZaz09!@"))
    }
}
