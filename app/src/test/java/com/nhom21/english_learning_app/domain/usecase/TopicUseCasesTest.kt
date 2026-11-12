package com.nhom21.english_learning_app.domain.usecase

import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.repository.TopicRepository
import com.nhom21.english_learning_app.domain.model.ContentStatus
import com.nhom21.english_learning_app.domain.model.LessonDetail
import com.nhom21.english_learning_app.domain.model.LessonSummary
import com.nhom21.english_learning_app.domain.model.Topic
import com.nhom21.english_learning_app.domain.model.TopicDetail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TopicUseCasesTest {

    private class FakeTopicRepository : TopicRepository {
        var lastLevelId: String? = null
        var lastTopicId: String? = null
        var lastLessonId: String? = null

        var topicsResult: ApiResult<List<Topic>> = ApiResult.Success(emptyList())
        var topicDetailResult: ApiResult<TopicDetail> = ApiResult.Success(
            TopicDetail(
                topic = Topic(
                    id = "fake-topic",
                    levelId = "fake-level",
                    name = "Fake Topic",
                    description = null,
                    themeKey = "default",
                    status = ContentStatus.UNLOCKED,
                    stars = 0,
                    maxStars = 15,
                    roundsTotal = 5,
                    roundsCompleted = 0,
                    progressPercent = 0.0,
                    orderIndex = 1
                ),
                lessons = emptyList()
            )
        )
        var lessonDetailResult: ApiResult<LessonDetail> = ApiResult.Success(
            LessonDetail(
                lesson = LessonSummary(
                    id = "fake-lesson",
                    topicId = "fake-topic",
                    title = "Fake Lesson",
                    description = null,
                    orderIndex = 1
                ),
                rounds = emptyList()
            )
        )

        override suspend fun getTopics(levelId: String): ApiResult<List<Topic>> {
            lastLevelId = levelId
            return topicsResult
        }

        override suspend fun getTopicDetail(topicId: String): ApiResult<TopicDetail> {
            lastTopicId = topicId
            return topicDetailResult
        }

        override suspend fun getLesson(lessonId: String): ApiResult<LessonDetail> {
            lastLessonId = lessonId
            return lessonDetailResult
        }
    }

    @Test
    fun `GetTopicsUseCase delegates parameter to repository`() = runTest {
        val fakeRepo = FakeTopicRepository()
        val expectedTopic = Topic(
            id = "t-1",
            levelId = "lvl-100",
            name = "Animals",
            description = null,
            themeKey = "nature",
            status = ContentStatus.UNLOCKED,
            stars = 2,
            maxStars = 15,
            roundsTotal = 5,
            roundsCompleted = 1,
            progressPercent = 20.0,
            orderIndex = 1
        )
        fakeRepo.topicsResult = ApiResult.Success(listOf(expectedTopic))

        val useCase = GetTopicsUseCase(fakeRepo)
        val result = useCase("lvl-100")

        assertEquals("lvl-100", fakeRepo.lastLevelId)
        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals(1, data.size)
        assertEquals("Animals", data[0].name)
    }

    @Test
    fun `GetTopicDetailUseCase delegates parameter to repository`() = runTest {
        val fakeRepo = FakeTopicRepository()
        val useCase = GetTopicDetailUseCase(fakeRepo)

        val result = useCase("topic-999")

        assertEquals("topic-999", fakeRepo.lastTopicId)
        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals("fake-topic", data.topic.id)
    }

    @Test
    fun `GetLessonUseCase delegates parameter to repository`() = runTest {
        val fakeRepo = FakeTopicRepository()
        val useCase = GetLessonUseCase(fakeRepo)

        val result = useCase("lesson-888")

        assertEquals("lesson-888", fakeRepo.lastLessonId)
        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals("fake-lesson", data.lesson.id)
    }
}

