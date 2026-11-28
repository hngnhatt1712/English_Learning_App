package com.nhom21.english_learning_app.features.round

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.domain.model.MatchingItem
import com.nhom21.english_learning_app.domain.model.MatchingPair
import com.nhom21.english_learning_app.domain.model.RoundResult
import com.nhom21.english_learning_app.domain.model.RoundUnlocks
import com.nhom21.english_learning_app.domain.model.Streak
import com.nhom21.english_learning_app.features.round.components.MatchingExitPanel
import com.nhom21.english_learning_app.features.round.components.MatchingStatusCard
import com.nhom21.english_learning_app.ui.theme.English_Learning_AppTheme

private val fruitWords = listOf("Apple", "Banana", "Orange", "Pineapple", "Mango")
private val fruitMeanings = listOf("Táo", "Chuối", "Cam", "Dứa", "Xoài")
private val textBoard = MatchingQuestion(
    id = "fruit-text",
    prompt = "Ghép từ với nghĩa",
    left = fruitWords.mapIndexed { index, word -> MatchingItem("l$index", word, null) },
    right = listOf(2, 4, 0, 3, 1).map { index -> MatchingItem("r$index", fruitMeanings[index], null) },
)

private val pictureBoard = MatchingQuestion(
    id = "fruit-picture",
    prompt = "Ghép từ với hình",
    left = Round3DemoPictureWords.mapIndexed { index, (word, _) -> MatchingItem("l$index", word, null) },
    right = listOf(3, 4, 0, 2, 1).map { index ->
        MatchingItem("r$index", null, Round3DemoPictureWords[index].second)
    },
)

private val firstQuestion = Round3UiState.Question(
    question = textBoard, number = 1, questionCount = 2, totalQuestions = 10, savedStars = 0,
)
private val matchedQuestion = firstQuestion.copy(pairs = fruitWords.indices.map { MatchingPair("l$it", "r$it") })

@Composable
private fun Round3Preview(state: Round3UiState, transitioning: Boolean = false) {
    English_Learning_AppTheme(dynamicColor = false) {
        val artwork = rememberRound3DemoArtwork()
        Round3Screen(
            state = state,
            onSelectLeft = { _, _ -> },
            onSelectRight = { _, _ -> },
            onClearFeedback = { _, _ -> },
            onNext = {},
            onStartRepair = {},
            onFinishRepair = {},
            onRetry = {},
            onExit = {},
            topicName = "Trái cây",
            imageContent = { url, modifier -> Round3DemoImage(artwork, url, modifier) },
            previewTransition = transitioning,
        )
    }
}

@Preview(name = "Đang tải bài", widthDp = 382, heightDp = 720, showSystemUi = true)
@Preview(name = "Đang tải bài · Tối", widthDp = 382, heightDp = 720, showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Round3LoadingPreview() = Round3Preview(Round3UiState.Loading)

@Preview(name = "Chờ chọn từ", widthDp = 382, heightDp = 720, showSystemUi = true)
@Preview(name = "Chờ chọn từ · Tối", widthDp = 382, heightDp = 720, showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Chữ lớn", widthDp = 320, heightDp = 600, showSystemUi = true, fontScale = 1.3f)
@Composable
private fun Round3BoardPreview() = Round3Preview(firstQuestion)

@Preview(name = "Đã chọn từ", widthDp = 382, heightDp = 720, showSystemUi = true)
@Composable
private fun Round3SelectedPreview() = Round3Preview(firstQuestion.copy(selectedLeftId = "l0"))

@Preview(name = "Ghép sai", widthDp = 382, heightDp = 720, showSystemUi = true)
@Composable
private fun Round3WrongPreview() =
    Round3Preview(firstQuestion.copy(feedback = MatchingFeedback(1, MatchingPair("l0", "r1"), false)))

@Preview(name = "Ghép đúng và khóa", widthDp = 382, heightDp = 720, showSystemUi = true)
@Composable
private fun Round3MatchedPreview() = Round3Preview(matchedQuestion)

@Preview(name = "Đang chuyển câu", widthDp = 382, heightDp = 720, showSystemUi = true)
@Composable
private fun Round3TransitionPreview() = Round3Preview(matchedQuestion, transitioning = true)

@Preview(name = "Câu 2 · Ghép với hình ảnh", widthDp = 382, heightDp = 720, showSystemUi = true)
@Composable
private fun Round3PicturePreview() = Round3Preview(
    firstQuestion.copy(question = pictureBoard, number = 2, completedPairsBefore = 5)
)

@Preview(name = "Xác nhận thoát", widthDp = 382, heightDp = 720, showSystemUi = true)
@Preview(name = "Xác nhận thoát · Tối", widthDp = 382, heightDp = 720, showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Round3ExitPreview() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Round3Preview(firstQuestion)
        Box(Modifier.matchParentSize().background(Color(0xFF091518).copy(alpha = 0.73f)))
        Box(Modifier.padding(horizontal = 24.dp)) {
            MatchingExitPanel(repairing = false, onStay = {}, onLeave = {})
        }
    }
}

private fun resultPreview(passed: Boolean) = Round3UiState.Completed(
    result = RoundResult(
        roundId = "fruit",
        totalQuestions = 10,
        correctCount = if (passed) 8 else 6,
        scorePercent = if (passed) 80.0 else 60.0,
        passed = passed,
        passThresholdPercent = 70,
        stars = if (passed) 2 else 0,
        bestStars = if (passed) 2 else 0,
        bestScorePercent = if (passed) 80.0 else 60.0,
        isNewBest = true,
        attempts = 1,
        results = emptyList(),
        unlocks = RoundUnlocks(null, false, null, false, null),
        streak = Streak(0, null, false),
    ),
    resultId = 1,
    repairTotal = if (passed) 2 else 4,
)

@Preview(name = "Hoàn thành · Đạt", widthDp = 382, heightDp = 720, showSystemUi = true)
@Preview(name = "Hoàn thành · Đạt · Tối", widthDp = 382, heightDp = 720, showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Round3PassedPreview() = Round3Preview(resultPreview(true))

@Preview(name = "Hoàn thành · Thử lại", widthDp = 382, heightDp = 720, showSystemUi = true)
@Composable
private fun Round3FailedPreview() = Round3Preview(resultPreview(false))

@Preview(name = "Sửa lỗi", widthDp = 382, heightDp = 720, showSystemUi = true)
@Composable
private fun Round3RepairPreview() = Round3Preview(
    firstQuestion.copy(
        question = textBoard.copy(left = textBoard.left.take(1)),
        selectedLeftId = "l0",
        totalQuestions = 2,
        isRepair = true,
    )
)

@Preview(name = "Trạng thái tải và nộp bài", widthDp = 382, heightDp = 380)
@Composable
private fun Round3StatusPreview() {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MatchingStatusCard(R.string.round3_load_error, R.string.round3_load_error_body, R.string.round_retry) {}
        MatchingStatusCard(R.string.round3_submitting, R.string.round3_submitting_body, null) {}
        MatchingStatusCard(R.string.round3_submit_error, R.string.round3_submit_error_body, R.string.round3_submit_retry) {}
    }
}
