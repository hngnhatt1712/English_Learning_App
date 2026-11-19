package com.nhom21.english_learning_app.features.round

import com.nhom21.english_learning_app.R

internal val round1DemoWords = listOf(
    Round1Word("apple", "apple", "noun", "/ˈæp.əl/", "quả táo"),
    Round1Word("banana", "banana", "noun", "/bəˈnɑː.nə/", "quả chuối"),
    Round1Word("orange", "orange", "noun", "/ˈɒr.ɪndʒ/", "quả cam", isInFlashcards = true),
    Round1Word("grape", "grape", "noun", "/ɡreɪp/", "quả nho"),
    Round1Word("mango", "mango", "noun", "/ˈmæŋ.ɡəʊ/", "quả xoài"),
    Round1Word("pear", "pear", "noun", "/peə(r)/", "quả lê"),
    Round1Word("lemon", "lemon", "noun", "/ˈlem.ən/", "quả chanh vàng"),
    Round1Word("peach", "peach", "noun", "/piːtʃ/", "quả đào"),
    Round1Word("pineapple", "pineapple", "noun", "/ˈpaɪnˌæp.əl/", "quả dứa"),
    Round1Word("watermelon", "watermelon", "noun", "/ˈwɔː.təˌmel.ən/", "quả dưa hấu")
)

/** Holds a single demo run in memory. Vocabulary IDs are stable across Undo and replay. */
internal class Round1DemoSession(private val words: List<Round1Word> = round1DemoWords) {
    private var queue = words
    private val decisions = mutableMapOf<String, Round1Action>()
    private var feedbackId = 0L
    private var lastAction: Pair<Round1Word, Round1Action>? = null
    private var feedback: Round1Feedback? = null
    private var audio = RoundAudioState.Ready
    private var finishing = false
    private var completed = false

    fun state(): Round1UiState {
        val current = queue.firstOrNull()
        return when {
            words.isEmpty() -> Round1UiState.Empty
            completed -> Round1UiState.Completed(
                learnedWords = decisions.size,
                stars = 3,
                addedWords = decisions.values.count { it == Round1Action.Add },
                skippedWords = decisions.values.count { it == Round1Action.Skip }
            )
            current == null -> Round1UiState.Finishing(
                totalWords = words.size,
                status = if (finishing) Round1FinishStatus.Saving else Round1FinishStatus.Ready,
                feedback = feedback
            )
            else -> Round1UiState.Learning(
                word = current,
                remainingCardsBehind = queue.size - 1,
                processedWords = decisions.size,
                totalWords = words.size,
                audio = audio,
                feedback = feedback
            )
        }
    }

    fun onCardAction(id: String, action: Round1Action): Boolean {
        val current = queue.firstOrNull() ?: return false
        if (current.id != id) return false
        if (action == Round1Action.Add && current.isInFlashcards) return false
        if (action == Round1Action.KeepAdded && !current.isInFlashcards) return false

        val originallySaved = words.first { it.id == id }.isInFlashcards
        decisions[id] = if (action == Round1Action.KeepAdded && !originallySaved) Round1Action.Add else action
        queue = queue.drop(1)
        audio = RoundAudioState.Ready
        lastAction = (current to action).takeIf { action != Round1Action.KeepAdded && !originallySaved }
        feedback = when (action) {
            Round1Action.Skip -> Round1Feedback(++feedbackId, R.string.round_feedback_skipped, lastAction != null)
            Round1Action.Add -> Round1Feedback(++feedbackId, R.string.round_feedback_added, lastAction != null)
            Round1Action.KeepAdded -> null
        }
        return true
    }

    fun undo(id: Long): Boolean {
        val (word, action) = lastAction ?: return false
        if (feedback?.id != id || finishing || completed) return false

        val inverse = if (action == Round1Action.Add) Round1Action.Skip else Round1Action.Add
        decisions[word.id] = inverse
        queue = listOf(word.copy(isInFlashcards = inverse == Round1Action.Add)) + queue
        audio = RoundAudioState.Ready
        lastAction = null
        feedback = null
        return true
    }

    fun playAudio(id: String): Boolean {
        if (queue.firstOrNull()?.id != id || audio == RoundAudioState.Playing) return false
        audio = RoundAudioState.Playing
        return true
    }

    fun stopAudio() {
        audio = RoundAudioState.Ready
    }

    fun beginFinish(): Boolean {
        if (queue.isNotEmpty() || words.isEmpty() || finishing || completed) return false
        finishing = true
        lastAction = null
        feedback = null
        return true
    }

    fun complete() {
        if (finishing) {
            finishing = false
            completed = true
        }
    }
}
