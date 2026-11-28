package com.nhom21.english_learning_app.data.repository

import com.nhom21.english_learning_app.core.network.ApiErrorParser
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.core.network.callApi
import com.nhom21.english_learning_app.data.remote.api.QuestionApi
import com.nhom21.english_learning_app.data.remote.api.RoundApi
import com.nhom21.english_learning_app.data.remote.api.VocabularyApi
import com.nhom21.english_learning_app.data.remote.dto.CheckAnswerRequest
import com.nhom21.english_learning_app.data.remote.dto.SubmitRoundRequest
import com.nhom21.english_learning_app.data.remote.dto.VocabularyActionRequest
import com.nhom21.english_learning_app.data.remote.mapper.toDomain
import com.nhom21.english_learning_app.data.remote.mapper.toCheckDto
import com.nhom21.english_learning_app.data.remote.mapper.toDto
import com.nhom21.english_learning_app.domain.model.Answer
import com.nhom21.english_learning_app.domain.model.QuestionResult
import com.nhom21.english_learning_app.domain.model.RoundAnswer
import com.nhom21.english_learning_app.domain.model.RoundDetail
import com.nhom21.english_learning_app.domain.model.RoundResult
import com.nhom21.english_learning_app.domain.model.VocabularyAction
import com.nhom21.english_learning_app.domain.model.VocabularyActionResult
import javax.inject.Inject
import javax.inject.Singleton

interface RoundRepository {
    suspend fun getRound(roundId: String): ApiResult<RoundDetail>
    suspend fun checkAnswer(questionId: String, answer: Answer): ApiResult<QuestionResult>
    suspend fun submitRound(roundId: String, answers: List<RoundAnswer>): ApiResult<RoundResult>
    suspend fun vocabularyAction(
        roundId: String,
        vocabularyId: String,
        action: VocabularyAction,
    ): ApiResult<VocabularyActionResult>
}

@Singleton
class RoundRepositoryImpl @Inject constructor(
    private val roundApi: RoundApi,
    private val questionApi: QuestionApi,
    private val vocabularyApi: VocabularyApi,
    private val errorParser: ApiErrorParser,
) : RoundRepository {
    override suspend fun getRound(roundId: String): ApiResult<RoundDetail> =
        errorParser.callApi({ roundApi.getRound(roundId) }) { it.toDomain() }

    override suspend fun checkAnswer(questionId: String, answer: Answer): ApiResult<QuestionResult> =
        errorParser.callApi({
            questionApi.checkAnswer(questionId, CheckAnswerRequest(answer.toCheckDto()))
        }) { it.toDomain() }

    override suspend fun submitRound(roundId: String, answers: List<RoundAnswer>): ApiResult<RoundResult> =
        errorParser.callApi({
            val request = SubmitRoundRequest(answers.map(RoundAnswer::toDto))
            roundApi.submitRound(roundId, request)
        }) { it.toDomain() }

    override suspend fun vocabularyAction(
        roundId: String,
        vocabularyId: String,
        action: VocabularyAction,
    ): ApiResult<VocabularyActionResult> = errorParser.callApi({
        vocabularyApi.vocabularyAction(roundId, VocabularyActionRequest(vocabularyId, action.toDto()))
    }) { it.toDomain() }
}
