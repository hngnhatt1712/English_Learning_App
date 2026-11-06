package com.nhom21.english_learning_app.core.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory test implementation of DataStore<Preferences>.
 * Avoids Windows NTFS File.renameTo collisions in JVM unit tests.
 */
class InMemoryPreferencesDataStore(
    initialPreferences: Preferences = emptyPreferences()
) : DataStore<Preferences> {
    private val mutex = Mutex()
    private val _data = MutableStateFlow(initialPreferences)
    override val data: Flow<Preferences> = _data.asStateFlow()

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        return mutex.withLock {
            val updated = transform(_data.value)
            _data.value = updated
            updated
        }
    }
}
