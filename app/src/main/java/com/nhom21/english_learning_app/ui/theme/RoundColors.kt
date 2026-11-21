package com.nhom21.english_learning_app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

internal data class RoundColors(
    val background: Color,
    val surface: Color,
    val ink: Color,
    val accentInk: Color,
    val border: Color,
    val primaryFace: Color,
    val primaryBase: Color,
    val primaryLabel: Color,
    val successInk: Color
)

@Composable
internal fun roundColors(): RoundColors {
    val dark = isSystemInDarkTheme()
    val background = if (dark) OwlaColors.DarkBackground else OwlaColors.LightBackground
    val surface = if (dark) OwlaColors.DarkSurface else OwlaColors.LightSurface
    val ink = if (dark) OwlaColors.DarkTextPrimary else OwlaColors.LightTextPrimary
    return RoundColors(
        background = lerp(background, OwlaColors.PrimaryBase, if (dark) 0.05f else 0.07f),
        surface = surface,
        ink = ink,
        accentInk = if (dark) ink else lerp(OwlaColors.PrimaryShadow, ink, 0.42f),
        border = if (dark) OwlaColors.DarkBorder else OwlaColors.LightBorder,
        primaryFace = OwlaColors.PrimaryBase,
        primaryBase = OwlaColors.PrimaryShadow,
        primaryLabel = OwlaColors.LightTextPrimary,
        successInk = if (dark) OwlaColors.SuccessBase else lerp(OwlaColors.SuccessShadow, ink, 0.36f)
    )
}
