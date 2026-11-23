package com.nhom21.english_learning_app.domain.usecase

import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.repository.TopicRepository
import com.nhom21.english_learning_app.domain.model.TopicDetail
import javax.inject.Inject

/**
 * Use case to retrieve detailed information and lessons for a given topic.
 */
class GetTopicDetailUseCase @Inject constructor(
    private val topicRepository: TopicRepository
) {
    /**
     * Executes topic detail retrieval for [topicId].
     */
    suspend operator fun invoke(topicId: String): ApiResult<TopicDetail> {
        return topicRepository.getTopicDetail(topicId = topicId)
    }
}

