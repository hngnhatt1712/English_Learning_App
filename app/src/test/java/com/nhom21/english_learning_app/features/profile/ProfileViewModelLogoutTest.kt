package com.nhom21.english_learning_app.features.profile

import com.nhom21.english_learning_app.core.network.ApiResponse
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.remote.api.ProfileApi
import com.nhom21.english_learning_app.data.remote.dto.AuthResultDto
import com.nhom21.english_learning_app.data.remote.dto.LoginRequest
import com.nhom21.english_learning_app.data.remote.dto.ProfileDto
import com.nhom21.english_learning_app.data.remote.dto.UpdateProfileRequest
import com.nhom21.english_learning_app.data.remote.dto.UserDto
import com.nhom21.english_learning_app.data.repository.AuthRepository
import com.nhom21.english_learning_app.data.repository.ChangePasswordResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelLogoutTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = kotlinx.coroutines.test.TestScope(testDispatcher)
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var viewModel: ProfileViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = FakeAuthRepository()
        viewModel = ProfileViewModel(FakeProfileApi(), authRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `consecutive logout calls invoke repository and callback once`() = testScope.runTest {
        viewModel.onOpenLogoutDialog()
        var callbackCount = 0

        viewModel.logout { callbackCount++ }
        viewModel.logout { callbackCount++ }
        assertTrue(viewModel.uiState.value.isLoggingOut)
        advanceUntilIdle()

        assertEquals(1, authRepository.logoutCallCount)
        assertEquals(1, callbackCount)
        assertFalse(viewModel.uiState.value.isLoggingOut)
        assertFalse(viewModel.uiState.value.showLogoutDialog)
    }

    @Test
    fun `close request is ignored while logout is running`() = testScope.runTest {
        val gate = CompletableDeferred<Unit>()
        authRepository.logoutAction = { gate.await() }
        viewModel.onOpenLogoutDialog()
        var callbackCount = 0

        viewModel.logout { callbackCount++ }
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isLoggingOut)
        viewModel.onCloseLogoutDialog()

        assertTrue(viewModel.uiState.value.showLogoutDialog)
        assertEquals(1, authRepository.logoutCallCount)
        assertEquals(0, callbackCount)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showLogoutDialog)
        assertFalse(viewModel.uiState.value.isLoggingOut)
        assertEquals(1, callbackCount)
    }

    @Test
    fun `logout completion resets dialog state and invokes callback`() = testScope.runTest {
        viewModel.onOpenLogoutDialog()
        var callbackCount = 0

        viewModel.logout { callbackCount++ }
        advanceUntilIdle()

        assertEquals(1, authRepository.logoutCallCount)
        assertEquals(1, callbackCount)
        assertFalse(viewModel.uiState.value.isLoggingOut)
        assertFalse(viewModel.uiState.value.showLogoutDialog)
    }

    @Test
    fun `unexpected repository error still resets state and navigates once`() = testScope.runTest {
        authRepository.logoutAction = { throw IllegalStateException("unexpected") }
        viewModel.onOpenLogoutDialog()
        var callbackCount = 0

        viewModel.logout { callbackCount++ }
        advanceUntilIdle()

        assertEquals(1, authRepository.logoutCallCount)
        assertEquals(1, callbackCount)
        assertFalse(viewModel.uiState.value.isLoggingOut)
        assertFalse(viewModel.uiState.value.showLogoutDialog)
    }

    private class FakeAuthRepository : AuthRepository {
        var logoutCallCount = 0
            private set
        var logoutAction: suspend () -> Unit = {}

        override suspend fun logout() {
            logoutCallCount++
            logoutAction()
        }

        override suspend fun register(username: String, email: String, password: String): ApiResult<AuthResultDto> = error("Unused")
        override suspend fun login(identifier: String, password: String): ApiResult<AuthResultDto> = error("Unused")
        override suspend fun me(): ApiResult<UserDto> = error("Unused")
        override suspend fun changePassword(currentPassword: String, newPassword: String): ChangePasswordResult = error("Unused")
    }

    private class FakeProfileApi : ProfileApi {
        override suspend fun getProfile() = ApiResponse(
            status = true,
            message = "OK",
            data = ProfileDto(
                id = "profile-id",
                username = "tester",
                email = "test@example.com",
                level = null,
                totalStars = 0,
                maxStars = 135,
                currentStreak = 0,
                createdAt = "2026-01-01T00:00:00Z"
            )
        )

        override suspend fun updateProfile(request: UpdateProfileRequest): ApiResponse<ProfileDto> = error("Unused")
    }
}
