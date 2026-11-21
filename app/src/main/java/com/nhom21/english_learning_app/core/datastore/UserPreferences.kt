package com.nhom21.english_learning_app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * Extension property to access user preferences DataStore.
 */
val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = UserPreferences.DATASTORE_NAME)

/**
 * Constants and keys for DataStore persistence per decision D-A16.
 */
object UserPreferences {
    const val DATASTORE_NAME = "user_prefs"
    val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
    val KEY_ROUND1_SWIPE_DEMO_SEEN = booleanPreferencesKey("round1_swipe_demo_seen")
}
