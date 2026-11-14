package com.nhom21.english_learning_app.features.lesson

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.nhom21.english_learning_app.core.ui.components.PathNodeColors
import com.nhom21.english_learning_app.ui.theme.OwlaColors

@Immutable
data class TopicThemeColors(
    val accent: Color,
    val accentDeep: Color,
    val onAccent: Color,
    val tint: Color,
    val ringTrack: Color,
)

@Immutable
data class TopicDecorItem(val icon: ImageVector, val sizeDp: Int, val rotationDeg: Float)

@Immutable
data class TopicTheme(val key: String, val colors: TopicThemeColors, val decor: List<TopicDecorItem>)

object TopicThemes {
    private data class Definition(val color: Color, val icon: ImageVector)

    private val definitions = linkedMapOf(
        "family" to Definition(Color(0xFFFFD166), Icons.Rounded.Home),
        "daily" to Definition(Color(0xFFFF70A6), Icons.Rounded.Schedule),
        "food" to Definition(Color(0xFF06D6A0), Icons.Rounded.Restaurant),
        "travel" to Definition(Color(0xFF4EA8DE), Icons.Rounded.Flight),
        "work" to Definition(Color(0xFFA663CC), Icons.Rounded.Work),
        "health" to Definition(Color(0xFF70E000), Icons.Rounded.Favorite),
        "entertainment" to Definition(Color(0xFFFF4D6D), Icons.Rounded.Movie),
        "tech" to Definition(Color(0xFF00F5D4), Icons.Rounded.Memory),
        "environment" to Definition(Color(0xFFE9FF70), Icons.Rounded.Eco),
    )

    private val defaultDefinition = Definition(OwlaColors.PrimaryBase, Icons.Rounded.Home)

    val Default: TopicTheme get() = create(key = "default", definition = defaultDefinition, isDark = false)

    fun resolve(themeKey: String?, isDark: Boolean): TopicTheme {
        val normalized = themeKey?.trim()?.lowercase().orEmpty()
        val definition = definitions[normalized]
        val key = normalized.takeIf { definition != null } ?: "default"
        return create(key = key, definition = definition ?: defaultDefinition, isDark = isDark)
    }

    private fun create(key: String, definition: Definition, isDark: Boolean): TopicTheme {
        val accent = if (isDark) lerp(definition.color, Color.Black, .12f) else definition.color
        val deep = lerp(accent, Color.Black, .28f)
        val ink = Color(0xFF1F2933)
        val onAccent = if (contrastRatio(Color.White, accent) >= contrastRatio(ink, accent)) {
            Color.White
        } else {
            ink
        }
        val alpha = if (isDark) .17f else .11f
        return TopicTheme(
            key = key,
            colors = TopicThemeColors(
                accent = accent,
                accentDeep = deep,
                onAccent = onAccent,
                tint = accent.copy(alpha = alpha),
                ringTrack = accent.copy(alpha = .24f),
            ),
            decor = listOf(
                TopicDecorItem(definition.icon, 48, -8f),
                TopicDecorItem(definition.icon, 42, 9f),
                TopicDecorItem(definition.icon, 54, -4f),
            ),
        )
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + .05f) / (darker + .05f)
    }
}

@Composable
fun rememberTopicTheme(themeKey: String?): TopicTheme {
    val dark = isSystemInDarkTheme()
    return remember(themeKey, dark) { TopicThemes.resolve(themeKey, dark) }
}

fun TopicThemeColors.toPathNodeColors() = PathNodeColors(
    face = accent,
    base = accentDeep,
    content = onAccent,
    ring = accent,
    ringTrack = ringTrack,
)

fun Modifier.topicBackground(theme: TopicTheme): Modifier = this.drawBehind {
    drawRect(theme.colors.tint)
}

@Composable
fun TopicDecorRow(
    theme: TopicTheme,
    rowIndex: Int,
    nodeOffsetSign: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (!enabled || theme.decor.isEmpty()) return
    val (icon, sizeDp, rotationDeg) = theme.decor[rowIndex.mod(theme.decor.size)]
    val placeLeft = if (nodeOffsetSign == 0) (rowIndex % 2 == 0) else (nodeOffsetSign > 0)
    Box(modifier.fillMaxSize().clearAndSetSemantics { }) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = theme.colors.accent.copy(alpha = .15f),
            modifier = Modifier
                .align(if (placeLeft) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 12.dp)
                .rotate(rotationDeg)
                .size(sizeDp.dp),
        )
    }
}

