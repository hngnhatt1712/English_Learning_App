package com.nhom21.english_learning_app.features.round

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RoundResultScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun starsPopLeftToRightAndOnlyReplayOnAnotherResultEntry() {
        val result = Round2UiState.Completed(10, 10, 3, passed = true, scorePercent = 100.0, resultId = 1)
        val state = mutableStateOf<Round2UiState>(result)
        val registry = mutableStateOf(SaveableStateRegistry(restoredValues = null) { true })
        val mounted = mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            if (mounted.value) {
                CompositionLocalProvider(LocalSaveableStateRegistry provides registry.value) {
                    Round2Screen(
                        state.value, onSelect = { _, _ -> }, onCheck = {}, onNext = {},
                        onAudio = {}, onRetry = {}, onExit = {},
                    )
                }
            }
        }

        compose.mainClock.advanceTimeByFrame()
        val small = goldPixelsByStar(3)
        compose.mainClock.advanceTimeBy(112)
        val first = goldPixelsByStar(3)
        compose.mainClock.advanceTimeBy(144)
        val second = goldPixelsByStar(3)
        compose.mainClock.advanceTimeBy(144)
        val third = goldPixelsByStar(3)
        compose.mainClock.advanceTimeBy(300)
        val settled = goldPixelsByStar(3)
        assertTrue("First star grows first: $small -> $first", first[0] > first[1] * 1.5f)
        assertTrue("Second star grows before the third: $second", second[1] > second[2] * 1.5f)
        assertTrue("Third star overshoots the resting size: $third -> $settled", third[2] > settled[2])

        compose.runOnIdle { state.value = result.copy(correctCount = 9) }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(settled, goldPixelsByStar(3))
        val saved = compose.runOnIdle {
            registry.value.performSave().also { mounted.value = false }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            registry.value = SaveableStateRegistry(saved) { true }
            mounted.value = true
        }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(settled, goldPixelsByStar(3))

        compose.runOnIdle { state.value = Round2UiState.Submitting() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("3 of 3 stars").assertDoesNotExist()
        compose.runOnIdle { state.value = result.copy(resultId = 2) }
        compose.mainClock.advanceTimeByFrame()
        assertTrue("A second submit with the same result starts small again", goldPixelsByStar(3)[0] < settled[0])
    }

    @Test
    fun roundOneAndRoundTwoKeepTheirResultContentAndActions() {
        val state = mutableStateOf<RoundUiState>(Round1UiState.Completed(9, 3))
        var continued = 0
        var retries = 0
        var exits = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            when (val current = state.value) {
                is Round1UiState -> Round1Screen(
                    current, onCardAction = { _, _ -> }, onUndo = {}, onAudio = {}, onFinish = {},
                    onRetry = {}, onExit = { exits++ }, onContinue = { continued++ },
                )
                is Round2UiState -> Round2Screen(
                    current, onSelect = { _, _ -> }, onCheck = {}, onNext = {}, onAudio = {},
                    onRetry = { retries++ }, onExit = { exits++ },
                )
            }
        }

        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("You learned 9 words.").assertExists()
        assertEquals(3, goldPixelsByStar(3).count { it > 0 })
        compose.onNodeWithText("Continue").performClick()
        compose.runOnIdle {
            assertEquals(1, continued)
            state.value = Round2UiState.Completed(0, 10, 0, passed = false, scorePercent = 0.0)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Chưa đạt · 0.0%").assertExists()
        assertEquals(listOf(0, 0, 0), goldPixelsByStar(0))
        compose.onNodeWithText("Thử lại").performClick()
        compose.runOnIdle { assertEquals(1, retries) }

        for (stars in 1..2) {
            compose.runOnIdle { state.value = Round2UiState.Submitting() }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                state.value = Round2UiState.Completed(8, 10, stars, passed = true, scorePercent = 81.5)
            }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithText("Đạt · 81.5%").assertExists()
            assertEquals(stars, goldPixelsByStar(stars).count { it > 0 })
        }
        compose.onNodeWithText("Về bài học").performClick()
        compose.runOnIdle { assertEquals(1, exits) }
    }

    private fun goldPixelsByStar(stars: Int): List<Int> {
        val image = compose.onNodeWithContentDescription("$stars of 3 stars").captureToImage().toPixelMap()
        val counts = IntArray(3)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val pixel = image[x, y]
                if (pixel.red > 0.8f && pixel.green > 0.42f && pixel.blue < 0.45f) {
                    counts[x * 3 / image.width]++
                }
            }
        }
        return counts.toList()
    }
}
