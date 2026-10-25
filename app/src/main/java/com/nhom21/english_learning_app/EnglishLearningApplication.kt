package com.nhom21.english_learning_app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class annotated with [HiltAndroidApp] to enable
 * Hilt dependency injection throughout the app.
 */
@HiltAndroidApp
class EnglishLearningApplication : Application()
