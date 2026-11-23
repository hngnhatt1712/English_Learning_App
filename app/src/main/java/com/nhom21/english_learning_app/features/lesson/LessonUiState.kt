package com.nhom21.english_learning_app.features.lesson

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.nhom21.english_learning_app.core.ui.components.PathNodeState

enum class RoundKind { LEARN, RECOGNITION, MATCHING, APPLICATION, FINAL_REVIEW }

sealed interface UnlockHint {
    data class PreviousRound(val roundIndex: Int) : UnlockHint
    data object PreviousTopic : UnlockHint
}

@Immutable
data class RoundNodeUi(
    val key: String,
    val roundId: String?,
    val index: Int,
    val kind: RoundKind,
    val state: PathNodeState,
    val stars: Int,
    val isChest: Boolean,
    val unlockHint: UnlockHint?,
)

@Immutable
data class TopicSectionUi(
    val topicId: String,
    val lessonId: String?,
    val name: String,
    val orderIndex: Int,
    val themeKey: String?,
    val isLocked: Boolean,
    val isCompleted: Boolean,
    val roundsCompleted: Int,
    val roundsTotal: Int,
    val stars: Int,
    val maxStars: Int,
    val progressPercent: Int,
    val rounds: List<RoundNodeUi>,
    val isLessonLoading: Boolean,
)

@Immutable
data class LevelUi(
    val levelId: String,
    val label: String,
    val isLocked: Boolean,
    val isSelected: Boolean,
    val topicsCompleted: Int,
    val topicsTotal: Int,
    val stars: Int,
    val maxStars: Int,
)

@Immutable
data class RoundPreviewUi(
    val topicId: String,
    val topicName: String,
    val themeKey: String?,
    val round: RoundNodeUi,
    val bestStars: Int,
    val isRetry: Boolean,
)

@Immutable data class LockedTooltipUi(val roundKey: String, val hint: UnlockHint)

enum class LessonPhase { Loading, Content, Empty, Error }
enum class LessonError { Network, Server, Unknown }

@Immutable
data class LessonUiState(
    val phase: LessonPhase = LessonPhase.Loading,
    val error: LessonError? = null,
    val levelLabel: String = "",
    val levels: List<LevelUi> = emptyList(),
    val topics: List<TopicSectionUi> = emptyList(),
    val isLevelSwitching: Boolean = false,
    val currentTopicId: String? = null,
    val currentRoundKey: String? = null,
    val preview: RoundPreviewUi? = null,
    val lockedTooltip: LockedTooltipUi? = null,
)

sealed interface LessonEvent {
    data object RefreshFailed : LessonEvent
    data class ScrollToCurrent(val animated: Boolean) : LessonEvent
    data class ScrollToTopic(val topicId: String) : LessonEvent
    data class ShowMessage(@StringRes val resId: Int) : LessonEvent
}
