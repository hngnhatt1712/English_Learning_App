package com.nhom21.english_learning_app.features.auth

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nhom21.english_learning_app.domain.model.Level
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SelectLevelScreenTest {
    @get:Rule val compose = createComposeRule()
    private val levelId = "10000000-0000-0000-0000-000000000001"
    private val level = Level(levelId, "Backend name", "Backend description", 1)

    @Test
    fun continueWithoutSelectionIsDisabled() {
        compose.setContent { SelectLevelScreen(uiState = SelectLevelUiState(levels = listOf(level))) }
        compose.onNodeWithText("Continue").assertIsNotEnabled()
    }

    @Test
    fun loadingDisablesSubmission() {
        compose.setContent { SelectLevelScreen(uiState = SelectLevelUiState(isLoading = true)) }
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithText("Đang tải trình độ…").assertExists()
    }

    @Test
    fun selectedCardExposesSelectionAndContinueOnlyCallsSubmit() {
        var submissions = 0
        compose.setContent {
            SelectLevelScreen(
                uiState = SelectLevelUiState(levels = listOf(level), selectedLevelId = levelId),
                onContinueClick = { submissions++ }
            )
        }
        compose.onNodeWithText("Backend name").assertIsSelected()
        compose.onNodeWithText("Continue").performClick()
        assertEquals(1, submissions)
    }

    @Test
    fun submissionDisablesChangingSelection() {
        var selections = 0
        compose.setContent {
            SelectLevelScreen(
                uiState = SelectLevelUiState(levels = listOf(level), selectedLevelId = levelId, isSubmitting = true),
                onLevelSelect = { selections++ }
            )
        }
        compose.onNodeWithText("Backend name").assertIsNotEnabled()
        assertEquals(0, selections)
    }

    @Test
    fun failureRetainsSelectionAndAllowsRetry() {
        var retries = 0
        compose.setContent {
            SelectLevelScreen(
                uiState = SelectLevelUiState(levels = listOf(level), selectedLevelId = levelId, errorMessage = "Thử lại yêu cầu"),
                onRetry = { retries++ }
            )
        }
        compose.onNodeWithText("Thử lại yêu cầu").assertExists()
        compose.onNodeWithText("Backend name").assertIsSelected()
        compose.onNodeWithText("Thử lại").performClick()
        assertEquals(1, retries)
    }
}
