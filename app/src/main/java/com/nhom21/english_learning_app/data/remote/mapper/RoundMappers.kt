package com.nhom21.english_learning_app.data.remote.mapper

import com.nhom21.english_learning_app.data.remote.dto.AnswerDto
import com.nhom21.english_learning_app.data.remote.dto.FillBlankAnswerDto
import com.nhom21.english_learning_app.data.remote.dto.FillBlankQuestionDto
import com.nhom21.english_learning_app.data.remote.dto.LearnCardQuestionDto
import com.nhom21.english_learning_app.data.remote.dto.MatchingAnswerDto
import com.nhom21.english_learning_app.data.remote.dto.MatchingItemDto
import com.nhom21.english_learning_app.data.remote.dto.MatchingPairDto
import com.nhom21.english_learning_app.data.remote.dto.MatchingQuestionDto
import com.nhom21.english_learning_app.data.remote.dto.MultipleChoiceAnswerDto
import com.nhom21.english_learning_app.data.remote.dto.MultipleChoiceQuestionDto
import com.nhom21.english_learning_app.data.remote.dto.PictureChoiceAnswerDto
import com.nhom21.english_learning_app.data.remote.dto.PictureChoiceQuestionDto
import com.nhom21.english_learning_app.data.remote.dto.QuestionDto
import com.nhom21.english_learning_app.data.remote.dto.QuestionResultDto
import com.nhom21.english_learning_app.data.remote.dto.RoundDetailDto
import com.nhom21.english_learning_app.data.remote.dto.RoundDto
import com.nhom21.english_learning_app.data.remote.dto.RoundResultDto
import com.nhom21.english_learning_app.data.remote.dto.SentenceCompletionAnswerDto
import com.nhom21.english_learning_app.data.remote.dto.SentenceCompletionQuestionDto
import com.nhom21.english_learning_app.data.remote.dto.SentenceCorrectionAnswerDto
import com.nhom21.english_learning_app.data.remote.dto.SentenceCorrectionQuestionDto
import com.nhom21.english_learning_app.data.remote.dto.SentenceOrderAnswerDto
import com.nhom21.english_learning_app.data.remote.dto.SentenceOrderQuestionDto
import com.nhom21.english_learning_app.data.remote.dto.SubmitRoundAnswer
import com.nhom21.english_learning_app.data.remote.dto.TextOptionDto
import com.nhom21.english_learning_app.data.remote.dto.TranslateAnswerDto
import com.nhom21.english_learning_app.data.remote.dto.TranslateQuestionDto
import com.nhom21.english_learning_app.data.remote.dto.TranslationDirectionDto
import com.nhom21.english_learning_app.data.remote.dto.UnknownQuestionDto
import com.nhom21.english_learning_app.data.remote.dto.VocabularyActionDto
import com.nhom21.english_learning_app.data.remote.dto.VocabularyActionResultDto
import com.nhom21.english_learning_app.data.remote.dto.VocabularyDto
import com.nhom21.english_learning_app.data.remote.dto.VocabularyStatusDto
import com.nhom21.english_learning_app.domain.model.Answer
import com.nhom21.english_learning_app.domain.model.MatchingItem
import com.nhom21.english_learning_app.domain.model.MatchingPair
import com.nhom21.english_learning_app.domain.model.PictureOption
import com.nhom21.english_learning_app.domain.model.Question
import com.nhom21.english_learning_app.domain.model.QuestionContent
import com.nhom21.english_learning_app.domain.model.QuestionResult
import com.nhom21.english_learning_app.domain.model.RoundAnswer
import com.nhom21.english_learning_app.domain.model.RoundDetail
import com.nhom21.english_learning_app.domain.model.RoundResult
import com.nhom21.english_learning_app.domain.model.RoundUnlocks
import com.nhom21.english_learning_app.domain.model.Streak
import com.nhom21.english_learning_app.domain.model.TextOption
import com.nhom21.english_learning_app.domain.model.TranslationDirection
import com.nhom21.english_learning_app.domain.model.Vocabulary
import com.nhom21.english_learning_app.domain.model.VocabularyAction
import com.nhom21.english_learning_app.domain.model.VocabularyActionResult
import com.nhom21.english_learning_app.domain.model.VocabularyStatus

internal fun RoundDetailDto.toDomain(): RoundDetail = RoundDetail(
    round = RoundDto(
        id = id,
        lessonId = lessonId,
        roundNumber = roundNumber,
        type = type,
        title = title,
        status = status,
        stars = stars,
        bestScorePercent = bestScorePercent,
        attempts = attempts,
        totalQuestions = totalQuestions,
    ).toDomain(),
    questions = questions.map(QuestionDto::toDomain),
)

private fun QuestionDto.toDomain(): Question {
    val content = when (this) {
        is LearnCardQuestionDto -> QuestionContent.LearnCard(vocabulary.toDomain())
        is MultipleChoiceQuestionDto -> QuestionContent.MultipleChoice(options.map(TextOptionDto::toDomain))
        is PictureChoiceQuestionDto -> QuestionContent.PictureChoice(options.map { PictureOption(it.id, it.imageUrl) })
        is MatchingQuestionDto -> QuestionContent.Matching(left.map(MatchingItemDto::toDomain), right.map(MatchingItemDto::toDomain))
        is FillBlankQuestionDto -> QuestionContent.FillBlank(sentence, wordBank)
        is TranslateQuestionDto -> QuestionContent.Translate(sourceText, direction.toDomain(), tokens.map(TextOptionDto::toDomain))
        is SentenceOrderQuestionDto -> QuestionContent.SentenceOrder(tokens.map(TextOptionDto::toDomain))
        is SentenceCorrectionQuestionDto -> QuestionContent.SentenceCorrection(options.map(TextOptionDto::toDomain))
        is SentenceCompletionQuestionDto -> QuestionContent.SentenceCompletion(sentence, options.map(TextOptionDto::toDomain))
        is UnknownQuestionDto -> QuestionContent.Unknown(originalType)
    }
    return Question(id, order, prompt, imageUrl, audioUrl, content)
}

private fun TextOptionDto.toDomain() = TextOption(id, text)
private fun MatchingItemDto.toDomain() = MatchingItem(id, text, imageUrl)

private fun TranslationDirectionDto.toDomain(): TranslationDirection = when (this) {
    TranslationDirectionDto.EN_TO_VI -> TranslationDirection.EN_TO_VI
    TranslationDirectionDto.VI_TO_EN -> TranslationDirection.VI_TO_EN
    TranslationDirectionDto.UNKNOWN -> TranslationDirection.UNKNOWN
}

internal fun Answer.toDto(): AnswerDto = when (this) {
    is Answer.MultipleChoice -> MultipleChoiceAnswerDto(optionId)
    is Answer.PictureChoice -> PictureChoiceAnswerDto(optionId)
    is Answer.Matching -> MatchingAnswerDto(pairs.map { MatchingPairDto(it.leftId, it.rightId) })
    is Answer.FillBlank -> FillBlankAnswerDto(text)
    is Answer.Translate -> TranslateAnswerDto(tokenIds)
    is Answer.SentenceOrder -> SentenceOrderAnswerDto(tokenIds)
    is Answer.SentenceCorrection -> SentenceCorrectionAnswerDto(optionId)
    is Answer.SentenceCompletion -> SentenceCompletionAnswerDto(optionId)
}

private fun AnswerDto.toDomain(): Answer = when (this) {
    is MultipleChoiceAnswerDto -> Answer.MultipleChoice(optionId)
    is PictureChoiceAnswerDto -> Answer.PictureChoice(optionId)
    is MatchingAnswerDto -> Answer.Matching(pairs.map { MatchingPair(it.leftId, it.rightId) })
    is FillBlankAnswerDto -> Answer.FillBlank(text)
    is TranslateAnswerDto -> Answer.Translate(tokenIds)
    is SentenceOrderAnswerDto -> Answer.SentenceOrder(tokenIds)
    is SentenceCorrectionAnswerDto -> Answer.SentenceCorrection(optionId)
    is SentenceCompletionAnswerDto -> Answer.SentenceCompletion(optionId)
}

internal fun RoundAnswer.toDto() = SubmitRoundAnswer(questionId, answer.toDto())

internal fun QuestionResultDto.toDomain() = QuestionResult(questionId, isCorrect, correctAnswer.toDomain(), explanation)

internal fun RoundResultDto.toDomain() = RoundResult(
    roundId = roundId,
    totalQuestions = totalQuestions,
    correctCount = correctCount,
    scorePercent = scorePercent,
    passed = passed,
    passThresholdPercent = passThresholdPercent,
    stars = stars,
    bestStars = bestStars,
    bestScorePercent = bestScorePercent,
    isNewBest = isNewBest,
    attempts = attempts,
    results = results.map(QuestionResultDto::toDomain),
    unlocks = RoundUnlocks(
        nextRoundId = unlocks.nextRoundId,
        topicCompleted = unlocks.topicCompleted,
        nextTopicId = unlocks.nextTopicId,
        levelCompleted = unlocks.levelCompleted,
        nextLevelId = unlocks.nextLevelId,
    ),
    streak = Streak(streak.current, streak.lastLearningDate, streak.learnedToday),
)

private fun VocabularyDto.toDomain() = Vocabulary(
    id = id,
    topicId = topicId,
    levelId = levelId,
    word = word,
    meaning = meaning,
    partOfSpeech = partOfSpeech,
    phonetic = phonetic,
    example = example,
    imageUrl = imageUrl,
    audioUrl = audioUrl,
    status = status.toDomain(),
    isInFlashcard = isInFlashcard,
)

internal fun VocabularyAction.toDto(): VocabularyActionDto = when (this) {
    VocabularyAction.SKIP -> VocabularyActionDto.SKIP
    VocabularyAction.FLASHCARD -> VocabularyActionDto.FLASHCARD
}

internal fun VocabularyActionResultDto.toDomain() =
    VocabularyActionResult(vocabularyId, status.toDomain(), isInFlashcard)

private fun VocabularyStatusDto.toDomain(): VocabularyStatus = when (this) {
    VocabularyStatusDto.NEW -> VocabularyStatus.NEW
    VocabularyStatusDto.SKIPPED -> VocabularyStatus.SKIPPED
    VocabularyStatusDto.IN_FLASHCARD -> VocabularyStatus.IN_FLASHCARD
    VocabularyStatusDto.LEARNING -> VocabularyStatus.LEARNING
    VocabularyStatusDto.REVIEW -> VocabularyStatus.REVIEW
    VocabularyStatusDto.MASTERED -> VocabularyStatus.MASTERED
    VocabularyStatusDto.UNKNOWN -> VocabularyStatus.UNKNOWN
}
