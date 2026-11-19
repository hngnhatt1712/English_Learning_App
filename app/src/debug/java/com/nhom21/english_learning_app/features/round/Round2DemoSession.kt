package com.nhom21.english_learning_app.features.round

internal class Round2DemoSession {
    private var index = 0
    private var selectedOptionId: String? = null
    private var feedback: Round2Feedback? = null
    private var correctCount = 0

    fun state(): Round2UiState {
        val item = round2DemoItems.getOrNull(index)
        if (item != null) {
            return Round2UiState.Question(item.question, index + 1, round2DemoItems.size, selectedOptionId, feedback)
        }

        // These thresholds are only used for the local preview of the result screen.
        val total = round2DemoItems.size
        val stars = when {
            correctCount == total -> 3
            correctCount * 100 >= total * 80 -> 2
            correctCount * 100 >= total * 70 -> 1
            else -> 0
        }
        return Round2UiState.Completed(correctCount, total, stars, passed = stars > 0)
    }

    fun select(questionId: String, optionId: String): Boolean {
        val question = round2DemoItems.getOrNull(index)?.question ?: return false
        if (question.id != questionId || feedback != null || question.options.none { it.id == optionId }) return false
        selectedOptionId = optionId
        return true
    }

    fun check(questionId: String): Boolean {
        val item = round2DemoItems.getOrNull(index) ?: return false
        val selection = selectedOptionId ?: return false
        if (item.question.id != questionId || feedback != null) return false

        val isCorrect = selection == item.correctOptionId
        feedback = Round2Feedback(isCorrect, item.correctOptionId)
        if (isCorrect) correctCount++
        return true
    }

    fun next(questionId: String): Boolean {
        if (round2DemoItems.getOrNull(index)?.question?.id != questionId || feedback == null) return false
        index++
        selectedOptionId = null
        feedback = null
        return true
    }
}
