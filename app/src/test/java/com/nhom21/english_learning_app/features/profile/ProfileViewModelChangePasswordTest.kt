package com.nhom21.english_learning_app.features.profile

import androidx.lifecycle.viewModelScope
import com.nhom21.english_learning_app.core.network.ApiResponse
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.remote.api.ProfileApi
import com.nhom21.english_learning_app.data.remote.dto.AuthResultDto
import com.nhom21.english_learning_app.data.remote.dto.ProfileDto
import com.nhom21.english_learning_app.data.remote.dto.UpdateProfileRequest
import com.nhom21.english_learning_app.data.remote.dto.UserDto
import com.nhom21.english_learning_app.data.repository.AuthRepository
import com.nhom21.english_learning_app.data.repository.ChangePasswordResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelChangePasswordTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var viewModel: ProfileViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = FakeAuthRepository()
        viewModel = ProfileViewModel(FakeProfileApi(), authRepository)
        testScope.testScheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // 16. Client validation does not call repository and assigns error to the right field
    @Test
    fun `client validation rejects empty currentPassword and sets currentPasswordError without calling repository`() = testScope.runTest {
        viewModel.onOpenChangePasswordDialog()
        viewModel.onCurrentPasswordChange("")
        viewModel.onNewPasswordChange("ValidNew1")
        viewModel.onConfirmPasswordChange("ValidNew1")

        viewModel.submitChangePassword()
        advanceUntilIdle()

        assertEquals(0, authRepository.changePasswordCallCount)
        assertEquals(PasswordPolicy.ERROR_CURRENT_REQUIRED, viewModel.uiState.value.currentPasswordError)
        assertNull(viewModel.uiState.value.newPasswordError)
        assertNull(viewModel.uiState.value.confirmPasswordError)
    }

    @Test
    fun `client validation rejects invalid newPassword cases and sets newPasswordError without calling repository`() = testScope.runTest {
        viewModel.onOpenChangePasswordDialog()
        viewModel.onCurrentPasswordChange("OldPassword1")

        // 7 characters -> too short
        viewModel.onNewPasswordChange("Abcde12")
        viewModel.onConfirmPasswordChange("Abcde12")
        viewModel.submitChangePassword()
        assertEquals(PasswordPolicy.ERROR_NEW_TOO_SHORT, viewModel.uiState.value.newPasswordError)
        assertNull(viewModel.uiState.value.currentPasswordError)
        assertNull(viewModel.uiState.value.confirmPasswordError)

        // 65 characters -> too long
        val pass65 = "A1" + "b".repeat(63)
        viewModel.onNewPasswordChange(pass65)
        viewModel.onConfirmPasswordChange(pass65)
        viewModel.submitChangePassword()
        assertEquals(PasswordPolicy.ERROR_NEW_TOO_LONG, viewModel.uiState.value.newPasswordError)

        // No digit
        viewModel.onNewPasswordChange("OnlyLetters")
        viewModel.onConfirmPasswordChange("OnlyLetters")
        viewModel.submitChangePassword()
        assertEquals(PasswordPolicy.ERROR_NEW_COMPLEXITY, viewModel.uiState.value.newPasswordError)

        // No letter
        viewModel.onNewPasswordChange("12345678")
        viewModel.onConfirmPasswordChange("12345678")
        viewModel.submitChangePassword()
        assertEquals(PasswordPolicy.ERROR_NEW_COMPLEXITY, viewModel.uiState.value.newPasswordError)

        assertEquals(0, authRepository.changePasswordCallCount)
    }

    @Test
    fun `client validation rejects mismatched confirmPassword and sets confirmPasswordError without calling repository`() = testScope.runTest {
        viewModel.onOpenChangePasswordDialog()
        viewModel.onCurrentPasswordChange("OldPassword1")
        viewModel.onNewPasswordChange("ValidNew1")
        viewModel.onConfirmPasswordChange("Different1")

        viewModel.submitChangePassword()
        advanceUntilIdle()

        assertEquals(0, authRepository.changePasswordCallCount)
        assertNull(viewModel.uiState.value.currentPasswordError)
        assertNull(viewModel.uiState.value.newPasswordError)
        assertEquals(PasswordPolicy.ERROR_CONFIRM_MISMATCH, viewModel.uiState.value.confirmPasswordError)
    }

    // 17. Valid 8 chars, 64 chars, and 64 Unicode code points with non-BMP emoji call repository with exact raw values
    @Test
    fun `valid 8 char 64 char and 64 code-point non-BMP passwords call repository without trimming`() = testScope.runTest {
        // 8 ASCII chars
        val valid8 = "Abcdefg1"
        viewModel.onOpenChangePasswordDialog()
        viewModel.onCurrentPasswordChange("  OldPass1  ")
        viewModel.onNewPasswordChange(valid8)
        viewModel.onConfirmPasswordChange(valid8)
        viewModel.submitChangePassword()
        advanceUntilIdle()

        assertEquals(1, authRepository.changePasswordCallCount)
        assertEquals("  OldPass1  ", authRepository.lastCurrentPassword)
        assertEquals(valid8, authRepository.lastNewPassword)

        // 64 ASCII chars
        val valid64 = "A1" + "b".repeat(62)
        viewModel.onOpenChangePasswordDialog()
        viewModel.onCurrentPasswordChange("OldPass1")
        viewModel.onNewPasswordChange(valid64)
        viewModel.onConfirmPasswordChange(valid64)
        viewModel.submitChangePassword()
        advanceUntilIdle()

        assertEquals(2, authRepository.changePasswordCallCount)
        assertEquals(valid64, authRepository.lastNewPassword)

        // 64 Unicode code points with 62 non-BMP emoji (UTF-16 length 126)
        val emoji64 = "a1" + "\uD83D\uDE00".repeat(62)
        assertEquals(126, emoji64.length)
        viewModel.onOpenChangePasswordDialog()
        viewModel.onCurrentPasswordChange("OldPass1")
        viewModel.onNewPasswordChange(emoji64)
        viewModel.onConfirmPasswordChange(emoji64)
        viewModel.submitChangePassword()
        advanceUntilIdle()

        assertEquals(3, authRepository.changePasswordCallCount)
        assertEquals(emoji64, authRepository.lastNewPassword)
    }

    // 18. onCurrentPasswordChange / onNewPasswordChange / onConfirmPasswordChange update ProfileUiState
    @Test
    fun `password change handlers update corresponding fields in ProfileUiState and toString redacts them`() {
        viewModel.onCurrentPasswordChange("CurSecret1")
        viewModel.onNewPasswordChange("NewSecret2")
        viewModel.onConfirmPasswordChange("ConfirmSecret3")

        val state = viewModel.uiState.value
        assertEquals("CurSecret1", state.currentPasswordInput)
        assertEquals("CurSecret1", state.currentPassword)
        assertEquals("NewSecret2", state.newPasswordInput)
        assertEquals("NewSecret2", state.newPassword)
        assertEquals("ConfirmSecret3", state.confirmPasswordInput)
        assertEquals("ConfirmSecret3", state.confirmPassword)

        val stateString = state.toString()
        assertFalse(stateString.contains("CurSecret1"))
        assertFalse(stateString.contains("NewSecret2"))
        assertFalse(stateString.contains("ConfirmSecret3"))
    }

    // 19. Success closes dialog, clears 3 inputs and all errors, sets snackbar, resets isSaving = false
    @Test
    fun `submitChangePassword on Success closes dialog clears inputs and errors sets snackbar and resets isSaving`() = testScope.runTest {
        authRepository.changePasswordAction = { _, _ -> ChangePasswordResult.Success }
        viewModel.onOpenChangePasswordDialog()
        viewModel.onCurrentPasswordChange("OldPassword1")
        viewModel.onNewPasswordChange("NewPassword2")
        viewModel.onConfirmPasswordChange("NewPassword2")

        viewModel.submitChangePassword()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.showChangePasswordDialog)
        assertEquals("", state.currentPasswordInput)
        assertEquals("", state.newPasswordInput)
        assertEquals("", state.confirmPasswordInput)
        assertNull(state.currentPasswordError)
        assertNull(state.newPasswordError)
        assertNull(state.confirmPasswordError)
        assertNull(state.passwordError)
        assertEquals("Đã đổi mật khẩu thành công!", state.snackbarMessage)
        assertFalse(state.isSaving)
    }

    // 20. WrongCurrentPassword sets currentPasswordError, keeps dialog open and preserves 3 inputs, resets isSaving = false
    @Test
    fun `submitChangePassword on WrongCurrentPassword sets currentPasswordError keeps dialog open and preserves inputs`() = testScope.runTest {
        authRepository.changePasswordAction = { _, _ -> ChangePasswordResult.WrongCurrentPassword }
        viewModel.onOpenChangePasswordDialog()
        viewModel.onCurrentPasswordChange("WrongOld1")
        viewModel.onNewPasswordChange("NewPassword2")
        viewModel.onConfirmPasswordChange("NewPassword2")

        viewModel.submitChangePassword()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.showChangePasswordDialog)
        assertEquals(PasswordPolicy.ERROR_CURRENT_WRONG, state.currentPasswordError)
        assertNull(state.newPasswordError)
        assertNull(state.confirmPasswordError)
        assertNull(state.passwordError)
        assertEquals("WrongOld1", state.currentPasswordInput)
        assertEquals("NewPassword2", state.newPasswordInput)
        assertEquals("NewPassword2", state.confirmPasswordInput)
        assertFalse(state.isSaving)
    }

    // 21. ValidationError with known fields maps code to Vietnamese message (never raw server English) and falls back on unknown code
    @Test
    fun `submitChangePassword on ValidationError with known fields maps code to Vietnamese and falls back on unknown code`() = testScope.runTest {
        viewModel.onOpenChangePasswordDialog()
        viewModel.onCurrentPasswordChange("OldPassword1")
        viewModel.onNewPasswordChange("OldPassword1")
        viewModel.onConfirmPasswordChange("OldPassword1")

        // Known codes
        authRepository.changePasswordAction = { _, _ ->
            ChangePasswordResult.ValidationError(
                mapOf(
                    "currentPassword" to "MAX_LENGTH",
                    "newPassword" to "INVALID_FORMAT"
                )
            )
        }
        viewModel.submitChangePassword()
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(PasswordPolicy.ERROR_CURRENT_TOO_LONG, state.currentPasswordError)
        assertEquals(PasswordPolicy.ERROR_NEW_FORMAT_OR_SAME, state.newPasswordError)
        assertNull(state.passwordError)

        // Unknown codes for known fields -> Vietnamese field fallback
        authRepository.changePasswordAction = { _, _ ->
            ChangePasswordResult.ValidationError(
                mapOf(
                    "currentPassword" to "SOME_NEW_SERVER_CODE",
                    "newPassword" to "ANOTHER_UNKNOWN_CODE"
                )
            )
        }
        viewModel.submitChangePassword()
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(PasswordPolicy.ERROR_CURRENT_INVALID, state.currentPasswordError)
        assertEquals(PasswordPolicy.ERROR_NEW_INVALID, state.newPasswordError)
        assertNull(state.passwordError)
    }

    // 22. ValidationError with unknown field ("foo", ""), mixed known + unknown field, and empty fieldErrors
    @Test
    fun `submitChangePassword on ValidationError handles unknown fields mixed fields and empty fieldErrors`() = testScope.runTest {
        viewModel.onOpenChangePasswordDialog()
        viewModel.onCurrentPasswordChange("OldPassword1")
        viewModel.onNewPasswordChange("NewPassword2")
        viewModel.onConfirmPasswordChange("NewPassword2")

        // Case A: Only unknown field ("foo") -> general passwordError in Vietnamese
        authRepository.changePasswordAction = { _, _ ->
            ChangePasswordResult.ValidationError(mapOf("foo" to "INVALID_FORMAT"))
        }
        viewModel.submitChangePassword()
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertNull(state.currentPasswordError)
        assertNull(state.newPasswordError)
        assertEquals(PasswordPolicy.ERROR_VALIDATION_GENERAL, state.passwordError)

        // Case B: Empty field name ("") -> general passwordError in Vietnamese
        authRepository.changePasswordAction = { _, _ ->
            ChangePasswordResult.ValidationError(mapOf("" to "INVALID"))
        }
        viewModel.submitChangePassword()
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(PasswordPolicy.ERROR_VALIDATION_GENERAL, state.passwordError)

        // Case C: Mixed known + unknown field -> only field error shown, general passwordError is null
        authRepository.changePasswordAction = { _, _ ->
            ChangePasswordResult.ValidationError(
                mapOf(
                    "newPassword" to "MIN_LENGTH",
                    "foo" to "SOMETHING"
                )
            )
        }
        viewModel.submitChangePassword()
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(PasswordPolicy.ERROR_NEW_TOO_SHORT, state.newPasswordError)
        assertNull(state.currentPasswordError)
        assertNull(state.passwordError)

        // Case D: Empty fieldErrors map -> general passwordError in Vietnamese
        authRepository.changePasswordAction = { _, _ ->
            ChangePasswordResult.ValidationError(emptyMap())
        }
        viewModel.submitChangePassword()
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertNull(state.currentPasswordError)
        assertNull(state.newPasswordError)
        assertEquals(PasswordPolicy.ERROR_VALIDATION_GENERAL, state.passwordError)
    }

    // 23. NetworkError sets general passwordError and keeps dialog open
    @Test
    fun `submitChangePassword on NetworkError sets general passwordError and keeps dialog open`() = testScope.runTest {
        authRepository.changePasswordAction = { _, _ -> ChangePasswordResult.NetworkError }
        viewModel.onOpenChangePasswordDialog()
        viewModel.onCurrentPasswordChange("OldPassword1")
        viewModel.onNewPasswordChange("NewPassword2")
        viewModel.onConfirmPasswordChange("NewPassword2")

        viewModel.submitChangePassword()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.showChangePasswordDialog)
        assertEquals(PasswordPolicy.ERROR_NETWORK, state.passwordError)
        assertFalse(state.isSaving)
    }

    // 24. SessionExpired shows no error and resets isSaving = false
    @Test
    fun `submitChangePassword on SessionExpired shows no error and resets isSaving to false`() = testScope.runTest {
        authRepository.changePasswordAction = { _, _ -> ChangePasswordResult.SessionExpired }
        viewModel.onOpenChangePasswordDialog()
        viewModel.onCurrentPasswordChange("OldPassword1")
        viewModel.onNewPasswordChange("NewPassword2")
        viewModel.onConfirmPasswordChange("NewPassword2")

        viewModel.submitChangePassword()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.currentPasswordError)
        assertNull(state.newPasswordError)
        assertNull(state.confirmPasswordError)
        assertNull(state.passwordError)
        assertNull(state.snackbarMessage)
        assertFalse(state.isSaving)
    }

    // 25. Unexpected RuntimeException resets isSaving = false, sets generic Vietnamese passwordError without e.message, no snackbar
    @Test
    fun `submitChangePassword on unexpected RuntimeException resets isSaving and sets generic Vietnamese error without leaking exception message`() = testScope.runTest {
        authRepository.changePasswordAction = { _, _ -> throw RuntimeException("Sensitive internal details") }
        viewModel.onOpenChangePasswordDialog()
        viewModel.onCurrentPasswordChange("OldPassword1")
        viewModel.onNewPasswordChange("NewPassword2")
        viewModel.onConfirmPasswordChange("NewPassword2")

        viewModel.submitChangePassword()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSaving)
        assertTrue(state.showChangePasswordDialog)
        assertNotNull(state.passwordError)
        assertEquals(PasswordPolicy.ERROR_UNKNOWN, state.passwordError)
        assertFalse(state.passwordError!!.contains("Sensitive"))
        assertNull(state.snackbarMessage)
    }

    // 26. Coroutine cancellation in the middle does not swallow CancellationException into passwordError and finally resets isSaving = false
    @Test
    fun `submitChangePassword cancellation does not swallow CancellationException and finally resets isSaving to false`() = testScope.runTest {
        var caughtCancellation: CancellationException? = null
        authRepository.changePasswordAction = { _, _ ->
            try {
                awaitCancellation()
            } catch (e: CancellationException) {
                caughtCancellation = e
                throw e
            }
        }
        viewModel.onOpenChangePasswordDialog()
        viewModel.onCurrentPasswordChange("OldPassword1")
        viewModel.onNewPasswordChange("NewPassword2")
        viewModel.onConfirmPasswordChange("NewPassword2")

        viewModel.submitChangePassword()
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isSaving)

        viewModel.viewModelScope.cancel()
        testScheduler.runCurrent()

        assertNotNull(caughtCancellation)
        assertFalse(viewModel.uiState.value.isSaving)
        assertNull(viewModel.uiState.value.passwordError)
        assertNull(viewModel.uiState.value.snackbarMessage)
    }

    // 27. Calling submitChangePassword() twice consecutively calls repository only once
    @Test
    fun `consecutive submitChangePassword calls invoke repository only once while isSaving is true`() = testScope.runTest {
        val gate = CompletableDeferred<ChangePasswordResult>()
        authRepository.changePasswordAction = { _, _ -> gate.await() }
        viewModel.onOpenChangePasswordDialog()
        viewModel.onCurrentPasswordChange("OldPassword1")
        viewModel.onNewPasswordChange("NewPassword2")
        viewModel.onConfirmPasswordChange("NewPassword2")

        viewModel.submitChangePassword()
        viewModel.submitChangePassword()
        testScheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isSaving)
        assertEquals(1, authRepository.changePasswordCallCount)

        gate.complete(ChangePasswordResult.Success)
        advanceUntilIdle()

        assertEquals(1, authRepository.changePasswordCallCount)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    // 28. Open and close dialog resets 3 password fields and all errors; cannot close while isSaving is true
    @Test
    fun `open and close dialog resets inputs and errors and close is ignored while isSaving is true`() = testScope.runTest {
        val gate = CompletableDeferred<ChangePasswordResult>()
        authRepository.changePasswordAction = { _, _ -> gate.await() }

        viewModel.onOpenChangePasswordDialog()
        viewModel.onCurrentPasswordChange("OldPassword1")
        viewModel.onNewPasswordChange("short")
        viewModel.onConfirmPasswordChange("mismatch")
        viewModel.submitChangePassword()

        assertNotNull(viewModel.uiState.value.newPasswordError)
        assertNotNull(viewModel.uiState.value.confirmPasswordError)

        // Closing dialog resets all fields and errors
        viewModel.onCloseChangePasswordDialog()
        var state = viewModel.uiState.value
        assertFalse(state.showChangePasswordDialog)
        assertEquals("", state.currentPasswordInput)
        assertEquals("", state.newPasswordInput)
        assertEquals("", state.confirmPasswordInput)
        assertNull(state.currentPasswordError)
        assertNull(state.newPasswordError)
        assertNull(state.confirmPasswordError)
        assertNull(state.passwordError)

        // Re-opening dialog and starting save blocks closing while isSaving == true
        viewModel.onOpenChangePasswordDialog()
        viewModel.onCurrentPasswordChange("OldPassword1")
        viewModel.onNewPasswordChange("NewPassword2")
        viewModel.onConfirmPasswordChange("NewPassword2")
        viewModel.submitChangePassword()
        testScheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isSaving)
        viewModel.onCloseChangePasswordDialog()
        assertTrue(viewModel.uiState.value.showChangePasswordDialog)

        gate.complete(ChangePasswordResult.NetworkError)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSaving)

        // Now closing works and clears everything
        viewModel.onCloseChangePasswordDialog()
        state = viewModel.uiState.value
        assertFalse(state.showChangePasswordDialog)
        assertEquals("", state.currentPasswordInput)
        assertNull(state.passwordError)
    }

    // 29. Editing a field clears that field's error and general passwordError
    @Test
    fun `editing a field clears its own error and general passwordError without clearing other field errors`() = testScope.runTest {
        authRepository.changePasswordAction = { _, _ ->
            ChangePasswordResult.ValidationError(mapOf("unknownField" to "INVALID"))
        }
        viewModel.onOpenChangePasswordDialog()
        viewModel.onCurrentPasswordChange("OldPassword1")
        viewModel.onNewPasswordChange("NewPassword2")
        viewModel.onConfirmPasswordChange("NewPassword2")
        viewModel.submitChangePassword()
        advanceUntilIdle()
        assertEquals(PasswordPolicy.ERROR_VALIDATION_GENERAL, viewModel.uiState.value.passwordError)

        // Trigger all 3 field errors via client validation
        viewModel.onCurrentPasswordChange("")
        assertNull(viewModel.uiState.value.passwordError)
        viewModel.onNewPasswordChange("short")
        viewModel.onConfirmPasswordChange("different")
        viewModel.submitChangePassword()

        assertNotNull(viewModel.uiState.value.currentPasswordError)
        assertNotNull(viewModel.uiState.value.newPasswordError)
        assertNotNull(viewModel.uiState.value.confirmPasswordError)

        // Editing currentPassword clears only currentPasswordError
        viewModel.onCurrentPasswordChange("OldPassword1")
        assertNull(viewModel.uiState.value.currentPasswordError)
        assertNotNull(viewModel.uiState.value.newPasswordError)
        assertNotNull(viewModel.uiState.value.confirmPasswordError)

        // Editing newPassword clears only newPasswordError
        viewModel.onNewPasswordChange("NewPassword2")
        assertNull(viewModel.uiState.value.newPasswordError)
        assertNotNull(viewModel.uiState.value.confirmPasswordError)

        // Editing confirmPassword clears confirmPasswordError
        viewModel.onConfirmPasswordChange("NewPassword2")
        assertNull(viewModel.uiState.value.confirmPasswordError)
    }

    private class FakeAuthRepository : AuthRepository {
        var changePasswordCallCount = 0
            private set
        var lastCurrentPassword: String? = null
            private set
        var lastNewPassword: String? = null
            private set
        var changePasswordAction: suspend (String, String) -> ChangePasswordResult = { _, _ ->
            ChangePasswordResult.Success
        }

        override suspend fun changePassword(
            currentPassword: String,
            newPassword: String
        ): ChangePasswordResult {
            changePasswordCallCount++
            lastCurrentPassword = currentPassword
            lastNewPassword = newPassword
            return changePasswordAction(currentPassword, newPassword)
        }

        override suspend fun register(username: String, email: String, password: String): ApiResult<AuthResultDto> = error("Unused")
        override suspend fun login(identifier: String, password: String): ApiResult<AuthResultDto> = error("Unused")
        override suspend fun me(): ApiResult<UserDto> = error("Unused")
        override suspend fun logout() = Unit
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

