package com.nhom21.english_learning_app.features.round

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.repository.RoundRepository
import com.nhom21.english_learning_app.domain.model.Answer
import com.nhom21.english_learning_app.domain.model.ContentStatus
import com.nhom21.english_learning_app.domain.model.MatchingItem
import com.nhom21.english_learning_app.domain.model.MatchingPairResult
import com.nhom21.english_learning_app.domain.model.Question
import com.nhom21.english_learning_app.domain.model.QuestionContent
import com.nhom21.english_learning_app.domain.model.QuestionResult
import com.nhom21.english_learning_app.domain.model.RoundAnswer
import com.nhom21.english_learning_app.domain.model.RoundDetail
import com.nhom21.english_learning_app.domain.model.RoundResult
import com.nhom21.english_learning_app.domain.model.RoundSummary
import com.nhom21.english_learning_app.domain.model.RoundType
import com.nhom21.english_learning_app.domain.model.RoundUnlocks
import com.nhom21.english_learning_app.domain.model.Streak
import com.nhom21.english_learning_app.domain.model.VocabularyAction
import com.nhom21.english_learning_app.domain.usecase.CheckAnswerUseCase
import com.nhom21.english_learning_app.domain.usecase.GetRoundUseCase
import com.nhom21.english_learning_app.domain.usecase.SubmitRoundUseCase
import com.nhom21.english_learning_app.domain.usecase.SwipeVocabularyUseCase
import com.nhom21.english_learning_app.ui.theme.English_Learning_AppTheme

private const val DemoRoundId = "demo-round-3"
private val Context.round3DemoPreferences by preferencesDataStore("round3_demo")

/** Runs the real matching flow against an in-memory demo repository. */
class Round3DemoActivity : ComponentActivity() {
    private val model by viewModels<RoundViewModel> {
        viewModelFactory {
            initializer {
                val repository = Round3DemoRepository()
                RoundViewModel(
                    DemoRoundId,
                    GetRoundUseCase(repository),
                    SubmitRoundUseCase(repository),
                    SwipeVocabularyUseCase(repository),
                    CheckAnswerUseCase(repository),
                    applicationContext.round3DemoPreferences,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            English_Learning_AppTheme {
                val artwork = rememberRound3DemoArtwork()
                RoundRoute(
                    onExit = ::finish, onSubmitted = {}, viewModel = model, topicName = "Trái cây",
                    isMatching = true,
                    matchingImage = { key, modifier -> Round3DemoImage(artwork, key, modifier) },
                )
            }
        }
    }
}

private class Round3DemoRepository : RoundRepository {
    private val boards = listOf(
        board(1, listOf("Apple" to "Táo", "Banana" to "Chuối", "Orange" to "Cam",
            "Pineapple" to "Dứa", "Mango" to "Xoài")),
        board(2, Round3DemoPictureWords, picture = true),
    )

    override suspend fun getRound(roundId: String): ApiResult<RoundDetail> {
        val round = RoundSummary(
            id = roundId,
            lessonId = null,
            roundNumber = 3,
            type = RoundType.MATCHING,
            title = null,
            status = ContentStatus.UNLOCKED,
            stars = 0,
            bestScorePercent = null,
            attempts = 0,
            totalQuestions = 10,
        )
        return ApiResult.Success(RoundDetail(round, boards))
    }

    override suspend fun checkAnswer(questionId: String, answer: Answer): ApiResult<QuestionResult> {
        val pair = (answer as Answer.MatchingCheck).pair
        return ApiResult.Success(QuestionResult(questionId, pair.leftId == pair.rightId, null, null))
    }

    override suspend fun submitRound(roundId: String, answers: List<RoundAnswer>): ApiResult<RoundResult> {
        val results = answers.map { entry ->
            val answer = entry.answer as Answer.Matching
            val units = answer.pairs.map { pair ->
                val first = answer.attempts.firstOrNull { it.leftId == pair.leftId } ?: pair
                MatchingPairResult(first.leftId, first.leftId == first.rightId)
            }
            QuestionResult(entry.questionId, units.all { it.isCorrect }, null, null, units)
        }
        val correct = results.sumOf { result -> result.pairResults.orEmpty().count { it.isCorrect } }
        val stars = when (correct) {
            10 -> 3
            8, 9 -> 2
            7 -> 1
            else -> 0
        }
        val percent = correct * 10.0
        return ApiResult.Success(
            RoundResult(
                roundId = roundId,
                totalQuestions = 10,
                correctCount = correct,
                scorePercent = percent,
                passed = stars > 0,
                passThresholdPercent = 70,
                stars = stars,
                bestStars = stars,
                bestScorePercent = percent,
                isNewBest = true,
                attempts = 1,
                results = results,
                unlocks = RoundUnlocks(null, false, null, false, null),
                streak = Streak(0, null, false),
            )
        )
    }

    override suspend fun vocabularyAction(roundId: String, vocabularyId: String, action: VocabularyAction) =
        error("Vocabulary actions are unavailable in the matching demo.")

    private fun board(order: Int, words: List<Pair<String, String>>, picture: Boolean = false): Question {
        val left = words.mapIndexed { index, (word, _) -> MatchingItem("$order-$index", word, null) }
        val options = words.mapIndexed { index, (_, value) ->
            if (picture) MatchingItem("$order-$index", null, value)
            else MatchingItem("$order-$index", value, null)
        }
        val permutation = if (picture) listOf(3, 4, 0, 2, 1) else listOf(2, 4, 0, 3, 1)
        val right = permutation.map { options[it] }
        return Question(
            "demo-matching-$order", order, if (picture) "Ghép từ với hình" else "Ghép từ với nghĩa", null, null,
            QuestionContent.Matching(left, right),
        )
    }
}
