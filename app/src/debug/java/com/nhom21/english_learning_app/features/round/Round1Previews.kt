package com.nhom21.english_learning_app.features.round

import com.nhom21.english_learning_app.core.ui.components.VocabularyAudioState
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.features.round.components.RoundExitDialogContent
import com.nhom21.english_learning_app.features.round.components.RoundHeading
import com.nhom21.english_learning_app.features.round.components.RoundWordProgress
import com.nhom21.english_learning_app.core.ui.components.VocabularySwipeCard
import com.nhom21.english_learning_app.ui.theme.roundColors
import com.nhom21.english_learning_app.ui.theme.English_Learning_AppTheme

private fun demoCard(word: Round1Word = round1DemoWords.first(), audio: VocabularyAudioState = VocabularyAudioState.Ready) =
    Round1UiState.Learning(word, 2, 3, round1DemoWords.size, audio)

@Composable
private fun RoundPreview(state: Round1UiState) {
    English_Learning_AppTheme(dynamicColor = false) {
        Round1Screen(
            state, onCardAction = { _, _ -> }, onUndo = {}, onAudio = {},
            onFinish = {}, onRetry = {}, onExit = {}, onContinue = {}
        )
    }
}

@Preview(name = "Loading", widthDp = 360, heightDp = 680)
@Composable
private fun LoadingPreview() = RoundPreview(Round1UiState.Loading)

@Preview(name = "Word without image", widthDp = 360, heightDp = 680)
@Preview(name = "Word in dark mode", widthDp = 360, heightDp = 680, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CardPreview() = RoundPreview(demoCard())

@Preview(name = "Audio playing", widthDp = 360, heightDp = 680)
@Composable
private fun AudioPreview() = RoundPreview(demoCard(audio = VocabularyAudioState.Playing))

@Preview(name = "Audio unavailable", widthDp = 360, heightDp = 680)
@Composable
private fun AudioUnavailablePreview() = RoundPreview(demoCard(audio = VocabularyAudioState.Unavailable))

@Preview(name = "Audio error", widthDp = 360, heightDp = 680)
@Composable
private fun AudioErrorPreview() = RoundPreview(demoCard(audio = VocabularyAudioState.Error))

@Preview(name = "Swipe add", widthDp = 360, heightDp = 680)
@Composable
private fun SwipeAddPreview() = SwipePreview(0.85f)

@Preview(name = "Swipe skip", widthDp = 360, heightDp = 680)
@Composable
private fun SwipeSkipPreview() = SwipePreview(-0.85f)

@Composable
private fun SwipePreview(fraction: Float) {
    English_Learning_AppTheme(dynamicColor = false) {
        Column(
            Modifier.fillMaxSize().background(roundColors().background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            RoundHeading(stringResource(R.string.round_title), stringResource(R.string.round_learn_words), {})
            RoundWordProgress(3, 10)
            VocabularySwipeCard(
                word = round1DemoWords.first().cardContent(),
                audio = VocabularyAudioState.Ready,
                cardsBehind = 2,
                onAudio = {},
                cardModifier = Modifier.graphicsLayer {
                    translationX = 24.dp.toPx() * fraction
                    rotationZ = 12f * fraction
                },
                swipeFraction = fraction
            )
        }
    }
}

@Preview(name = "Already in Flashcards", widthDp = 360, heightDp = 680)
@Composable
private fun AlreadyAddedPreview() = RoundPreview(demoCard(round1DemoWords.first { it.isInFlashcards }))

@Preview(name = "Exit confirmation", widthDp = 360, heightDp = 680)
@Composable
private fun ExitDialogPreview() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        RoundPreview(demoCard())
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.35f)))
        Box(Modifier.padding(24.dp).widthIn(max = 360.dp)) {
            RoundExitDialogContent(
                stringResource(R.string.round_leave_saved),
                stringResource(R.string.round_leave_title),
                stringResource(R.string.round_keep_learning),
                stringResource(R.string.round_leave),
                onKeepLearning = {},
                onLeave = {}
            )
        }
    }
}

@Preview(name = "All words done", widthDp = 360, heightDp = 680)
@Composable
private fun ReadyToFinishPreview() = RoundPreview(Round1UiState.Finishing(10))

@Preview(name = "Saving", widthDp = 360, heightDp = 680)
@Composable
private fun SavingPreview() = RoundPreview(Round1UiState.Finishing(10, Round1FinishStatus.Saving))

@Preview(name = "Save error", widthDp = 360, heightDp = 680)
@Composable
private fun SaveErrorPreview() = RoundPreview(Round1UiState.Finishing(10, Round1FinishStatus.Error))

@Preview(name = "Round complete", widthDp = 360, heightDp = 680)
@Composable
private fun CompletedPreview() = RoundPreview(Round1UiState.Completed(10, 3, 6, 4))

@Preview(name = "Round error", widthDp = 360, heightDp = 680)
@Composable
private fun ErrorPreview() = RoundPreview(Round1UiState.Error)

@Preview(name = "No words", widthDp = 360, heightDp = 680)
@Composable
private fun EmptyPreview() = RoundPreview(Round1UiState.Empty)

@Preview(name = "Narrow, 130% font scale", widthDp = 320, heightDp = 560, fontScale = 1.3f)
@Composable
private fun NarrowPreview() = RoundPreview(demoCard(round1DemoWords.last(), VocabularyAudioState.Unavailable))
