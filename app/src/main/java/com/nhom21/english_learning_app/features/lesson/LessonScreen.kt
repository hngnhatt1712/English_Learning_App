package com.nhom21.english_learning_app.features.lesson

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.core.ui.components.Owla3DButton
import com.nhom21.english_learning_app.core.ui.components.OwlaExpression
import com.nhom21.english_learning_app.core.ui.components.OwlaMascot
import com.nhom21.english_learning_app.core.ui.components.PathGeometry
import com.nhom21.english_learning_app.core.ui.components.PathNode
import com.nhom21.english_learning_app.core.ui.components.PathNodeShape
import com.nhom21.english_learning_app.core.ui.components.PathNodeState
import com.nhom21.english_learning_app.core.ui.components.TopicProgress
import com.nhom21.english_learning_app.ui.theme.OwlaColors
import com.nhom21.english_learning_app.ui.theme.OwlaType
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LessonRoute(
    modifier: Modifier = Modifier,
    viewModel: LessonViewModel = hiltViewModel(),
    onRoundStart: (String, String, RoundKind) -> Unit = { _, _, _ -> },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LessonScreen(
        uiState = state,
        onRetry = viewModel::onRetry,
        onRoundClick = viewModel::onRoundClick,
        onDismissPreview = viewModel::onDismissPreview,
        onRoundStart = onRoundStart,
        events = viewModel.events,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LessonScreen(
    uiState: LessonUiState,
    onRetry: () -> Unit,
    onRoundClick: (String) -> Unit,
    onDismissPreview: () -> Unit,
    onRoundStart: (String, String, RoundKind) -> Unit,
    events: kotlinx.coroutines.flow.Flow<LessonEvent>?,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(events, uiState.topics) {
        events?.collectLatest { event ->
            when (event) {
                is LessonEvent.ScrollToCurrent -> {
                    val index = itemIndexForRound(uiState.topics, uiState.currentRoundKey)
                    if (index >= 0) {
                        if (event.animated) listState.animateScrollToItem(index) else listState.scrollToItem(index)
                    }
                }
                is LessonEvent.ScrollToTopic -> {
                    val index = itemIndexForTopic(uiState.topics, event.topicId)
                    if (index >= 0) listState.animateScrollToItem(index)
                }
                LessonEvent.RefreshFailed -> {
                    val action = snackbar.showSnackbar(
                        context.getString(R.string.lesson_refresh_failed),
                        actionLabel = context.getString(R.string.lesson_try_again),
                    )
                    if (action == androidx.compose.material3.SnackbarResult.ActionPerformed) onRetry()
                }
                is LessonEvent.ShowMessage -> snackbar.showSnackbar(context.getString(event.resId))
            }
        }
    }

    val background = if (androidx.compose.foundation.isSystemInDarkTheme()) OwlaColors.DarkBackground else OwlaColors.LightBackground
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = background,
    ) { padding ->
        when (uiState.phase) {
            LessonPhase.Loading -> LessonLoading(Modifier.fillMaxSize().padding(padding))
            LessonPhase.Empty -> LessonEmpty(onRetry, Modifier.fillMaxSize().padding(padding))
            LessonPhase.Error -> LessonErrorState(uiState.error, onRetry, Modifier.fillMaxSize().padding(padding))
            LessonPhase.Content -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                item(key = "level-title") {
                    Text(
                        text = uiState.levelLabel,
                        style = OwlaType.h1,
                        color = if (androidx.compose.foundation.isSystemInDarkTheme()) OwlaColors.DarkTextPrimary else OwlaColors.LightTextPrimary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
                uiState.topics.forEachIndexed { topicIndex, topic ->
                    stickyHeader(key = "header-" + topic.topicId) {
                        TopicHeader(topic)
                    }
                    topic.rounds.forEach { round ->
                        item(key = round.key) {
                            RoundPathRow(
                                topic = topic,
                                topicIndex = topicIndex,
                                round = round,
                                isCurrent = uiState.currentRoundKey == round.key,
                                tooltip = uiState.lockedTooltip?.takeIf { it.roundKey == round.key },
                                onClick = { onRoundClick(round.key) },
                            )
                        }
                    }
                }
                item(key = "lesson-bottom-space") { Spacer(Modifier.height(40.dp)) }
            }
        }
    }

    uiState.preview?.let { preview ->
        RoundPreviewSheet(preview, onDismissPreview, onRoundStart)
    }
}

@Composable
private fun TopicHeader(topic: TopicSectionUi) {
    val theme = rememberTopicTheme(topic.themeKey)
    Surface(
        color = theme.colors.accent,
        contentColor = theme.colors.onAccent,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.lesson_topic_label, topic.orderIndex),
                        style = OwlaType.caption,
                        color = theme.colors.onAccent.copy(alpha = .78f),
                    )
                    Text(
                        text = topic.name,
                        style = OwlaType.h1,
                        color = theme.colors.onAccent,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Rounded.MenuBook, contentDescription = null, tint = theme.colors.onAccent, modifier = Modifier.size(30.dp))
            }
            TopicProgress(
                roundsCompleted = topic.roundsCompleted,
                roundsTotal = topic.roundsTotal,
                stars = topic.stars,
                maxStars = topic.maxStars,
                accent = theme.colors.onAccent.copy(alpha = .7f),
                accentDeep = theme.colors.onAccent,
                track = theme.colors.accentDeep.copy(alpha = .45f),
            )
        }
    }
}

@Composable
private fun RoundPathRow(
    topic: TopicSectionUi,
    topicIndex: Int,
    round: RoundNodeUi,
    isCurrent: Boolean,
    tooltip: LockedTooltipUi?,
    onClick: () -> Unit,
) {
    val theme = rememberTopicTheme(topic.themeKey)
    val xOffset = PathGeometry.xOffset(round.index, topicIndex, 88.dp)
    val sign = xOffset.value.compareTo(0f)
    val stateText = when (round.state) {
        PathNodeState.Completed -> stringResource(R.string.lesson_round_completed)
        PathNodeState.Current -> stringResource(R.string.lesson_round_current)
        PathNodeState.Locked -> stringResource(R.string.lesson_round_locked)
    }
    val kindText = roundKindLabel(round.kind)
    val description = stringResource(R.string.lesson_round_description, round.index, kindText, stateText, round.stars)
    Box(
        modifier = Modifier.fillMaxWidth().height(144.dp).topicBackground(theme),
        contentAlignment = Alignment.Center,
    ) {
        TopicDecorRow(theme, round.index, sign, Modifier.fillMaxSize(), enabled = !isCurrent)
        if (isCurrent) {
            OwlaMascot(
                expression = OwlaExpression.Idle,
                modifier = Modifier
                    .align(if (sign >= 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 20.dp)
                    .size(82.dp),
            )
        }
        PathNode(
            state = round.state,
            stars = round.stars,
            colors = theme.colors.toPathNodeColors(),
            onClick = onClick,
            contentDescription = description,
            shape = if (round.isChest) PathNodeShape.Chest else PathNodeShape.Round,
            modifier = Modifier.offset(x = xOffset),
        )
        tooltip?.let {
            val message = when (val hint = it.hint) {
                is UnlockHint.PreviousRound -> stringResource(R.string.lesson_unlock_previous_round, hint.roundIndex)
                UnlockHint.PreviousTopic -> stringResource(R.string.lesson_unlock_previous_topic)
            }
            Surface(
                color = if (androidx.compose.foundation.isSystemInDarkTheme()) OwlaColors.DarkSurface else OwlaColors.LightSurface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.TopCenter).offset(x = xOffset).padding(top = 2.dp),
            ) {
                Text(message, style = OwlaType.caption, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoundPreviewSheet(
    preview: RoundPreviewUi,
    onDismiss: () -> Unit,
    onRoundStart: (String, String, RoundKind) -> Unit,
) {
    val theme = rememberTopicTheme(preview.themeKey)
    val playableRoundId = when (preview.round.kind) {
        RoundKind.LEARN, RoundKind.RECOGNITION, RoundKind.MATCHING, RoundKind.APPLICATION -> preview.round.roundId
        RoundKind.FINAL_REVIEW -> null
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = if (androidx.compose.foundation.isSystemInDarkTheme()) OwlaColors.DarkSurface else OwlaColors.LightSurface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(preview.topicName, style = OwlaType.caption)
            Text(roundKindLabel(preview.round.kind), style = OwlaType.h1)
            Text(stringResource(R.string.lesson_best_stars, preview.bestStars), style = OwlaType.body)
            Owla3DButton(
                text = stringResource(if (preview.isRetry) R.string.lesson_retry else R.string.lesson_start),
                onClick = {
                    playableRoundId?.let { roundId ->
                        onDismiss()
                        onRoundStart(roundId, preview.topicName, preview.round.kind)
                    }
                },
                enabled = playableRoundId != null,
                baseColor = theme.colors.accent,
                shadowColor = theme.colors.accentDeep,
                textColor = theme.colors.onAccent,
                height = 56.dp,
            )
            if (playableRoundId == null) {
                Text(stringResource(R.string.lesson_round_coming_soon), style = OwlaType.caption)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LessonLoading(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OwlaMascot(OwlaExpression.Think, Modifier.size(140.dp))
            CircularProgressIndicator(color = OwlaColors.PrimaryBase)
            Text(stringResource(R.string.lesson_loading), style = OwlaType.body)
        }
    }
}

@Composable
private fun LessonEmpty(onRetry: () -> Unit, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OwlaMascot(OwlaExpression.Sad, Modifier.size(180.dp))
            Text(stringResource(R.string.lesson_empty), style = OwlaType.h2)
            Owla3DButton(stringResource(R.string.lesson_try_again), onRetry)
        }
    }
}

@Composable
private fun LessonErrorState(error: LessonError?, onRetry: () -> Unit, modifier: Modifier) {
    val text = when (error) {
        LessonError.Network -> stringResource(R.string.lesson_error_network)
        LessonError.Server -> stringResource(R.string.lesson_error_server)
        else -> stringResource(R.string.lesson_error_unknown)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OwlaMascot(OwlaExpression.Offline, Modifier.size(180.dp))
            Text(text, style = OwlaType.body)
            Owla3DButton(stringResource(R.string.lesson_try_again), onRetry)
        }
    }
}

@Composable
private fun roundKindLabel(kind: RoundKind): String = stringResource(
    when (kind) {
        RoundKind.LEARN -> R.string.lesson_round_learn
        RoundKind.RECOGNITION -> R.string.lesson_round_recognition
        RoundKind.MATCHING -> R.string.lesson_round_matching
        RoundKind.APPLICATION -> R.string.lesson_round_application
        RoundKind.FINAL_REVIEW -> R.string.lesson_round_final_review
    },
)

private fun itemIndexForTopic(topics: List<TopicSectionUi>, topicId: String): Int {
    val topicIndex = topics.indexOfFirst { it.topicId == topicId }
    if (topicIndex < 0) return -1
    return 1 + topics.take(topicIndex).sumOf { 1 + it.rounds.size }
}

private fun itemIndexForRound(topics: List<TopicSectionUi>, roundKey: String?): Int {
    if (roundKey == null) return -1
    var index = 1
    topics.forEach { topic ->
        index += 1
        val roundIndex = topic.rounds.indexOfFirst { it.key == roundKey }
        if (roundIndex >= 0) return index + roundIndex
        index += topic.rounds.size
    }
    return -1
}

@Preview(name = "Lesson - Light", showBackground = true, heightDp = 820)
@Preview(name = "Lesson - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, heightDp = 820)
@Composable
private fun LessonPreview() {
    val rounds = (1..5).map { index ->
        RoundNodeUi(
            key = "topic-r" + index,
            roundId = if (index <= 2) "r" + index else null,
            index = index,
            kind = RoundKind.entries[index - 1],
            state = when {
                index == 1 -> PathNodeState.Completed
                index == 2 -> PathNodeState.Current
                else -> PathNodeState.Locked
            },
            stars = if (index == 1) 2 else 0,
            isChest = index == 5,
            unlockHint = if (index > 2) UnlockHint.PreviousRound(index - 1) else null,
        )
    }
    LessonScreen(
        uiState = LessonUiState(
            phase = LessonPhase.Content,
            levelLabel = "Level 1",
            topics = listOf(
                TopicSectionUi("topic", null, "Giới thiệu bản thân và gia đình", 1, "family", false, false, 0, 5, 0, 15, 20, rounds, false),
            ),
            currentTopicId = "topic",
            currentRoundKey = "topic-r2",
        ),
        onRetry = {},
        onRoundClick = {},
        onDismissPreview = {},
        onRoundStart = { _, _, _ -> },
        events = null,
    )
}
