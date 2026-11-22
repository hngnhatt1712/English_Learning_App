package com.nhom21.english_learning_app.features.round

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.core.network.ApiErrorType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class Round2ScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun checkingAndNetworkRetryRetainSelectionAndFeedbackHasOneContinueAction() {
        val question = Round2Question(
            id = "question",
            variant = Round2Variant.Meaning,
            prompt = "book",
            options = listOf(Round2Option("wrong", "vở"), Round2Option("right", "sách")),
        )
        var state by mutableStateOf(Round2UiState.Question(question, 1, 2, savedStars = 2))
        var checks = 0
        var advances = 0
        compose.setContent {
            Box(Modifier.width(320.dp).height(480.dp)) {
                Round2Screen(
                    state = state,
                    onSelect = { _, option -> state = state.copy(selectedOptionId = option) },
                    onCheck = {
                        checks++
                        state = state.copy(checking = true, checkError = null)
                    },
                    onNext = { advances++ },
                    onAudio = {},
                    onRetry = {},
                    onExit = {},
                )
            }
        }

        compose.onNodeWithText("Câu 1/2").assertIsDisplayed()
        compose.onNodeWithText("2 sao").assertIsDisplayed()
        compose.onNodeWithText("Kiểm tra").assertIsNotEnabled()
        compose.onNodeWithText("vở").performClick().assertIsSelected()
        compose.onNodeWithText("Kiểm tra").assertIsEnabled().performClick()
        compose.onNodeWithText("Đang kiểm tra…").assertIsNotEnabled()
        compose.onNodeWithText("sách").assertIsNotEnabled()

        compose.runOnIdle {
            state = state.copy(checking = false, checkError = ApiErrorType.NETWORK, networkFailures = 1)
        }
        compose.onNodeWithText("Mất kết nối. Chạm để thử lại").assertIsDisplayed()
        compose.onNodeWithText("vở").assertIsSelected()
        compose.onNodeWithText("Thử lại").performClick()
        compose.runOnIdle {
            assertEquals(2, checks)
            state = state.copy(checking = false, feedback = Round2Feedback(false, "right"))
        }

        compose.onNodeWithText("Chưa đúng").assertIsDisplayed()
        compose.onNodeWithText("Đáp án đúng: sách").assertIsDisplayed()
        compose.onNodeWithText("Kiểm tra").assertDoesNotExist()
        compose.onNodeWithText("vở").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Close round").performClick()
        compose.onNodeWithText("Thoát bài này?").assertIsDisplayed()
        compose.onNodeWithText("Tiến độ round hiện tại sẽ không được lưu").assertIsDisplayed()
        compose.onNodeWithText("Ở lại").performClick()
        compose.onNodeWithText("Tiếp tục").assertIsDisplayed().performClick().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, advances) }
    }

    @Test
    fun pictureFeedbackUsesTheServerOptionAndOmitsUnrenderableAnswers() {
        val question = Round2Question(
            id = "picture",
            variant = Round2Variant.Picture,
            prompt = "Chọn hình",
            options = listOf(
                Round2Option("wrong", imageRes = R.drawable.ic_owla),
                Round2Option("right", imageRes = R.drawable.ic_star),
            ),
        )
        var state by mutableStateOf(
            Round2UiState.Question(
                question, 1, 1, selectedOptionId = "wrong", feedback = Round2Feedback(false, "right")
            )
        )
        compose.setContent {
            Round2Screen(state, onSelect = { _, _ -> }, onCheck = {}, onNext = {}, onAudio = {}, onRetry = {}, onExit = {})
        }

        compose.onNodeWithText("Đáp án đúng: Hình 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hình 2").assertIsNotEnabled()
        compose.runOnIdle {
            state = state.copy(question = question.copy(options = listOf(question.options[0], Round2Option("right"))))
        }
        compose.onNodeWithText("Đáp án đúng: Hình 2").assertDoesNotExist()
        compose.onNodeWithText("Chưa đúng").assertIsDisplayed()
        compose.onNodeWithText("Tiếp tục").assertIsDisplayed()
    }
}
