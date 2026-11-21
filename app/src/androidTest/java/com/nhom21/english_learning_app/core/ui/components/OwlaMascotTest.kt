package com.nhom21.english_learning_app.core.ui.components

import android.graphics.Bitmap
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nhom21.english_learning_app.ui.theme.OwlaColors
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OwlaMascotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun disabledAnimationRemainsStaticAndExpressionChangeUpdatesTheVector() {
        val expression = mutableStateOf(OwlaExpression.Idle)
        compose.setContent {
            OwlaMascot(expression.value, Modifier.size(200.dp).testTag("mascot"), "Owla", false)
        }
        compose.onNodeWithContentDescription("Owla").assertExists()
        val idle = compose.onNodeWithTag("mascot").captureToImage()
        save("idle", idle)
        compose.mainClock.advanceTimeBy(10_000)
        assertEquals(fingerprint(idle), fingerprint(compose.onNodeWithTag("mascot").captureToImage()))
        compose.runOnIdle { expression.value = OwlaExpression.Think }
        val think = compose.onNodeWithTag("mascot").captureToImage()
        save("think", think)
        assertTrue("Think must visibly differ from Idle", fingerprint(idle) != fingerprint(think))
    }

    @Test
    fun blinksCoverBothIrisesAndReopen() {
        compose.mainClock.autoAdvance = false
        compose.setContent { OwlaMascot(modifier = Modifier.size(200.dp).testTag("mascot")) }
        compose.waitForIdle()
        val baseline = orangeEyePixels(compose.onNodeWithTag("mascot").captureToImage())
        assertTrue("Open eyes have orange irises", baseline > 100)
        compose.mainClock.advanceTimeBy(2950)
        var minimum = baseline
        var reopened = false
        // Random rest is 3-5 seconds; sample through the entire first blink.
        repeat(145) {
            compose.mainClock.advanceTimeBy(16)
            val frame = compose.onNodeWithTag("mascot").captureToImage()
            val count = orangeEyePixels(frame)
            if (count < minimum) {
                minimum = count
                if (minimum < baseline / 10) save("blink-closed", frame)
            }
            if (minimum < baseline / 10 && count > baseline * 0.8) reopened = true
        }
        assertTrue("Both irises must disappear under closed lids: $minimum / $baseline", minimum < baseline / 10)
        assertTrue("Eyes must reopen after the blink", reopened)
    }

    @Test
    fun pausedHostStopsAnimationAndSystemScaleZeroKeepsStaticThink() {
        compose.mainClock.autoAdvance = false
        lateinit var host: LifecycleOwner
        lateinit var registry: LifecycleRegistry
        compose.runOnUiThread {
            host = object : LifecycleOwner {
                override val lifecycle: Lifecycle get() = registry
            }
            registry = LifecycleRegistry(host).apply { currentState = Lifecycle.State.RESUMED }
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides host) {
                OwlaMascot(OwlaExpression.Think, Modifier.size(160.dp).testTag("mascot"))
            }
        }
        compose.mainClock.advanceTimeBy(800)
        compose.runOnUiThread { registry.currentState = Lifecycle.State.STARTED }
        compose.mainClock.advanceTimeBy(32)
        val paused = fingerprint(compose.onNodeWithTag("mascot").captureToImage())
        compose.mainClock.advanceTimeBy(10_000)
        assertEquals(paused, fingerprint(compose.onNodeWithTag("mascot").captureToImage()))

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val oldScale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        fun setScale(value: Float) {
            instrumentation.uiAutomation.executeShellCommand("settings put global animator_duration_scale $value").close()
            compose.waitUntil(5000) {
                Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == value
            }
        }
        try {
            setScale(0f)
            compose.runOnUiThread { registry.currentState = Lifecycle.State.RESUMED }
            compose.mainClock.advanceTimeBy(32)
            val reduced = fingerprint(compose.onNodeWithTag("mascot").captureToImage())
            compose.mainClock.advanceTimeBy(10_000)
            assertEquals(reduced, fingerprint(compose.onNodeWithTag("mascot").captureToImage()))
            save("think-reduced-motion", compose.onNodeWithTag("mascot").captureToImage())
        } finally {
            setScale(oldScale)
        }
    }

    @Test
    fun allFallbacksAndHeadRenderAtSmallAndLargeSizes() {
        val dark = mutableStateOf(false)
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                Column(
                    Modifier.testTag("gallery").background(
                        if (dark.value) OwlaColors.DarkBackground else OwlaColors.LightBackground,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OwlaExpression.entries.chunked(3).forEach { row ->
                        Row {
                            row.forEach { OwlaMascot(it, Modifier.size(96.dp), animationsEnabled = false) }
                        }
                    }
                    Row {
                        OwlaMascot(modifier = Modifier.size(32.dp), animationsEnabled = false)
                        OwlaHeaderLogo(Modifier.size(48.dp).testTag("head"), "Logo Owla")
                        OwlaMascot(OwlaExpression.Think, Modifier.size(200.dp), animationsEnabled = false)
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Logo Owla").assertExists()
        save("gallery", compose.onNodeWithTag("gallery").captureToImage())
        save("head", compose.onNodeWithTag("head").captureToImage())
        compose.runOnIdle { dark.value = true }
        save("gallery-dark", compose.onNodeWithTag("gallery").captureToImage())
    }

    private fun fingerprint(image: ImageBitmap): Int {
        val pixels = image.toPixelMap()
        var hash = 1
        for (y in 0 until image.height) for (x in 0 until image.width) {
            hash = 31 * hash + pixels[x, y].hashCode()
        }
        return hash
    }

    private fun orangeEyePixels(image: ImageBitmap): Int {
        val pixels = image.toPixelMap()
        var count = 0
        for (y in (image.height * 0.2).toInt() until (image.height * 0.43).toInt()) {
            for (x in (image.width * 0.15).toInt() until (image.width * 0.85).toInt()) {
                if (x > image.width * 0.45 && x < image.width * 0.55) continue // Exclude beak.
                val c = pixels[x, y]
                if (c.red > 0.7f && c.green in 0.3f..0.8f && c.blue < 0.18f) count++
            }
        }
        return count
    }

    private fun save(name: String, image: ImageBitmap) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "owla-qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
