package com.nhom21.english_learning_app.data.remote.mapper

import com.nhom21.english_learning_app.data.remote.dto.LessonDetailDto
import com.nhom21.english_learning_app.data.remote.dto.LessonSummaryDto
import com.nhom21.english_learning_app.data.remote.dto.RoundDto
import com.nhom21.english_learning_app.data.remote.dto.TopicDetailDto
import com.nhom21.english_learning_app.data.remote.dto.TopicDto
import com.nhom21.english_learning_app.domain.model.ContentStatus
import com.nhom21.english_learning_app.domain.model.RoundType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicMapperTest {

    @Test
    fun `topicDto toDomain maps all fields preserving stars and progress without recalculation`() {
        val dto = TopicDto(
            id = "topic-1",
            levelId = "level-1",
            name = "Greetings",
            description = "Basic greetings",
            orderIndex = 1,
            status = "unlocked",
            stars = 7,
            maxStars = 15,
            roundsCompleted = 2,
            roundsTotal = 5,
            progressPercent = 40.0,
            themeKey = "food"
        )

        val domain = dto.toDomain()

        assertEquals("topic-1", domain.id)
        assertEquals("level-1", domain.levelId)
        assertEquals("Greetings", domain.name)
        assertEquals("Basic greetings", domain.description)
        assertEquals(1, domain.orderIndex)
        assertEquals(ContentStatus.UNLOCKED, domain.status)
        assertEquals(7, domain.stars)
        assertEquals(15, domain.maxStars)
        assertEquals(2, domain.roundsCompleted)
        assertEquals(5, domain.roundsTotal)
        assertEquals(40.0, domain.progressPercent, 0.001)
        assertEquals("food", domain.themeKey)
    }

    @Test
    fun `topicDto with blank or unknown themeKey and unknown status maps safely`() {
        val dto = TopicDto(
            id = "topic-2",
            name = "Weather",
            description = null,
            status = "archived_future_status",
            themeKey = "   "
        )

        val domain = dto.toDomain()

        assertEquals("default", domain.themeKey)
        assertEquals(ContentStatus.UNKNOWN, domain.status)
        assertNull(domain.description)
        assertNull(domain.levelId)
    }

    @Test
    fun `lessonSummaryDto toDomain maps fields correctly`() {
        val dto = LessonSummaryDto(
            id = "lesson-1",
            topicId = "topic-1",
            title = "Lesson Title",
            description = null,
            orderIndex = 2
        )

        val domain = dto.toDomain()

        assertEquals("lesson-1", domain.id)
        assertEquals("topic-1", domain.topicId)
        assertEquals("Lesson Title", domain.title)
        assertNull(domain.description)
        assertEquals(2, domain.orderIndex)
    }

    @Test
    fun `roundDto toDomain maps known and unknown types and statuses`() {
        val dtoKnown = RoundDto(
            id = "round-1",
            lessonId = "lesson-1",
            roundNumber = 2,
            type = "recognition",
            title = "Word recognition",
            status = "completed",
            stars = 3,
            bestScorePercent = 95.5,
            attempts = 2,
            totalQuestions = 10
        )

        val domainKnown = dtoKnown.toDomain()

        assertEquals(RoundType.RECOGNITION, domainKnown.type)
        assertEquals(ContentStatus.COMPLETED, domainKnown.status)
        assertEquals(95.5, domainKnown.bestScorePercent!!, 0.001)

        val dtoUnknown = RoundDto(
            id = "round-2",
            roundNumber = 9,
            type = "futuristic_ai_dialogue",
            status = "pending_validation"
        )

        val domainUnknown = dtoUnknown.toDomain()

        assertEquals(RoundType.UNKNOWN, domainUnknown.type)
        assertEquals(ContentStatus.UNKNOWN, domainUnknown.status)
        assertNull(domainUnknown.bestScorePercent)
        assertEquals(0, domainUnknown.attempts)
    }

    @Test
    fun `topicDetailDto toDomain maps topic and preserves lesson ordering`() {
        val lesson1 = LessonSummaryDto(id = "l-1", title = "Part 1", orderIndex = 1)
        val lesson2 = LessonSummaryDto(id = "l-2", title = "Part 2", orderIndex = 2)

        val detailDto = TopicDetailDto(
            id = "topic-3",
            name = "Travel",
            themeKey = "travel",
            status = "completed",
            lessons = listOf(lesson1, lesson2)
        )

        val domain = detailDto.toDomain()

        assertEquals("topic-3", domain.topic.id)
        assertEquals(ContentStatus.COMPLETED, domain.topic.status)
        assertEquals(2, domain.lessons.size)
        assertEquals("l-1", domain.lessons[0].id)
        assertEquals("l-2", domain.lessons[1].id)
    }

    @Test
    fun `lessonDetailDto toDomain maps empty rounds as emptyList without crashing`() {
        val detailDto = LessonDetailDto(
            id = "lesson-sprint4",
            title = "Sprint 4 lesson with no rounds yet",
            rounds = emptyList()
        )

        val domain = detailDto.toDomain()

        assertEquals("lesson-sprint4", domain.lesson.id)
        assertTrue(domain.rounds.isEmpty())
    }
}

