package com.nhom21.english_learning_app.data.remote.mapper

import com.nhom21.english_learning_app.data.remote.dto.LessonDetailDto
import com.nhom21.english_learning_app.data.remote.dto.LessonSummaryDto
import com.nhom21.english_learning_app.data.remote.dto.RoundDto
import com.nhom21.english_learning_app.data.remote.dto.TopicDetailDto
import com.nhom21.english_learning_app.data.remote.dto.TopicDto
import com.nhom21.english_learning_app.domain.model.ContentStatus
import com.nhom21.english_learning_app.domain.model.LessonDetail
import com.nhom21.english_learning_app.domain.model.LessonSummary
import com.nhom21.english_learning_app.domain.model.RoundSummary
import com.nhom21.english_learning_app.domain.model.RoundType
import com.nhom21.english_learning_app.domain.model.Topic
import com.nhom21.english_learning_app.domain.model.TopicDetail

/**
 * Maps [TopicDto] wire model to immutable [Topic] domain entity.
 * Fallbacks invalid or blank theme keys to "default" and unrecognized statuses to [ContentStatus.UNKNOWN].
 */
fun TopicDto.toDomain(): Topic {
    return Topic(
        id = id,
        levelId = levelId,
        name = name,
        description = description,
        themeKey = if (themeKey.isBlank()) "default" else themeKey,
        status = ContentStatus.fromRaw(status),
        stars = stars,
        maxStars = maxStars,
        roundsTotal = roundsTotal,
        roundsCompleted = roundsCompleted,
        progressPercent = progressPercent,
        orderIndex = orderIndex
    )
}

/**
 * Maps [LessonSummaryDto] wire model to [LessonSummary] domain entity.
 */
fun LessonSummaryDto.toDomain(): LessonSummary {
    return LessonSummary(
        id = id,
        topicId = topicId,
        title = title,
        description = description,
        orderIndex = orderIndex
    )
}

/**
 * Maps [RoundDto] wire model to [RoundSummary] domain entity.
 */
fun RoundDto.toDomain(): RoundSummary {
    return RoundSummary(
        id = id,
        lessonId = lessonId,
        roundNumber = roundNumber,
        type = RoundType.fromRaw(type),
        title = title,
        status = ContentStatus.fromRaw(status),
        stars = stars,
        bestScorePercent = bestScorePercent,
        attempts = attempts,
        totalQuestions = totalQuestions
    )
}

/**
 * Maps [TopicDetailDto] wire model to [TopicDetail] domain entity.
 */
fun TopicDetailDto.toDomain(): TopicDetail {
    val topic = Topic(
        id = id,
        levelId = levelId,
        name = name,
        description = description,
        themeKey = if (themeKey.isBlank()) "default" else themeKey,
        status = ContentStatus.fromRaw(status),
        stars = stars,
        maxStars = maxStars,
        roundsTotal = roundsTotal,
        roundsCompleted = roundsCompleted,
        progressPercent = progressPercent,
        orderIndex = orderIndex
    )
    return TopicDetail(
        topic = topic,
        lessons = lessons.map { it.toDomain() }
    )
}

/**
 * Maps [LessonDetailDto] wire model to [LessonDetail] domain entity.
 */
fun LessonDetailDto.toDomain(): LessonDetail {
    val lesson = LessonSummary(
        id = id,
        topicId = topicId,
        title = title,
        description = description,
        orderIndex = orderIndex
    )
    return LessonDetail(
        lesson = lesson,
        rounds = rounds.map { it.toDomain() }
    )
}

