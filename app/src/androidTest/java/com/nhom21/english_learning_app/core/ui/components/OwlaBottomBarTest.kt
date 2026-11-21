package com.nhom21.english_learning_app.core.ui.components

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.nhom21.english_learning_app.core.navigation.MainTab
import com.nhom21.english_learning_app.ui.theme.OwlaColors
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OwlaBottomBarTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun fourTabsExposeSelectionAndClickCallbacks() {
        compose.setContent {
            var selected by remember { mutableStateOf(MainTab.HOME) }
            OwlaBottomBar(selected, { selected = it })
        }
        compose.onAllNodes(androidx.compose.ui.test.hasTestTag("OwlaBottomBar")).assertCountEquals(1)
        compose.onAllNodes(isSelectable()).assertCountEquals(4)
        tab("Home").assertIsSelected()
        listOf("Lesson", "Practice", "Profile", "Home").forEach { label ->
            tab(label).performClick().assertIsSelected()
            listOf("Home", "Lesson", "Practice", "Profile").filter { it != label }.forEach {
                tab(it).assertIsNotSelected()
            }
        }
    }

    @Test
    fun lightAtNarrowWidthAndLargeFontKeepsAllTargetsReadable() = assertLargeFontTargets(dark = false)

    @Test
    fun darkAtNarrowWidthAndLargeFontKeepsAllTargetsReadable() = assertLargeFontTargets(dark = true)

    @Test
    fun practiceBadgeFollowsParameter() {
        val notification = mutableStateOf(false)
        compose.setContent { OwlaBottomBar(MainTab.HOME, {}, hasPracticeNotification = notification.value) }
        val badge = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Có từ cần ôn")
        compose.onAllNodes(badge).assertCountEquals(0)
        compose.runOnIdle { notification.value = true }
        compose.onNode(badge and hasText("Practice")).assertIsDisplayed()
        compose.runOnIdle { notification.value = false }
        compose.onAllNodes(badge).assertCountEquals(0)
    }

    @Test
    fun lightSelectionsKeepOriginalArtworkAndFlatSurface() = assertArtwork(dark = false)

    @Test
    fun darkSelectionsKeepOriginalArtworkAndFlatSurface() = assertArtwork(dark = true)

    private fun assertArtwork(dark: Boolean) {
        val selected = mutableStateOf(MainTab.HOME)
        val surface = if (dark) OwlaColors.DarkSurface else OwlaColors.LightSurface
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                Column(Modifier.width(320.dp)) {
                    OwlaBottomBar(selected.value, { selected.value = it })
                    // Independent untinted source images are the artwork reference.
                    Row {
                        MainTab.entries.forEach { tab ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Image(
                                    painterResource(tab.iconRes), null,
                                    Modifier.size(26.dp).background(surface).testTag("source-${tab.name}")
                                )
                            }
                        }
                    }
                }
            }
        }
        MainTab.entries.forEach { selectedTab ->
            compose.runOnIdle { selected.value = selectedTab }
            tab(selectedTab.label).assertIsSelected()
            MainTab.entries.forEach { tab ->
                val actual = tab(tab.label).captureToImage().asAndroidBitmap()
                val expected = compose.onNodeWithTag("source-${tab.name}").captureToImage().asAndroidBitmap()
                val left = (actual.width - expected.width) / 2
                val top = with(compose.density) { 4.dp.roundToPx() }
                assertEquals("No selected card: ${tab.name}", surface.toArgb(), actual.getPixel(actual.width / 2, 2))
                // Compare average RGB to tolerate subpixel centering, but catch icon tint/alpha.
                for (shift in listOf(16, 8, 0)) {
                    fun average(bitmap: Bitmap, x: Int, y: Int): Double {
                        var sum = 0L
                        for (dy in 0 until expected.height) for (dx in 0 until expected.width) {
                            sum += (bitmap.getPixel(x + dx, y + dy) shr shift) and 255
                        }
                        return sum.toDouble() / (expected.width * expected.height)
                    }
                    assertEquals("Original ${tab.name} channel $shift", average(expected, 0, 0), average(actual, left, top), 5.0)
                }
            }
            val directory = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
            File(directory, "bottom-bar-${if (dark) "dark" else "light"}-${selectedTab.name}.png")
                .outputStream().use {
                    compose.onNodeWithTag("OwlaBottomBar").captureToImage().asAndroidBitmap()
                        .compress(Bitmap.CompressFormat.PNG, 100, it)
                }
        }
    }

    private fun assertLargeFontTargets(dark: Boolean) {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)
            ) {
                OwlaBottomBar(MainTab.PROFILE, {}, Modifier.width(320.dp), hasPracticeNotification = true)
            }
        }
        listOf("Home", "Lesson", "Practice", "Profile").forEach { label ->
            val node = tab(label).assertIsDisplayed().fetchSemanticsNode()
            assertTrue("$label touch width", node.size.width >= with(compose.density) { 48.dp.toPx() })
            assertTrue("$label touch height", node.size.height >= with(compose.density) { 48.dp.toPx() })
        }
        tab("Profile").assertIsSelected()
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        File(directory, "navigation-bar-${if (dark) "dark" else "light"}-font130.png").outputStream().use {
            compose.onNodeWithTag("OwlaBottomBar").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun tab(label: String) = compose.onNode(hasText(label) and isSelectable())
}
