package com.nhom21.english_learning_app.core.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TokenManagerTest {

    private lateinit var tokenManager: TokenManager

    @Before
    fun setUp() {
        val testDataStore = InMemoryPreferencesDataStore()
        tokenManager = TokenManager(testDataStore)
    }

    @Test
    fun saveToken_andReadOut_returnsCorrectToken() = runTest {
        tokenManager.saveToken("sample_jwt_token_123")
        val token = tokenManager.accessToken.first()
        assertEquals("sample_jwt_token_123", token)
    }

    @Test
    fun clearToken_andReadOut_returnsNull() = runTest {
        tokenManager.saveToken("sample_jwt_token_123")
        tokenManager.clearToken()
        val token = tokenManager.accessToken.first()
        assertNull(token)
    }

    @Test
    fun currentTokenBlocking_andClearTokenBlocking_workAsExpected() {
        tokenManager.clearTokenBlocking()
        assertNull(tokenManager.currentTokenBlocking())

        runTest {
            tokenManager.saveToken("blocking_jwt_token")
        }
        assertEquals("blocking_jwt_token", tokenManager.currentTokenBlocking())

        tokenManager.clearTokenBlocking()
        assertNull(tokenManager.currentTokenBlocking())
    }
}
