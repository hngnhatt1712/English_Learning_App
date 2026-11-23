package com.nhom21.english_learning_app.core.navigation

import kotlin.reflect.KClass

/** The only destinations that display the shared bottom bar. */
enum class MainTab(val destination: Any) {
    HOME(Home),
    LESSON(Lesson),
    PRACTICE(Practice),
    PROFILE(Profile)
}

/** Whitelist: Auth, future Round/Result, and all other destinations have no tab/bar. */
internal fun mainTabMatching(matchesRoute: (KClass<*>) -> Boolean): MainTab? =
    MainTab.entries.firstOrNull { matchesRoute(it.destination::class) }
