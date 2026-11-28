package com.nhom21.english_learning_app.features.round

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.core.datastore.UserPreferences
import com.nhom21.english_learning_app.core.navigation.LearnRound
import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.core.ui.components.VocabularyAudioState
import com.nhom21.english_learning_app.domain.model.Answer
import com.nhom21.english_learning_app.domain.model.MatchingPair
import com.nhom21.english_learning_app.domain.model.Question
import com.nhom21.english_learning_app.domain.model.RoundAnswer
import com.nhom21.english_learning_app.domain.model.QuestionContent
import com.nhom21.english_learning_app.domain.model.RoundType
import com.nhom21.english_learning_app.domain.model.Vocabulary
import com.nhom21.english_learning_app.domain.model.VocabularyAction
import com.nhom21.english_learning_app.domain.model.VocabularyStatus
import com.nhom21.english_learning_app.domain.usecase.CheckAnswerUseCase
import com.nhom21.english_learning_app.domain.usecase.GetRoundUseCase
import com.nhom21.english_learning_app.domain.usecase.SubmitRoundUseCase
import com.nhom21.english_learning_app.domain.usecase.SwipeVocabularyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

@HiltViewModel
class RoundViewModel internal constructor(
    private val roundId: String,
    private val getRound: GetRoundUseCase,
    private val submitRound: SubmitRoundUseCase,
    private val swipeVocabulary: SwipeVocabularyUseCase,
    private val checkAnswer: CheckAnswerUseCase,
    private val preferences: DataStore<Preferences>,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        getRound: GetRoundUseCase,
        submitRound: SubmitRoundUseCase,
        swipeVocabulary: SwipeVocabularyUseCase,
        checkAnswer: CheckAnswerUseCase,
        preferences: DataStore<Preferences>,
    ) : this(
        savedStateHandle.toRoute<LearnRound>().roundId,
        getRound,
        submitRound,
        swipeVocabulary,
        checkAnswer,
        preferences,
    )

    private val mutableState = MutableStateFlow<RoundUiState>(Round1UiState.Loading)
    val uiState = mutableState.asStateFlow()

    private data class Action(val wordId: String, val value: VocabularyAction, val undoId: Long? = null)
    private data class Notice(val feedback: Round1Feedback, val action: Action? = null)

    // The deck controls presentation. Only repository responses update the stored vocabulary snapshot.
    private val words = mutableMapOf<String, Vocabulary>()
    private val deck = ArrayDeque<String>()
    private val pending = ArrayDeque<Action>()
    private val notices = ArrayDeque<Notice>()
    private var nextNoticeId = 0L
    private var noticeTimer: Job? = null
    private var syncing = false
    private var loading = false
    private var finishRequested = false
    private var submitting = false
    private var recoveringIncomplete = false
    private var syncError: ApiErrorType? = null
    private var submitError: ApiErrorType? = null
    private var savedStars = 0
    private var resultSequence = 0L

    private var recognitionQuestions = emptyList<Round2Question>()
    private val answers = linkedMapOf<String, RoundAnswer>()
    private var matchingQuestions = emptyList<MatchingQuestion>()
    private var matchingScoringUnits = 0
    private var matchingRequest: Job? = null
    private var matchingFeedbackId = 0L

    init {
        load()
    }

    suspend fun claimSwipeDemo(): Boolean {
        var firstTime = false
        val key = UserPreferences.KEY_ROUND1_SWIPE_DEMO_SEEN
        try {
            preferences.edit {
                if (it[key] != true) {
                    it[key] = true
                    firstTime = true
                }
            }
        } catch (_: IOException) {
            return false
        }
        return firstTime
    }

    fun swipe(wordId: String, swipe: Round1Action) {
        if (mutableState.value !is Round1UiState.Learning || deck.firstOrNull() != wordId) return
        val value = if (swipe == Round1Action.Skip) VocabularyAction.SKIP else VocabularyAction.FLASHCARD
        val id = ++nextNoticeId
        val action = Action(wordId, value, undoId = id)
        val label = when {
            value == VocabularyAction.SKIP -> R.string.round_skip
            words.getValue(wordId).isInFlashcard -> R.string.round_added
            else -> R.string.round_feedback_add_pending
        }
        deck.removeFirst()
        pending.addLast(action)
        addNotice(Notice(Round1Feedback(id, label, canUndo = true), action))
        publish()
        sync()
    }

    fun undo(id: Long) {
        if (submitting || loading) return
        val notice = notices.firstOrNull()?.takeIf { it.feedback.id == id } ?: return
        val original = notice.action ?: return
        val opposite = if (original.value == VocabularyAction.SKIP) VocabularyAction.FLASHCARD
            else VocabularyAction.SKIP
        finishRequested = false
        pending.addLast(Action(original.wordId, opposite))
        deck.addFirst(original.wordId)
        dismissNotice()
        publish()
        sync()
    }

    fun retry() {
        when (val state = mutableState.value) {
            is Round2UiState.Submitting -> {
                if (state.error != null) submitRecognition()
            }
            is Round3UiState.Submitting -> {
                if (state.error != null) submitMatching()
            }
            is Round3UiState.Completed -> load()
            is Round2UiState.Completed, is Round2UiState.Message, Round1UiState.Error -> load()
            is Round1UiState.Learning, is Round1UiState.Finishing -> {
                syncError = null
                publish()
                sync()
            }
            else -> Unit
        }
    }

    fun finish() {
        if (mutableState.value !is Round1UiState.Finishing || finishRequested || submitting) return
        finishRequested = true
        syncError = null
        submitError = null
        publish()
        sync()
        submitWhenReady()
    }

    private fun load() {
        if (loading) return
        loading = true
        mutableState.value = Round1UiState.Loading
        viewModelScope.launch {
            val result = getRound(roundId)
            loading = false
            when (result) {
                is ApiResult.Success -> {
                    val round = result.data.round
                    savedStars = round.stars
                    val questions = result.data.questions
                    if (questions.any { it.content is QuestionContent.Unknown }) {
                        mutableState.value = Round2UiState.Message(RoundMessage.UpdateRequired)
                        return@launch
                    }
                    when (round.type) {
                        RoundType.RECOGNITION -> {
                            startRecognition(questions)
                            return@launch
                        }
                        RoundType.MATCHING -> {
                            startMatching(questions, round.totalQuestions)
                            return@launch
                        }
                        RoundType.LEARN -> Unit
                        else -> {
                            mutableState.value = Round2UiState.Message(RoundMessage.Unsupported)
                            return@launch
                        }
                    }
                    val vocabularies = result.data.questions.sortedBy { it.order }
                        .mapNotNull { (it.content as? QuestionContent.LearnCard)?.vocabulary }
                    words.clear()
                    words.putAll(vocabularies.associateBy { it.id })
                    deck.clear()
                    deck.addAll(words.values.filter { !recoveringIncomplete ||
                        it.status == VocabularyStatus.NEW || it.status == VocabularyStatus.UNKNOWN
                    }.map { it.id })
                    if (recoveringIncomplete && deck.isEmpty()) submitError = ApiErrorType.ROUND_INCOMPLETE
                    recoveringIncomplete = false
                    publish()
                }
                is ApiResult.Failure -> mutableState.value = if (result.error.type == ApiErrorType.ROUND_LOCKED) {
                    Round1UiState.Locked
                } else Round1UiState.Error
            }
        }
    }

    private fun startRecognition(questions: List<Question>) {
        answers.clear()
        recognitionQuestions = questions.sortedBy { it.order }.map { question ->
            val variant: Round2Variant
            val options = when (val content = question.content) {
                is QuestionContent.MultipleChoice -> {
                    variant = Round2Variant.Text
                    content.options.map { Round2Option(it.id, label = it.text) }
                }
                is QuestionContent.PictureChoice -> {
                    variant = Round2Variant.Picture
                    content.options.map { Round2Option(it.id, imageUrl = it.imageUrl) }
                }
                else -> {
                    mutableState.value = Round2UiState.Message(RoundMessage.Unsupported)
                    return
                }
            }
            if (options.isEmpty() || options.distinctBy { it.id }.size != options.size) {
                mutableState.value = Round2UiState.Message(RoundMessage.InvalidContent)
                return
            }
            Round2Question(question.id, variant, question.prompt, options,
                imageUrl = question.imageUrl, audioUrl = question.audioUrl)
        }
        mutableState.value = when {
            recognitionQuestions.isEmpty() -> Round2UiState.Message(RoundMessage.Empty)
            recognitionQuestions.distinctBy { it.id }.size != recognitionQuestions.size ->
                Round2UiState.Message(RoundMessage.InvalidContent)
            else -> recognitionQuestion(0)
        }
    }

    private fun recognitionQuestion(index: Int) =
        Round2UiState.Question(
            recognitionQuestions[index], index + 1, recognitionQuestions.size, savedStars = savedStars
        )

    fun selectOption(questionId: String, optionId: String) {
        val state = editableQuestion(questionId) ?: return
        if (optionId == state.selectedOptionId || state.question.options.none { it.id == optionId }) return
        mutableState.value = state.copy(selectedOptionId = optionId, checkError = null, networkFailures = 0)
    }

    private fun editableQuestion(id: String): Round2UiState.Question? =
        (mutableState.value as? Round2UiState.Question)?.takeIf {
            it.question.id == id && !it.checking && it.feedback == null
        }

    private fun Round2UiState.Question.selectedAnswer(): Answer? = selectedOptionId?.let {
        if (question.variant == Round2Variant.Picture) Answer.PictureChoice(it) else Answer.MultipleChoice(it)
    }

    fun checkOption(questionId: String) {
        val state = editableQuestion(questionId) ?: return
        val answer = state.selectedAnswer() ?: return
        mutableState.value = state.copy(checking = true, checkError = null)
        viewModelScope.launch {
            when (val response = checkAnswer(questionId, answer)) {
                is ApiResult.Success -> {
                    val result = response.data
                    val correctId = when (val correct = result.correctAnswer) {
                        is Answer.MultipleChoice -> correct.optionId.takeIf { answer is Answer.MultipleChoice }
                        is Answer.PictureChoice -> correct.optionId.takeIf { answer is Answer.PictureChoice }
                        else -> null
                    }
                    if (result.questionId != questionId || state.question.options.none { it.id == correctId }) {
                        mutableState.value = Round2UiState.Message(RoundMessage.InvalidContent)
                        return@launch
                    }
                    answers[questionId] = RoundAnswer(questionId, answer)
                    mutableState.value = state.copy(feedback = Round2Feedback(result.isCorrect, correctId), checkError = null)
                }
                is ApiResult.Failure -> {
                    val type = response.error.type
                    mutableState.value = if (type == ApiErrorType.ROUND_LOCKED) {
                        Round2UiState.Message(RoundMessage.Locked)
                    } else {
                        state.copy(
                            checkError = type,
                            networkFailures = if (type == ApiErrorType.NETWORK) state.networkFailures + 1 else 0,
                        )
                    }
                }
            }
        }
    }

    fun nextQuestion(questionId: String) {
        val state = mutableState.value as? Round2UiState.Question ?: return
        if (state.question.id != questionId || state.checking) return
        if (state.feedback == null) {
            if (state.networkFailures < 2 || state.checkError != ApiErrorType.NETWORK) return
            val answer = state.selectedAnswer() ?: return
            answers[questionId] = RoundAnswer(questionId, answer)
        }
        if (state.number < state.total) mutableState.value = recognitionQuestion(state.number)
        else submitRecognition()
    }

    private fun submitRecognition() {
        val payload = recognitionQuestions.map { answers.getValue(it.id) }
        mutableState.value = Round2UiState.Submitting()
        viewModelScope.launch {
            mutableState.value = when (val response = submitRound(roundId, payload)) {
                is ApiResult.Success -> with(response.data) {
                    Round2UiState.Completed(
                        correctCount, totalQuestions, stars, passed, scorePercent, unlocks, streak,
                        resultId = ++resultSequence,
                    )
                }
                is ApiResult.Failure -> if (response.error.type == ApiErrorType.ROUND_LOCKED) {
                    Round2UiState.Message(RoundMessage.Locked)
                } else Round2UiState.Submitting(response.error.type)
            }
        }
    }

    private fun startMatching(questions: List<Question>, totalQuestions: Int) {
        if (questions.isEmpty()) {
            mutableState.value = Round2UiState.Message(RoundMessage.Empty)
            return
        }
        if (questions.distinctBy { it.id }.size != questions.size) {
            mutableState.value = Round2UiState.Message(RoundMessage.InvalidContent)
            return
        }
        matchingQuestions = questions.sortedBy { it.order }.map { question ->
            val content = question.content as? QuestionContent.Matching
            if (content == null || content.left.isEmpty() || content.left.size != content.right.size ||
                content.left.distinctBy { it.id }.size != content.left.size ||
                content.right.distinctBy { it.id }.size != content.right.size
            ) {
                mutableState.value = Round2UiState.Message(RoundMessage.InvalidContent)
                return
            }
            MatchingQuestion(question.id, question.prompt, content.left, content.right)
        }
        answers.clear()
        matchingScoringUnits = totalQuestions
        mutableState.value = matchingQuestion(0)
    }

    private fun matchingQuestion(index: Int) = Round3UiState.Question(
        matchingQuestions[index], index + 1, matchingQuestions.size, matchingScoringUnits, savedStars,
    )

    fun selectMatchingLeft(questionId: String, leftId: String) {
        val state = editableMatching(questionId) ?: return
        if (state.question.left.none { it.id == leftId } || state.pairs.any { it.leftId == leftId }) return
        selectMatching(state.copy(selectedLeftId = leftId))
    }

    fun selectMatchingRight(questionId: String, rightId: String) {
        val state = editableMatching(questionId) ?: return
        if (state.question.right.none { it.id == rightId } || state.pairs.any { it.rightId == rightId }) return
        selectMatching(state.copy(selectedRightId = rightId))
    }

    private fun editableMatching(questionId: String): Round3UiState.Question? =
        (mutableState.value as? Round3UiState.Question)?.takeIf {
            it.question.id == questionId && it.pendingPair == null && it.feedback?.isCorrect != false
        }

    private fun selectMatching(selection: Round3UiState.Question) {
        val state = selection.copy(feedback = null, checkError = null)
        val left = state.selectedLeftId
        val right = state.selectedRightId
        if (left == null || right == null) {
            mutableState.value = state
            return
        }
        val pair = MatchingPair(left, right)
        val pending = state.copy(pendingPair = pair)
        val ready = state.copy(selectedLeftId = null, selectedRightId = null)
        val questionId = state.question.id
        mutableState.value = pending
        matchingRequest = viewModelScope.launch {
            try {
                val response = checkAnswer(questionId, Answer.MatchingCheck(pair))
                ensureActive()
                if (mutableState.value !== pending) return@launch
                mutableState.value = when (response) {
                    is ApiResult.Success -> {
                        val result = response.data
                        if (result.questionId != questionId || result.correctAnswer != null ||
                            result.explanation != null || result.pairResults != null
                        ) {
                            ready.copy(checkError = ApiErrorType.UNKNOWN)
                        } else {
                            ready.copy(
                                feedback = MatchingFeedback(++matchingFeedbackId, pair, result.isCorrect),
                                pairs = if (result.isCorrect) state.pairs + pair else state.pairs,
                                attempts = state.attempts + pair,
                            )
                        }
                    }
                    is ApiResult.Failure -> if (response.error.type == ApiErrorType.ROUND_LOCKED) {
                        Round2UiState.Message(RoundMessage.Locked)
                    } else ready.copy(checkError = response.error.type)
                }
            } catch (cancelled: CancellationException) {
                if (mutableState.value === pending) mutableState.value = ready.copy(checkError = ApiErrorType.NETWORK)
                throw cancelled
            }
        }
    }

    fun acknowledgeMatchingFeedback(questionId: String, feedbackId: Long) {
        val state = mutableState.value as? Round3UiState.Question ?: return
        if (state.question.id == questionId && state.feedback?.id == feedbackId) {
            mutableState.value = state.copy(feedback = null)
        }
    }

    fun nextMatchingQuestion(questionId: String) {
        val state = editableMatching(questionId) ?: return
        if (!state.canContinue) return
        answers[questionId] = RoundAnswer(questionId, Answer.Matching(state.pairs, state.attempts))
        if (state.number < state.questionCount) mutableState.value = matchingQuestion(state.number)
        else submitMatching()
    }

    private fun submitMatching() {
        val payload = matchingQuestions.map { answers.getValue(it.id) }
        val pending = Round3UiState.Submitting()
        mutableState.value = pending
        matchingRequest = viewModelScope.launch {
            try {
                val response = submitRound(roundId, payload)
                ensureActive()
                if (mutableState.value !== pending) return@launch
                mutableState.value = when (response) {
                    is ApiResult.Success -> if (response.data.roundId == roundId) {
                        Round3UiState.Completed(response.data, ++resultSequence)
                    } else Round3UiState.Submitting(ApiErrorType.UNKNOWN)
                    is ApiResult.Failure -> if (response.error.type == ApiErrorType.ROUND_LOCKED) {
                        Round2UiState.Message(RoundMessage.Locked)
                    } else Round3UiState.Submitting(response.error.type)
                }
            } catch (cancelled: CancellationException) {
                if (mutableState.value === pending) mutableState.value = Round3UiState.Submitting(ApiErrorType.NETWORK)
                throw cancelled
            }
        }
    }

    fun leaveMatching() {
        if (mutableState.value !is Round3UiState) return
        mutableState.value = Round3UiState.Exited
        matchingRequest?.cancel()
    }

    private fun sync() {
        if (syncing || syncError != null || pending.isEmpty()) return
        syncing = true
        viewModelScope.launch {
            while (pending.isNotEmpty()) {
                val action = pending.first()
                when (val result = swipeVocabulary(roundId, action.wordId, action.value)) {
                    is ApiResult.Success -> {
                        val saved = result.data
                        if (saved.vocabularyId != action.wordId) {
                            syncError = ApiErrorType.UNKNOWN
                            break
                        }
                        val previous = words.getValue(action.wordId)
                        words[action.wordId] = previous.copy(status = saved.status, isInFlashcard = saved.isInFlashcard)
                        pending.removeFirst()
                        if (pending.none { it.wordId == action.wordId }) {
                            if (!previous.isInFlashcard && saved.isInFlashcard && action.value == VocabularyAction.FLASHCARD) {
                                confirmAdd(action.undoId)
                            } else if (action.value == VocabularyAction.SKIP) {
                                if (saved.isInFlashcard) {
                                    confirm(action.undoId, R.string.round_feedback_kept)
                                } else if (action.undoId != null) {
                                    updateNotice(action.undoId, R.string.round_feedback_skipped)
                                }
                            }
                        }
                    }
                    is ApiResult.Failure -> {
                        syncError = result.error.type
                        break
                    }
                }
                publish()
            }
            syncing = false
            if (syncError != null) finishRequested = false
            if (syncError == ApiErrorType.ROUND_LOCKED) {
                clearNotices()
                mutableState.value = Round1UiState.Locked
            } else {
                publish()
                submitWhenReady()
            }
        }
    }

    private fun submitWhenReady() {
        if (!finishRequested || submitting || loading || syncing || pending.isNotEmpty() ||
            deck.isNotEmpty() || notices.isNotEmpty()
        ) return
        submitting = true
        publish()
        viewModelScope.launch {
            val result = submitRound(roundId, emptyList())
            submitting = false
            finishRequested = false
            when (result) {
                is ApiResult.Success -> {
                    clearNotices()
                    mutableState.value = Round1UiState.Completed(
                        result.data.totalQuestions, result.data.stars, resultId = ++resultSequence,
                    )
                }
                is ApiResult.Failure -> when (result.error.type) {
                    ApiErrorType.ROUND_INCOMPLETE -> {
                        clearNotices()
                        recoveringIncomplete = true
                        load()
                    }
                    ApiErrorType.ROUND_LOCKED -> {
                        clearNotices()
                        mutableState.value = Round1UiState.Locked
                    }
                    else -> {
                        submitError = result.error.type
                        publish()
                    }
                }
            }
        }
    }

    private fun confirmAdd(id: Long?) = confirm(id, R.string.round_feedback_added)

    private fun confirm(id: Long?, label: Int) {
        if (!updateNotice(id, label)) addNotice(Notice(Round1Feedback(++nextNoticeId, label)))
    }

    private fun updateNotice(id: Long?, label: Int): Boolean {
        val index = notices.indexOfFirst { it.feedback.id == id }
        if (index < 0) return false
        val notice = notices[index]
        notices[index] = notice.copy(feedback = notice.feedback.copy(message = label))
        return true
    }

    private fun addNotice(notice: Notice) {
        notices.addLast(notice)
        if (notices.size == 1) startNoticeTimer()
    }

    private fun startNoticeTimer() {
        if (notices.isEmpty()) return
        noticeTimer = viewModelScope.launch {
            delay(3_000)
            noticeTimer = null
            dismissNotice()
            if (!loading && mutableState.value !is Round1UiState.Completed) {
                publish()
                submitWhenReady()
            }
        }
    }

    private fun dismissNotice() {
        noticeTimer?.cancel()
        noticeTimer = null
        notices.removeFirst()
        startNoticeTimer()
    }

    private fun clearNotices() {
        noticeTimer?.cancel()
        noticeTimer = null
        notices.clear()
    }

    private fun publish() {
        val feedback = notices.firstOrNull()?.feedback
        val word = deck.firstOrNull()?.let(words::getValue)
        mutableState.value = when {
            word != null -> Round1UiState.Learning(
                word = word.toPresentation(),
                remainingCardsBehind = deck.size - 1,
                processedWords = words.size - deck.size,
                totalWords = words.size,
                audio = if (word.audioUrl.isBlank()) VocabularyAudioState.Unavailable else VocabularyAudioState.Ready,
                hasPendingActions = pending.isNotEmpty(),
                feedback = feedback,
                syncError = syncError?.messageId(),
                savedStars = savedStars,
            )
            words.isEmpty() -> Round1UiState.Empty
            else -> Round1UiState.Finishing(
                totalWords = words.size,
                status = when {
                    finishRequested || submitting -> Round1FinishStatus.Saving
                    syncError != null || submitError != null -> Round1FinishStatus.Error
                    else -> Round1FinishStatus.Ready
                },
                feedback = feedback,
                errorMessage = (syncError ?: submitError)?.messageId() ?: R.string.round_error,
                hasPendingActions = pending.isNotEmpty(),
                savedStars = savedStars,
            )
        }
    }

    private fun ApiErrorType.messageId() = when (this) {
        ApiErrorType.NETWORK -> R.string.round_network_error
        ApiErrorType.ROUND_INCOMPLETE -> R.string.round_incomplete_error
        else -> R.string.round_error
    }

    private fun Vocabulary.toPresentation() = Round1Word(
        id, word, partOfSpeech, phonetic, meaning, example, imageUrl, isInFlashcard, audioUrl
    )
}
