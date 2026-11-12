package com.nhom21.english_learning_app.domain.usecase

import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.repository.TopicRepository
import com.nhom21.english_learning_app.domain.model.LessonDetail
import javax.inject.Inject

/**
 * Use case to retrieve lesson detail and its constituent rounds.
 */
class GetLessonUseCase @Inject constructor(
    private val topicRepository: TopicRepository
) {
    /**
     * Executes lesson detail retrieval for [lessonId].
     */
    suspend operator fun invoke(lessonId: String): ApiResult<LessonDetail> {
        return topicRepository.getLesson(lessonId = lessonId)
    }
}

