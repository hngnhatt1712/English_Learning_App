package com.nhom21.english_learning_app.features.round.components

import org.junit.Assert.assertEquals
import org.junit.Test

class Round2DisplayPromptTest {
    @Test
    fun extractsSeedTemplatesWithoutChangingContent() {
        assertEquals("home", round2DisplayPrompt("Chọn hình minh họa cho từ «home»."))
        assertEquals("sister", round2DisplayPrompt("Từ «sister» có nghĩa là gì?"))
        assertEquals("wake up", round2DisplayPrompt("Từ «wake up» có nghĩa là gì?"))
        assertEquals(" Wake  Up ", round2DisplayPrompt("Chọn hình minh họa cho từ « Wake  Up »."))
    }

    @Test
    fun preservesUnknownOrAmbiguousPrompts() {
        val prompts = listOf(
            "Chọn từ đúng cho nghĩa này",
            "wake up",
            "Từ «sister» có nghĩa là gì",
            "Từ «» có nghĩa là gì?",
            "Từ «home» hoặc «house» có nghĩa là gì?",
        )
        prompts.forEach { assertEquals(it, round2DisplayPrompt(it)) }
    }
}
