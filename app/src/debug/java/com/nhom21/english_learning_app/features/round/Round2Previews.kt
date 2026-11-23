package com.nhom21.english_learning_app.features.round

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.features.round.components.RoundExitDialogContent
import com.nhom21.english_learning_app.ui.theme.English_Learning_AppTheme

private fun previewQuestion(index: Int) =
    Round2UiState.Question(round2DemoItems[index].question, index + 1, round2DemoItems.size)

@Composable
private fun PreviewRound2(state: Round2UiState) {
    English_Learning_AppTheme(dynamicColor = false) {
        Round2Screen(state, onSelect = { _, _ -> }, onCheck = {}, onNext = {}, onAudio = {}, onRetry = {}, onExit = {})
    }
}

@Preview(name = "Choose the meaning", widthDp = 360, heightDp = 680)
@Composable
private fun MeaningPreview() = PreviewRound2(previewQuestion(0))

@Preview(name = "Choose the word · selected", widthDp = 360, heightDp = 680)
@Composable
private fun WordSelectedPreview() {
    val state = previewQuestion(3)
    PreviewRound2(state.copy(selectedOptionId = state.question.options.first().id))
}

@Preview(name = "Choose the picture", widthDp = 360, heightDp = 680)
@Composable
private fun PicturePreview() = PreviewRound2(previewQuestion(5))

@Preview(name = "Choose the picture · selected", widthDp = 360, heightDp = 680)
@Preview(name = "Choose the picture · dark", widthDp = 360, heightDp = 680, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PictureSelectedPreview() {
    val state = previewQuestion(5)
    PreviewRound2(state.copy(selectedOptionId = state.question.options.first().id))
}

@Preview(name = "Correct feedback", widthDp = 360, heightDp = 680)
@Composable
private fun CorrectPreview() {
    val item = round2DemoItems[0]
    PreviewRound2(previewQuestion(0).copy(
        selectedOptionId = item.correctOptionId,
        feedback = Round2Feedback(true, item.correctOptionId)
    ))
}

@Preview(name = "Wrong feedback", widthDp = 360, heightDp = 680)
@Preview(name = "Wrong feedback · 130% font", widthDp = 320, heightDp = 560, fontScale = 1.3f)
@Composable
private fun WrongPreview() {
    val item = round2DemoItems[0]
    PreviewRound2(previewQuestion(0).copy(
        selectedOptionId = item.question.options.first { it.id != item.correctOptionId }.id,
        feedback = Round2Feedback(false, item.correctOptionId)
    ))
}

@Preview(name = "Loading", widthDp = 360, heightDp = 680)
@Composable
private fun LoadingPreview() = PreviewRound2(Round2UiState.Loading)

@Preview(name = "Exit dialog", widthDp = 360, heightDp = 680)
@Composable
private fun ExitPreview() {
    English_Learning_AppTheme(dynamicColor = false) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PreviewRound2(previewQuestion(5))
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.35f)))
            Box(Modifier.padding(24.dp).widthIn(max = 360.dp)) {
                RoundExitDialogContent(
                body = stringResource(R.string.round_leave_default),
                    title = stringResource(R.string.round_leave_title),
                    keepLearningLabel = stringResource(R.string.round_keep_learning),
                    leaveLabel = stringResource(R.string.round_leave),
                    onKeepLearning = {},
                    onLeave = {}
                )
            }
        }
    }
}

@Preview(name = "Completed", widthDp = 360, heightDp = 680)
@Composable
private fun CompletePreview() = PreviewRound2(Round2UiState.Completed(8, 10, 2, true))

@Preview(name = "Completed · try again", widthDp = 360, heightDp = 680)
@Composable
private fun RetryPreview() = PreviewRound2(Round2UiState.Completed(6, 10, 0, false))
