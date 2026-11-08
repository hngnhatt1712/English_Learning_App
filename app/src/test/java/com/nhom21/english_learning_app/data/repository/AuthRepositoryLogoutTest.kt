package com.nhom21.english_learning_app.data.repository

import com.nhom21.english_learning_app.core.network.ApiResponse
import com.nhom21.english_learning_app.core.network.ApiErrorParser
import com.nhom21.english_learning_app.core.network.TokenManager
import com.nhom21.english_learning_app.data.remote.api.AuthApi
import com.nhom21.english_learning_app.data.remote.dto.AuthResultDto
import com.nhom21.english_learning_app.data.remote.dto.ChangePasswordRequest
import com.nhom21.english_learning_app.data.remote.dto.LoginRequest
import com.nhom21.english_learning_app.data.remote.dto.LogoutRequest
import com.nhom21.english_learning_app.data.remote.dto.RegisterRequest
import com.nhom21.english_learning_app.data.remote.dto.UserDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryLogoutTest {

    @Test
    fun `logout success sends null fcm token and clears local token once`() = runTest {
        val fixture = Fixture()
        fixture.tokenManager.saveToken("local-token")
        fixture.dataStore.resetUpdateCount()

        fixture.repository.logout()

        assertEquals(1, fixture.authApi.logoutCallCount)
        assertNull(fixture.authApi.lastLogoutRequest?.fcmToken)
        assertEquals(1, fixture.dataStore.updateCount)
        assertNull(fixture.tokenManager.accessToken.first())
    }

    @Test
    fun `logout IOException is best effort and clears local token`() = runTest {
        val fixture = Fixture { throw IOException("offline") }
        fixture.tokenManager.saveToken("local-token")
        fixture.dataStore.resetUpdateCount()

        fixture.repository.logout()

        assertEquals(1, fixture.authApi.logoutCallCount)
        assertEquals(1, fixture.dataStore.updateCount)
        assertNull(fixture.tokenManager.accessToken.first())
    }

    @Test
    fun `logout unauthorized response is best effort and clears local token`() = runTest {
        val fixture = Fixture {
            throw HttpException(Response.error<ApiResponse<Unit?>>(401, "Unauthorized".toResponseBody()))
        }
        fixture.tokenManager.saveToken("local-token")
        fixture.dataStore.resetUpdateCount()

        fixture.repository.logout()

        assertEquals(1, fixture.authApi.logoutCallCount)
        assertEquals(1, fixture.dataStore.updateCount)
        assertNull(fixture.tokenManager.accessToken.first())
    }

    @Test
    fun `logout timeout after five virtual seconds clears local token`() = runTest {
        val fixture = Fixture { delay(6_000); successResponse() }
        fixture.tokenManager.saveToken("local-token")
        fixture.dataStore.resetUpdateCount()
        val logout = async { fixture.repository.logout() }

        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        logout.await()

        assertEquals(1, fixture.authApi.logoutCallCount)
        assertEquals(1, fixture.dataStore.updateCount)
        assertNull(fixture.tokenManager.accessToken.first())
    }

    @Test
    fun `external cancellation propagates and still clears local token`() = runTest {
        val fixture = Fixture { kotlinx.coroutines.awaitCancellation() }
        fixture.tokenManager.saveToken("local-token")
        fixture.dataStore.resetUpdateCount()
        val logout = async { fixture.repository.logout() }
        runCurrent()

        logout.cancelAndJoin()

        assertEquals(1, fixture.authApi.logoutCallCount)
        assertEquals(1, fixture.dataStore.updateCount)
        assertNull(fixture.tokenManager.accessToken.first())
        try {
            logout.await()
            fail("CancellationException should propagate")
        } catch (_: CancellationException) {
            // Expected.
        }
    }

    private class Fixture(
        logoutCall: suspend () -> ApiResponse<Unit?> = { successResponse() }
    ) {
        val dataStore = CountingPreferencesDataStore()
        val tokenManager = TokenManager(dataStore)
        val authApi = FakeAuthApi(logoutCall)
        val repository = AuthRepositoryImpl(authApi, tokenManager, ApiErrorParser(Json))
    }

    private class CountingPreferencesDataStore : DataStore<Preferences> {
        private val backingStore = com.nhom21.english_learning_app.core.network.InMemoryPreferencesDataStore()
        override val data: Flow<Preferences> = backingStore.data
        var updateCount = 0
            private set

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences
        ): Preferences {
            updateCount++
            return backingStore.updateData(transform)
        }

        fun resetUpdateCount() { updateCount = 0 }
    }

    private class FakeAuthApi(
        private val logoutCall: suspend () -> ApiResponse<Unit?>
    ) : AuthApi {
        var logoutCallCount = 0
            private set
        var lastLogoutRequest: LogoutRequest? = null
            private set

        override suspend fun register(request: RegisterRequest): ApiResponse<AuthResultDto> = error("Unused")
        override suspend fun login(request: LoginRequest): ApiResponse<AuthResultDto> = error("Unused")
        override suspend fun me(): ApiResponse<UserDto> = error("Unused")
        override suspend fun changePassword(request: ChangePasswordRequest): ApiResponse<AuthResultDto> = error("Unused")
        override suspend fun logout(request: LogoutRequest): ApiResponse<Unit?> {
            logoutCallCount++
            lastLogoutRequest = request
            return logoutCall()
        }
    }
}

private fun successResponse() = ApiResponse<Unit?>(status = true, message = "OK", data = null)
