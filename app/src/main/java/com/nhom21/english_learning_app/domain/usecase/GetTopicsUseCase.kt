package com.nhom21.english_learning_app.domain.usecase

import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.repository.TopicRepository
import com.nhom21.english_learning_app.domain.model.Topic
import javax.inject.Inject

/**
 * Use case to retrieve the list of topics belonging to a specific level.
 */
class GetTopicsUseCase @Inject constructor(
    private val topicRepository: TopicRepository
) {
    /**
     * Executes topic list retrieval for [levelId].
     */
    suspend operator fun invoke(levelId: String): ApiResult<List<Topic>> {
        return topicRepository.getTopics(levelId = levelId)
    }
}

