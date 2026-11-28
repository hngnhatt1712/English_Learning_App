package com.nhom21.english_learning_app.features.round

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nhom21.english_learning_app.domain.model.QuestionContent
import com.nhom21.english_learning_app.domain.model.QuestionResult
import com.nhom21.english_learning_app.domain.model.RoundResult
import com.nhom21.english_learning_app.domain.model.RoundUnlocks
import com.nhom21.english_learning_app.domain.model.Streak
import com.nhom21.english_learning_app.ui.theme.English_Learning_AppTheme

@Composable
private fun Round4Preview(state: Round4UiState) {
    English_Learning_AppTheme(dynamicColor = false) {
        val artwork = rememberRound3DemoArtwork()
        Round4Screen(
            state, onToken = { _, _ -> }, onText = { _, _ -> }, onMode = {},
            onCheck = {}, onRetryAnswer = {}, onNext = {}, onRetry = {}, onExit = {},
            imageContent = { key, modifier -> Round3DemoImage(artwork, key, modifier) },
        )
    }
}

@Preview(name = "Fill Blank · Thẻ từ", widthDp = 382, heightDp = 720)
@Composable
private fun Round4FillPreview() = Round4Preview(Round4UiState.Question(round4Fill, 1, 3))

@Preview(name = "Fill Blank · Bàn phím", widthDp = 382, heightDp = 720)
@Composable
private fun Round4FillKeyboardPreview() = Round4Preview(
    Round4UiState.Question(round4Fill.copy(content = QuestionContent.FillBlank("These are ___.", null)), 1, 3, draft = "grapes")
)

@Preview(name = "Translate · Thẻ từ", widthDp = 382, heightDp = 720)
@Composable
private fun Round4TranslatePreview() = Round4Preview(
    Round4UiState.Question(round4Translate, 2, 3, selectedIds = listOf("i", "like", "fruit"))
)

@Preview(name = "Translate · Bàn phím", widthDp = 382, heightDp = 720)
@Composable
private fun Round4KeyboardPreview() = Round4Preview(
    Round4UiState.Question(round4Translate, 2, 3, keyboard = true, draft = "I like strawberries.")
)

@Preview(name = "Sentence Order", widthDp = 382, heightDp = 720)
@Composable
private fun Round4OrderPreview() = Round4Preview(
    Round4UiState.Question(round4Order, 3, 3, selectedIds = listOf("we", "eat"))
)

@Preview(name = "Phản hồi đúng", widthDp = 382, heightDp = 720)
@Composable
private fun Round4CorrectPreview() = Round4Preview(
    Round4UiState.Question(round4Order, 3, 3, selectedIds = listOf("we", "eat", "them", "after", "lunch"),
        feedback = QuestionResult("order", true, null, null))
)

@Preview(name = "Phản hồi sai", widthDp = 382, heightDp = 720)
@Composable
private fun Round4WrongPreview() = Round4Preview(
    Round4UiState.Question(round4Order, 3, 3, selectedIds = listOf("we", "eat", "lunch", "them", "after"),
        feedback = QuestionResult("order", false, null, null))
)

@Preview(name = "Đang kiểm tra", widthDp = 382, heightDp = 720)
@Composable
private fun Round4CheckingPreview() = Round4Preview(
    Round4UiState.Question(round4Order, 3, 3,
        selectedIds = listOf("we", "eat", "them", "after", "lunch"), checking = true)
)

@Preview(name = "Đang tải bài", widthDp = 382, heightDp = 720)
@Composable
private fun Round4LoadingPreview() = Round4Preview(Round4UiState.Loading)

private fun resultPreview(passed: Boolean) = Round4UiState.Completed(
    RoundResult(
        roundId = "round4-preview",
        totalQuestions = 8,
        correctCount = if (passed) 7 else 5,
        scorePercent = if (passed) 87.5 else 62.5,
        passed = passed,
        passThresholdPercent = 70,
        stars = if (passed) 2 else 0,
        bestStars = if (passed) 2 else 0,
        bestScorePercent = if (passed) 87.5 else 62.5,
        isNewBest = true,
        attempts = 1,
        results = emptyList(),
        unlocks = RoundUnlocks(null, false, null, false, null),
        streak = Streak(0, null, false),
    ),
    resultId = 1,
)

@Preview(name = "Hoàn thành · Đạt", widthDp = 382, heightDp = 720)
@Composable
private fun Round4PassedPreview() = Round4Preview(resultPreview(true))

@Preview(name = "Hoàn thành · Thử lại", widthDp = 382, heightDp = 720)
@Composable
private fun Round4FailedPreview() = Round4Preview(resultPreview(false))

