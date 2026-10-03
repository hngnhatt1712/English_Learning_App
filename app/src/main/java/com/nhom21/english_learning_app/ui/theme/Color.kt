package com.nhom21.english_learning_app.ui.theme

import androidx.compose.ui.graphics.Color

// Default template colors (backward compatibility)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

/**
 * Owla English — Design System Color Tokens (owla-design-system-skill.md)
 * Luôn dùng cặp base & shadow cùng nhau cho các phần tử bấm được (3D elevation).
 */
object OwlaColors {
    // Primary Teal
    val PrimaryBase = Color(0xFF1CB0A6)
    val PrimaryShadow = Color(0xFF148F87)

    // Accent Red-Orange
    val AccentBase = Color(0xFFFF7A50)
    val AccentShadow = Color(0xFFD65F3A)

    // Secondary Yellow
    val SecondaryBase = Color(0xFFFFC93C)
    val SecondaryShadow = Color(0xFFD9A319)

    // Success Green (đúng / pass)
    val SuccessBase = Color(0xFF58CC02)
    val SuccessShadow = Color(0xFF3F9200)

    // Danger Red (sai / fail / error)
    val DangerBase = Color(0xFFFF4B4B)
    val DangerShadow = Color(0xFFD6373A)

    // Background & Surface
    val LightBackground = Color(0xFFF7F9FA)
    val DarkBackground = Color(0xFF15202B)
    val DarkBackgroundAlt = Color(0xFF1A2634)

    val LightSurface = Color(0xFFFFFFFF)
    val DarkSurface = Color(0xFF22303C)

    // Neutral Text & Borders
    val LightTextPrimary = Color(0xFF1A2634)
    val DarkTextPrimary = Color(0xFFF7F9FA)

    val LightTextSecondary = Color(0xFF7B8B9A)
    val DarkTextSecondary = Color(0xFF8B98A5)

    val LightBorder = Color(0xFFE5E9EC)
    val DarkBorder = Color(0xFF2E3F50)
}