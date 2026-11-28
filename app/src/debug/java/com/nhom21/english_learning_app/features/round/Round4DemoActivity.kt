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

private val Context.round4DemoPreferences by preferencesDataStore("round4_demo")

class Round4DemoActivity : ComponentActivity() {
    private val model by viewModels<RoundViewModel> {
        viewModelFactory {
            initializer {
                val repository = Round4DemoRepository()
                RoundViewModel(
                    "round4-demo", GetRoundUseCase(repository), SubmitRoundUseCase(repository),
                    SwipeVocabularyUseCase(repository), CheckAnswerUseCase(repository),
                    applicationContext.round4DemoPreferences,
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
                    onExit = ::finish, onSubmitted = {}, viewModel = model, isApplication = true,
                    applicationImage = { key, modifier -> Round3DemoImage(artwork, key, modifier) },
                )
            }
        }
    }
}

private class Round4DemoRepository : RoundRepository {
    private val questions = listOf(round4Fill, round4Translate, round4Order)
    private val expected = mapOf(
        "fill" to Answer.FillBlank("grapes"),
        "translate" to Answer.Translate(listOf("i", "like", "fruit")),
        "order" to Answer.SentenceOrder(listOf("we", "eat", "them", "after", "lunch")),
    )

    override suspend fun getRound(roundId: String) = ApiResult.Success(
        RoundDetail(RoundSummary(roundId, null, 4, RoundType.APPLICATION, null, ContentStatus.UNLOCKED,
            0, null, 0, questions.size), questions),
    )

    override suspend fun checkAnswer(questionId: String, answer: Answer) =
        ApiResult.Success(grade(questionId, answer))

    override suspend fun submitRound(roundId: String, answers: List<RoundAnswer>): ApiResult<RoundResult> {
        val results = answers.map { grade(it.questionId, it.answer) }
        val count = results.count { it.isCorrect }
        val passed = count * 100 >= questions.size * 70
        val stars = if (passed) 3 else 0
        val percent = (count * 1000 / questions.size) / 10.0
        return ApiResult.Success(RoundResult(
            roundId, questions.size, count, percent, passed, 70, stars, stars, percent, true, 1, results,
            RoundUnlocks(null, false, null, false, null), Streak(0, null, false),
        ))
    }

    private fun grade(questionId: String, answer: Answer): QuestionResult {
        val correct = expected.getValue(questionId)
        val matches = when {
            questionId == "translate" && answer is Answer.TranslateText ->
                answer.text.trim().trimEnd('.').equals("I like strawberries", ignoreCase = true)
            else -> answer == correct
        }
        return QuestionResult(questionId, matches, correct, null)
    }

    override suspend fun vocabularyAction(roundId: String, vocabularyId: String, action: VocabularyAction) =
        error("Vocabulary actions are unavailable in the Round 4 demo.")
}
