package com.nhom21.english_learning_app.features.round

import com.nhom21.english_learning_app.domain.model.Question
import com.nhom21.english_learning_app.domain.model.QuestionContent
import com.nhom21.english_learning_app.domain.model.TextOption
import com.nhom21.english_learning_app.domain.model.TranslationDirection

internal val round4Fill = Question(
    "fill", 1, "", "grape", null,
    QuestionContent.FillBlank("These are ___.", listOf("grapes", "pears", "cherries")),
)
internal val round4Translate = Question(
    "translate", 2, "", "strawberry", null,
    QuestionContent.Translate(
        "Tôi thích dâu tây.", TranslationDirection.VI_TO_EN,
        listOf(TextOption("like", "like"), TextOption("i", "I"), TextOption("fruit", "strawberries.")),
    ),
)
internal val round4Order = Question(
    "order", 3, "", null, null,
    QuestionContent.SentenceOrder(
        listOf(TextOption("lunch", "lunch."), TextOption("them", "them"), TextOption("eat", "eat"),
            TextOption("after", "after"), TextOption("we", "We")),
    ),
)
