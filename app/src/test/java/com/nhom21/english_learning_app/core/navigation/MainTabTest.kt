package com.nhom21.english_learning_app.core.navigation

import androidx.compose.ui.graphics.toArgb
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.core.ui.components.OwlaBottomBarDefaults
import com.nhom21.english_learning_app.core.ui.components.iconRes
import com.nhom21.english_learning_app.ui.theme.OwlaColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainTabTest {
    @Test
    fun `exactly four tabs have stable distinct destinations in display order`() {
        assertEquals(listOf("HOME", "LESSON", "PRACTICE", "PROFILE"), MainTab.entries.map { it.name })
        assertEquals(listOf(Home, Lesson, Practice, Profile), MainTab.entries.map { it.destination })
        assertEquals(4, MainTab.entries.map { it.destination }.distinct().size)
    }

    @Test
    fun `every main destination resolves to its selected tab`() {
        MainTab.entries.forEach { tab ->
            assertEquals(tab, mainTabMatching { it == tab.destination::class })
        }
    }

    @Test
    fun `Auth graph and all existing Auth destinations never display bottom bar`() {
        listOf(AuthGraph, Login, Signup, SelectLevel, WelcomeSuccess, ForgotPassword).forEach { route ->
            assertNull(mainTabMatching { it == route::class })
        }
    }

    @Test
    fun `Main graph membership alone does not select a tab`() {
        assertNull(mainTabMatching { it == MainGraph::class })
    }

    @Test
    fun `future Splash Round and Result classes default to no bottom bar`() {
        listOf(Splash::class, Round::class, RoundResult::class).forEach { routeClass ->
            assertNull(mainTabMatching { it == routeClass })
        }
    }

    @Test
    fun `tabs keep their original artwork resources in display order`() {
        assertEquals(
            listOf(R.drawable.ic_home, R.drawable.ic_lesson, R.drawable.ic_practice, R.drawable.ic_profile),
            MainTab.entries.map { it.iconRes }
        )
    }

    @Test
    fun `selected label uses Deep Teal on light and original Primary on dark`() {
        assertEquals(0xFF036268.toInt(), OwlaBottomBarDefaults.SelectedColor.toArgb())
        assertEquals(OwlaColors.PrimaryBase, OwlaBottomBarDefaults.DarkSelectedColor)
    }

    private class Splash
    private data class Round(val roundId: String)
    private data class RoundResult(val roundId: String)
}
