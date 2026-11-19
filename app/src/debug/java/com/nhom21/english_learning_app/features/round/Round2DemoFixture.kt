package com.nhom21.english_learning_app.features.round

import androidx.annotation.DrawableRes
import com.nhom21.english_learning_app.features.round.DemoWord.Apple
import com.nhom21.english_learning_app.features.round.DemoWord.Banana
import com.nhom21.english_learning_app.features.round.DemoWord.Bread
import com.nhom21.english_learning_app.features.round.DemoWord.Milk
import com.nhom21.english_learning_app.features.round.DemoWord.Water

internal data class Round2DemoItem(val question: Round2Question, val correctOptionId: String)

private enum class DemoWord(val english: String, val vietnamese: String, @param:DrawableRes val image: Int? = null) {
    Apple("apple", "quả táo"),
    Banana("banana", "quả chuối"),
    Milk("milk", "sữa"),
    Water("water", "nước"),
    Bread("bread", "bánh mì")
}

private fun demoItem(
    variant: Round2Variant,
    answer: DemoWord,
    vararg words: DemoWord
): Round2DemoItem {
    val id = "${variant.name.lowercase()}-${answer.english}"
    val options = words.map { word ->
        Round2Option(
            id = "$id-${word.english}",
            label = when (variant) {
                Round2Variant.Meaning -> word.vietnamese
                Round2Variant.Word -> word.english
                Round2Variant.Picture -> null
            },
            imageRes = word.image.takeIf { variant == Round2Variant.Picture }
        )
    }
    return Round2DemoItem(
        question = Round2Question(
            id = id,
            variant = variant,
            prompt = if (variant == Round2Variant.Word) answer.vietnamese else answer.english,
            options = options,
            illustrationRes = answer.image.takeIf { variant == Round2Variant.Meaning }
        ),
        correctOptionId = "$id-${answer.english}"
    )
}

internal val round2DemoItems = listOf(
    demoItem(Round2Variant.Meaning, Apple, Apple, Banana, Milk, Water),
    demoItem(Round2Variant.Meaning, Banana, Milk, Banana, Apple, Bread),
    demoItem(Round2Variant.Meaning, Milk, Water, Apple, Milk, Bread),
    demoItem(Round2Variant.Word, Bread, Apple, Water, Banana, Bread),
    demoItem(Round2Variant.Word, Water, Milk, Water, Bread, Apple),
    demoItem(Round2Variant.Picture, Apple, Apple, Banana, Milk, Water),
    demoItem(Round2Variant.Picture, Banana, Water, Bread, Banana, Apple),
    demoItem(Round2Variant.Picture, Milk, Banana, Water, Bread, Milk),
    demoItem(Round2Variant.Picture, Water, Apple, Water, Milk, Bread),
    demoItem(Round2Variant.Picture, Bread, Bread, Banana, Apple, Milk)
)
