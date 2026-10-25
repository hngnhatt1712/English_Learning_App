package com.nhom21.english_learning_app.core.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.nhom21.english_learning_app.core.datastore.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages JWT access token storage in Jetpack DataStore per decision D-A7.
 */
@Singleton
class TokenManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    /**
     * Flow observing the current JWT access token or null if unauthenticated.
     */
    val accessToken: Flow<String?> = dataStore.data.map { preferences ->
        preferences[UserPreferences.KEY_ACCESS_TOKEN]
    }

    /**
     * Persists the given JWT access token.
     */
    suspend fun saveToken(token: String) {
        dataStore.edit { preferences ->
            preferences[UserPreferences.KEY_ACCESS_TOKEN] = token
        }
    }

    /**
     * Clears the stored access token upon logout or session invalidation.
     */
    suspend fun clearToken() {
        dataStore.edit { preferences ->
            preferences.remove(UserPreferences.KEY_ACCESS_TOKEN)
        }
    }

    /**
     * Synchronous token retrieval for OkHttp interceptor invocation on background threads.
     * Allowed usage of runBlocking per decision D-A7.
     */
    fun currentTokenBlocking(): String? = runBlocking {
        accessToken.firstOrNull()
    }

    /**
     * Synchronous token removal for OkHttp interceptor invocation on 401 unauthorized.
     * Allowed usage of runBlocking per decision D-A7.
     */
    fun clearTokenBlocking() = runBlocking {
        clearToken()
    }
}
