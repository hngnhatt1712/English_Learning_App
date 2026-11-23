package com.nhom21.english_learning_app.domain.model

data class RoundDetail(
    val round: RoundSummary,
    val questions: List<Question>,
)

data class RoundAnswer(val questionId: String, val answer: Answer)

data class RoundResult(
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
    val results: List<QuestionResult>,
    val unlocks: RoundUnlocks,
    val streak: Streak,
)

data class RoundUnlocks(
    val nextRoundId: String?,
    val topicCompleted: Boolean,
    val nextTopicId: String?,
    val levelCompleted: Boolean,
    val nextLevelId: String?,
)

data class Streak(
    val current: Int,
    val lastLearningDate: String?,
    val learnedToday: Boolean,
)
