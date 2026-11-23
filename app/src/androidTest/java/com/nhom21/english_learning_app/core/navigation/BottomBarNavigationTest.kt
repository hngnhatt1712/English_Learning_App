package com.nhom21.english_learning_app.core.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** Uses the real shell/graph/tab commands with offline screen slots. */
class BottomBarNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun tabsStaySingleRestoreStateAndLogoutClearsMainHistory() {
        lateinit var nav: NavHostController
        compose.setContent {
            nav = rememberNavController()
            MainScaffold(nav) { padding ->
                NavHost(nav, startDestination = MainGraph, modifier = Modifier.padding(padding)) {
                    authGraph(
                        login = { Text("Auth Login") }, signup = {}, selectLevel = {},
                        welcomeSuccess = {}, forgotPassword = {}
                    )
                    mainGraph { tab, _ ->
                        var count by rememberSaveable { mutableIntStateOf(0) }
                        Column {
                            Text("${tab.name} count $count")
                            Button(onClick = { count++ }) { Text("Increment") }
                            Button(onClick = { nav.applyCommand(AppNavTransitions.onLogout()) }) { Text("Logout") }
                        }
                    }
                }
            }
        }
        fun select(label: String) {
            compose.onNode(hasText(label) and isSelectable()).performClick().assertIsSelected()
            compose.onAllNodes(hasTestTag("OwlaBottomBar")).assertCountEquals(1)
        }
        select("Home")
        select("Lesson")
        compose.onNodeWithText("Increment").performClick()
        select("Practice")
        select("Profile")
        repeat(3) { select("Profile") }
        compose.runOnIdle { nav.popBackStack() }
        compose.onNode(hasText("Home") and isSelectable()).assertIsSelected()
        select("Lesson")
        compose.onNodeWithText("LESSON count 1").assertExists()
        select("Practice")
        select("Profile")
        select("Home")
        select("Profile")
        compose.onNodeWithText("Logout").performClick()
        compose.onNodeWithText("Auth Login").assertExists()
        compose.onAllNodes(hasTestTag("OwlaBottomBar")).assertCountEquals(0)
        compose.runOnIdle { assertFalse(nav.popBackStack()) }
        compose.onAllNodes(isSelectable()).assertCountEquals(0)
    }
}
