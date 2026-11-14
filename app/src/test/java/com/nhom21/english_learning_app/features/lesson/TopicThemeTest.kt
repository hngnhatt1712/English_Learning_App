package com.nhom21.english_learning_app.features.lesson

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicThemeTest {
    private val expected = mapOf(
        "family" to Color(0xFFFFD166),
        "daily" to Color(0xFFFF70A6),
        "food" to Color(0xFF06D6A0),
        "travel" to Color(0xFF4EA8DE),
        "work" to Color(0xFFA663CC),
        "health" to Color(0xFF70E000),
        "entertainment" to Color(0xFFFF4D6D),
        "tech" to Color(0xFF00F5D4),
        "environment" to Color(0xFFE9FF70),
    )

    @Test
    fun allOfficialKeysUseExactLightColorAndReadableContent() {
        expected.forEach { (key, color) ->
            val theme = TopicThemes.resolve(key, false)
            assertEquals(color, theme.colors.accent)
            assertTrue(contrast(theme.colors.onAccent, theme.colors.accent) >= 3f)
            assertTrue(theme.colors.accentDeep.luminance() < theme.colors.accent.luminance())
        }
    }

    @Test
    fun unknownKeysResolveToDefaultInBothThemes() {
        listOf(null, "", "xyz").forEach { key ->
            assertEquals("default", TopicThemes.resolve(key, false).key)
            assertEquals("default", TopicThemes.resolve(key, true).key)
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val light = maxOf(a.luminance(), b.luminance())
        val dark = minOf(a.luminance(), b.luminance())
        return (light + .05f) / (dark + .05f)
    }
}
