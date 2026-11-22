package com.nhom21.english_learning_app.features.lesson

import androidx.lifecycle.SavedStateHandle
import com.nhom21.english_learning_app.MainDispatcherRule
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.remote.dto.HomeDataDto
import com.nhom21.english_learning_app.data.remote.dto.HomeLevelDto
import com.nhom21.english_learning_app.data.repository.HomeRepository
import com.nhom21.english_learning_app.data.repository.LevelRepository
import com.nhom21.english_learning_app.data.repository.TopicRepository
import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiFailure
import com.nhom21.english_learning_app.domain.model.RoundSummary
import com.nhom21.english_learning_app.domain.model.RoundType
import com.nhom21.english_learning_app.domain.model.ContentStatus
import com.nhom21.english_learning_app.domain.model.LessonDetail
import com.nhom21.english_learning_app.domain.model.LessonSummary
import com.nhom21.english_learning_app.domain.model.Level
import com.nhom21.english_learning_app.domain.model.Topic
import com.nhom21.english_learning_app.domain.model.TopicDetail
import com.nhom21.english_learning_app.domain.usecase.GetLessonUseCase
import com.nhom21.english_learning_app.domain.usecase.GetLevelsUseCase
import com.nhom21.english_learning_app.domain.usecase.GetTopicDetailUseCase
import com.nhom21.english_learning_app.domain.usecase.GetTopicsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LessonViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadsCurrentLevelAndSkipsLessonCallsForLockedTopics() = runTest {
        val topicRepository = FakeTopicRepository()
        val viewModel = LessonViewModel(
            getLevels = GetLevelsUseCase(FakeLevelRepository()),
            getTopics = GetTopicsUseCase(topicRepository),
            getTopicDetail = GetTopicDetailUseCase(topicRepository),
            getLesson = GetLessonUseCase(topicRepository),
            homeRepository = FakeHomeRepository(),
            savedStateHandle = SavedStateHandle(),
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LessonPhase.Content, state.phase)
        assertEquals("Level 1", state.levelLabel)
        assertEquals(3, state.topics.size)
        assertEquals(1, topicRepository.lessonCalls)
        assertFalse(state.topics.first().isLocked)
        assertNotNull(state.currentRoundKey)
    }

    @Test
    fun retryRefreshUpdatesBothTopicAndRoundFromServer() = runTest {
        val repo = FakeTopicRepository()
        val vm = LessonViewModel(GetLevelsUseCase(FakeLevelRepository()), GetTopicsUseCase(repo),
            GetTopicDetailUseCase(repo), GetLessonUseCase(repo), FakeHomeRepository(), SavedStateHandle())
        advanceUntilIdle()
        val before = vm.uiState.value
        repo.completed = true
        repo.offline = true
        vm.refreshRound("round-1")
        advanceUntilIdle()
        assertEquals(before, vm.uiState.value)
        repo.offline = false
        vm.onRetry()
        advanceUntilIdle()
        val section = vm.uiState.value.topics.first()
        assertEquals(3, section.stars)
        assertEquals(1, section.roundsCompleted)
        assertEquals(20, section.progressPercent)
        assertEquals(3, section.rounds.first().stars)
        vm.onRoundClick(section.rounds[1].key)
        assertEquals("round-2", vm.uiState.value.preview?.round?.roundId)
    }

}

private class FakeLevelRepository : LevelRepository {
    override suspend fun getLevels(): ApiResult<List<Level>> = ApiResult.Success(
        listOf(
            Level("level-1", "Level 1", null, 1),
            Level("level-2", "Level 2", null, 2),
            Level("level-3", "Level 3", null, 3),
        ),
    )
}

private class FakeHomeRepository : HomeRepository {
    override suspend fun getHomeData(): ApiResult<HomeDataDto> =
        ApiResult.Success(HomeDataDto(level = HomeLevelDto(id = "level-1", name = "Level 1")))
}

private class FakeTopicRepository : TopicRepository {
    var lessonCalls = 0
    var completed = false
    var offline = false

    private val topics = listOf(
        topic("topic-1", "Self & Family", "family", ContentStatus.UNLOCKED, 1),
        topic("topic-2", "Daily", "daily", ContentStatus.LOCKED, 2),
        topic("topic-3", "Food", "food", ContentStatus.LOCKED, 3),
    )

    override suspend fun getTopics(levelId: String): ApiResult<List<Topic>> = ApiResult.Success(topics)

    override suspend fun getTopicDetail(topicId: String): ApiResult<TopicDetail> {
        val topic = topics.first { it.id == topicId }
        return ApiResult.Success(TopicDetail(if (completed) topic.copy(stars = 3, roundsCompleted = 1, progressPercent = 20.0) else topic, listOf(LessonSummary("lesson-1", topicId, topic.name, null, 1))))
    }

    override suspend fun getLesson(lessonId: String): ApiResult<LessonDetail> {
        lessonCalls += 1
        if (offline) return ApiResult.Failure(ApiFailure(ApiErrorType.NETWORK, "offline"))
        val first = RoundSummary("round-1", lessonId, 1, RoundType.LEARN, null,
            if (completed) ContentStatus.COMPLETED else ContentStatus.UNLOCKED,
            if (completed) 3 else 0, null, 0, 10)
        val second = RoundSummary("round-2", lessonId, 2, RoundType.RECOGNITION, null,
            if (completed) ContentStatus.UNLOCKED else ContentStatus.LOCKED, 0, null, 0, 10)
        return ApiResult.Success(LessonDetail(LessonSummary(lessonId, "topic-1", "Lesson", null, 1), listOf(first, second)))
    }

    private fun topic(id: String, name: String, theme: String, status: ContentStatus, order: Int) = Topic(
        id = id,
        levelId = "level-1",
        name = name,
        description = null,
        themeKey = theme,
        status = status,
        stars = 0,
        maxStars = 15,
        roundsTotal = 5,
        roundsCompleted = 0,
        progressPercent = 0.0,
        orderIndex = order,
    )
}
