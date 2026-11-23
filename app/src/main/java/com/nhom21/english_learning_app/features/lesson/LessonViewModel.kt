package com.nhom21.english_learning_app.features.lesson

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiFailure
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.core.ui.components.PathNodeState
import com.nhom21.english_learning_app.data.repository.HomeRepository
import com.nhom21.english_learning_app.domain.model.ContentStatus
import com.nhom21.english_learning_app.domain.model.RoundSummary
import com.nhom21.english_learning_app.domain.model.RoundType
import com.nhom21.english_learning_app.domain.model.Topic
import com.nhom21.english_learning_app.domain.usecase.GetLessonUseCase
import com.nhom21.english_learning_app.domain.usecase.GetLevelsUseCase
import com.nhom21.english_learning_app.domain.usecase.GetTopicDetailUseCase
import com.nhom21.english_learning_app.domain.usecase.GetTopicsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

@HiltViewModel
class LessonViewModel @Inject constructor(
    private val getLevels: GetLevelsUseCase,
    private val getTopics: GetTopicsUseCase,
    private val getTopicDetail: GetTopicDetailUseCase,
    private val getLesson: GetLessonUseCase,
    private val homeRepository: HomeRepository,
    @Suppress("UNUSED_PARAMETER") savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LessonUiState())
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<LessonEvent>(Channel.BUFFERED)
    val events: Flow<LessonEvent> = eventChannel.receiveAsFlow()

    private val cache = mutableMapOf<String, List<TopicSectionUi>>()
    private var loadJob: Job? = null
    private var tooltipJob: Job? = null
    private var didInitialScroll = false
    private var selectedLevelId: String? = null

    init {
        loadInitial()
    }

    private var failedRoundRefresh: String? = null

    fun onRetry() {
        failedRoundRefresh?.let(::refreshRound) ?: loadInitial()
    }

    fun refreshRound(roundId: String) {
        val section = _uiState.value.topics.firstOrNull { topic -> topic.rounds.any { it.roundId == roundId } }
            ?: return
        val lessonId = section.lessonId ?: return
        val levelId = selectedLevelId ?: return
        failedRoundRefresh = null
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val detail = getTopicDetail(section.topicId)
            val lesson = getLesson(lessonId)
            if (detail !is ApiResult.Success || lesson !is ApiResult.Success) {
                failedRoundRefresh = roundId
                eventChannel.send(LessonEvent.RefreshFailed)
                return@launch
            }
            val updated = detail.data.topic.toSection(lessonId, section.themeKey ?: "default", lesson.data.rounds)
            val topics = _uiState.value.topics.map { if (it.topicId == section.topicId) updated else it }
            cache[levelId] = topics
            publishTopics(topics)
        }
    }

    fun refresh() {
        cache.clear()
        selectedLevelId?.let { loadLevel(it, switching = false) } ?: loadInitial()
    }

    fun onLevelSelected(levelId: String) {
        val level = _uiState.value.levels.firstOrNull { it.levelId == levelId } ?: return
        if (level.isLocked) {
            viewModelScope.launch { eventChannel.send(LessonEvent.ShowMessage(R.string.lesson_level_locked)) }
            return
        }
        if (levelId == selectedLevelId) return
        failedRoundRefresh = null
        loadLevel(levelId, switching = true)
    }

    fun onTopicSelected(topicId: String) {
        val topic = _uiState.value.topics.firstOrNull { it.topicId == topicId } ?: return
        viewModelScope.launch {
            if (topic.isLocked) eventChannel.send(LessonEvent.ShowMessage(R.string.lesson_topic_locked))
            else eventChannel.send(LessonEvent.ScrollToTopic(topicId))
        }
    }

    fun onRoundClick(roundKey: String) {
        val topic = _uiState.value.topics.firstOrNull { section -> section.rounds.any { it.key == roundKey } } ?: return
        val round = topic.rounds.first { it.key == roundKey }
        if (round.state == PathNodeState.Locked) {
            val hint = round.unlockHint ?: return
            tooltipJob?.cancel()
            _uiState.update { it.copy(lockedTooltip = LockedTooltipUi(roundKey, hint), preview = null) }
            tooltipJob = viewModelScope.launch {
                delay(2_000)
                _uiState.update { state ->
                    if (state.lockedTooltip?.roundKey == roundKey) state.copy(lockedTooltip = null) else state
                }
            }
        } else {
            _uiState.update {
                it.copy(
                    preview = RoundPreviewUi(
                        topicId = topic.topicId,
                        topicName = topic.name,
                        themeKey = topic.themeKey,
                        round = round,
                        bestStars = round.stars,
                        isRetry = round.state == PathNodeState.Completed,
                    ),
                    lockedTooltip = null,
                )
            }
        }
    }

    fun onDismissPreview() = _uiState.update { it.copy(preview = null) }
    fun focusCurrent() {
        viewModelScope.launch { eventChannel.send(LessonEvent.ScrollToCurrent(animated = true)) }
    }

    private fun loadInitial() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = LessonUiState(phase = LessonPhase.Loading)
            val (levelsResult, homeResult) = supervisorScope {
                val levels = async { getLevels() }
                val home = async { homeRepository.getHomeData() }
                levels.await() to home.await()
            }
            if (levelsResult is ApiResult.Failure) {
                showGlobalError(levelsResult.error)
                return@launch
            }
            if (homeResult is ApiResult.Failure) {
                showGlobalError(homeResult.error)
                return@launch
            }
            val levels = (levelsResult as ApiResult.Success).data
            val currentLevelId = (homeResult as ApiResult.Success).data.level?.id
                ?.takeIf { id -> levels.any { it.id == id } }
                ?: levels.firstOrNull()?.id
            if (currentLevelId == null) {
                _uiState.value = LessonUiState(phase = LessonPhase.Empty)
                return@launch
            }
            selectedLevelId = currentLevelId
            val levelUi = levels.map { level ->
                LevelUi(
                    levelId = level.id,
                    label = level.name,
                    isLocked = false,
                    isSelected = level.id == currentLevelId,
                    topicsCompleted = 0,
                    topicsTotal = 3,
                    stars = 0,
                    maxStars = 45,
                )
            }
            _uiState.update {
                it.copy(
                    levels = levelUi,
                    levelLabel = levelUi.first { level -> level.levelId == currentLevelId }.label,
                )
            }
            loadTopics(currentLevelId, switching = false)
        }
    }

    private fun loadLevel(levelId: String, switching: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            selectedLevelId = levelId
            val selected = _uiState.value.levels.firstOrNull { it.levelId == levelId }
            _uiState.update { state ->
                state.copy(
                    isLevelSwitching = switching,
                    levelLabel = selected?.label ?: state.levelLabel,
                    levels = state.levels.map { it.copy(isSelected = it.levelId == levelId) },
                    preview = null,
                    lockedTooltip = null,
                )
            }
            cache[levelId]?.let {
                publishTopics(it)
                return@launch
            }
            loadTopics(levelId, switching)
        }
    }

    private suspend fun loadTopics(levelId: String, switching: Boolean) {
        if (switching) _uiState.update { it.copy(isLevelSwitching = true) }
        when (val result = getTopics(levelId)) {
            is ApiResult.Failure -> showGlobalError(result.error)
            is ApiResult.Success -> {
                val levelOrder = _uiState.value.levels.indexOfFirst { it.levelId == levelId }.coerceAtLeast(0) + 1
                val topics = supervisorScope {
                    result.data.sortedBy { it.orderIndex ?: Int.MAX_VALUE }.mapIndexed { index, topic ->
                        async { loadTopicSection(topic, levelOrder, index + 1) }
                    }.map { it.await() }
                }
                cache[levelId] = topics
                publishTopics(topics)
            }
        }
    }

    private suspend fun loadTopicSection(topic: Topic, levelOrder: Int, fallbackOrder: Int): TopicSectionUi {
        val order = topic.orderIndex ?: fallbackOrder
        val themeKey = resolvedThemeKey(topic.themeKey, levelOrder, order)
        val locked = topic.status == ContentStatus.LOCKED || topic.status == ContentStatus.UNKNOWN
        if (locked) return topic.toSection(null, themeKey, emptyList())

        val detail = getTopicDetail(topic.id)
        if (detail !is ApiResult.Success) return topic.toSection(null, themeKey, emptyList())
        val lessonId = detail.data.lessons.firstOrNull()?.id
        if (lessonId == null) return topic.toSection(null, themeKey, emptyList())
        return when (val lesson = getLesson(lessonId)) {
            is ApiResult.Success -> topic.toSection(lessonId, themeKey, lesson.data.rounds)
            is ApiResult.Failure -> topic.toSection(lessonId, themeKey, emptyList())
        }
    }

    private fun Topic.toSection(lessonId: String?, key: String, roundData: List<RoundSummary>): TopicSectionUi {
        val total = roundsTotal.takeIf { it > 0 } ?: 5
        val nodes = buildRoundNodes(id, status, roundData, total)
        return TopicSectionUi(
            topicId = id,
            lessonId = lessonId,
            name = name,
            orderIndex = orderIndex ?: 1,
            themeKey = key,
            isLocked = status == ContentStatus.LOCKED || status == ContentStatus.UNKNOWN,
            isCompleted = status == ContentStatus.COMPLETED,
            roundsCompleted = roundsCompleted.coerceIn(0, total),
            roundsTotal = total,
            stars = stars.coerceIn(0, maxStars.coerceAtLeast(total * 3)),
            maxStars = maxStars.takeIf { it > 0 } ?: total * 3,
            progressPercent = progressPercent.toInt().coerceIn(0, 100),
            rounds = nodes,
            isLessonLoading = false,
        )
    }

    private fun publishTopics(topics: List<TopicSectionUi>) {
        val currentTopic = topics.firstOrNull { !it.isLocked && !it.isCompleted }
        val currentRound = currentTopic?.rounds?.firstOrNull { it.state == PathNodeState.Current }
        _uiState.update {
            it.copy(
                phase = if (topics.isEmpty()) LessonPhase.Empty else LessonPhase.Content,
                error = null,
                topics = topics,
                isLevelSwitching = false,
                currentTopicId = currentTopic?.topicId,
                currentRoundKey = currentRound?.key,
            )
        }
        if (!didInitialScroll && currentRound != null) {
            didInitialScroll = true
            viewModelScope.launch { eventChannel.send(LessonEvent.ScrollToCurrent(animated = false)) }
        }
    }

    private fun showGlobalError(error: ApiFailure) {
        val mapped = when (error.type) {
            ApiErrorType.NETWORK -> LessonError.Network
            ApiErrorType.INTERNAL_ERROR -> LessonError.Server
            else -> LessonError.Unknown
        }
        _uiState.update { it.copy(phase = LessonPhase.Error, error = mapped, isLevelSwitching = false) }
    }
}

private val officialThemeKeys = listOf(
    listOf("family", "daily", "food"),
    listOf("travel", "work", "health"),
    listOf("entertainment", "tech", "environment"),
)

private fun resolvedThemeKey(raw: String, levelOrder: Int, topicOrder: Int): String {
    val normalized = raw.trim().lowercase()
    val known = officialThemeKeys.flatten()
    if (normalized in known) return normalized
    return officialThemeKeys.getOrNull(levelOrder - 1)?.getOrNull(topicOrder - 1) ?: "default"
}

fun buildRoundNodes(
    topicId: String,
    topicStatus: ContentStatus,
    rounds: List<RoundSummary>,
    roundsTotal: Int,
): List<RoundNodeUi> {
    val total = roundsTotal.coerceAtLeast(1)
    val byIndex = rounds.associateBy { it.roundNumber }
    return (1..total).map { index ->
        val round = byIndex[index]
        val state = when (round?.status) {
            ContentStatus.COMPLETED -> PathNodeState.Completed
            ContentStatus.UNLOCKED -> PathNodeState.Current
            ContentStatus.LOCKED, ContentStatus.UNKNOWN -> PathNodeState.Locked
            null -> when (topicStatus) {
                ContentStatus.COMPLETED -> PathNodeState.Completed
                ContentStatus.UNLOCKED -> if (index == 1) PathNodeState.Current else PathNodeState.Locked
                ContentStatus.LOCKED, ContentStatus.UNKNOWN -> PathNodeState.Locked
            }
        }
        RoundNodeUi(
            key = topicId + "-r" + index,
            roundId = round?.id,
            index = index,
            kind = round?.type.toRoundKind(index),
            state = state,
            stars = round?.stars?.coerceIn(0, 3) ?: 0,
            isChest = index == total,
            unlockHint = if (state != PathNodeState.Locked) null
                else if (index > 1) UnlockHint.PreviousRound(index - 1) else UnlockHint.PreviousTopic,
        )
    }
}

private fun RoundType?.toRoundKind(index: Int): RoundKind = when (this) {
    RoundType.LEARN -> RoundKind.LEARN
    RoundType.RECOGNITION -> RoundKind.RECOGNITION
    RoundType.MATCHING -> RoundKind.MATCHING
    RoundType.APPLICATION -> RoundKind.APPLICATION
    RoundType.REVIEW -> RoundKind.FINAL_REVIEW
    RoundType.UNKNOWN, null -> when (index) {
        1 -> RoundKind.LEARN
        2 -> RoundKind.RECOGNITION
        3 -> RoundKind.MATCHING
        4 -> RoundKind.APPLICATION
        else -> RoundKind.FINAL_REVIEW
    }
}
