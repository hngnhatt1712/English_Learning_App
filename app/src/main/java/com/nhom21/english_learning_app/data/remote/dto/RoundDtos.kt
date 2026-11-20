package com.nhom21.english_learning_app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RoundDetailDto(
    val id: String,
    val lessonId: String,
    val roundNumber: Int,
    val type: String,
    val title: String,
    val status: String,
    val stars: Int,
    val bestScorePercent: Double?,
    val attempts: Int,
    val totalQuestions: Int,
    val questions: List<QuestionDto>,
)

@Serializable
data class SubmitRoundAnswer(
    val questionId: String,
    val answer: AnswerDto,
)

@Serializable
data class SubmitRoundRequest(val answers: List<SubmitRoundAnswer>)

@Serializable
data class RoundResultDto(
    val roundId: String,
    val totalQuestions: Int,
    val correctCount: Int,
    val scorePercent: Double,
    val passed: Boolean,
    val passThresholdPercent: Int,
    val stars: Int,
    val bestStars: Int,
    val bestScorePercent: Double?,
    val isNewBest: Boolean,
    val attempts: Int,
    val results: List<QuestionResultDto>,
    val unlocks: UnlocksDto,
    val streak: StreakDto,
)

@Serializable
data class UnlocksDto(
    val nextRoundId: String?,
    val topicCompleted: Boolean,
    val nextTopicId: String?,
    val levelCompleted: Boolean,
    val nextLevelId: String?,
)
