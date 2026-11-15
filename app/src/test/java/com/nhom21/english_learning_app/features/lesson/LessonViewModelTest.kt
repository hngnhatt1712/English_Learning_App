package com.nhom21.english_learning_app.features.lesson

import androidx.lifecycle.SavedStateHandle
import com.nhom21.english_learning_app.MainDispatcherRule
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.remote.dto.HomeDataDto
import com.nhom21.english_learning_app.data.remote.dto.HomeLevelDto
import com.nhom21.english_learning_app.data.repository.HomeRepository
import com.nhom21.english_learning_app.data.repository.LevelRepository
import com.nhom21.english_learning_app.data.repository.TopicRepository
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

    private val topics = listOf(
        topic("topic-1", "Self & Family", "family", ContentStatus.UNLOCKED, 1),
        topic("topic-2", "Daily", "daily", ContentStatus.LOCKED, 2),
        topic("topic-3", "Food", "food", ContentStatus.LOCKED, 3),
    )

    override suspend fun getTopics(levelId: String): ApiResult<List<Topic>> = ApiResult.Success(topics)

    override suspend fun getTopicDetail(topicId: String): ApiResult<TopicDetail> {
        val topic = topics.first { it.id == topicId }
        return ApiResult.Success(TopicDetail(topic, listOf(LessonSummary("lesson-1", topicId, topic.name, null, 1))))
    }

    override suspend fun getLesson(lessonId: String): ApiResult<LessonDetail> {
        lessonCalls += 1
        return ApiResult.Success(LessonDetail(LessonSummary(lessonId, "topic-1", "Lesson", null, 1), emptyList()))
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
