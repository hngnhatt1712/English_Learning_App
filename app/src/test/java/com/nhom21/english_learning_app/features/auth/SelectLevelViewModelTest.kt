package com.nhom21.english_learning_app.features.auth

import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiFailure
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.core.navigation.AppNavTransitions
import com.nhom21.english_learning_app.core.navigation.SelectLevel
import com.nhom21.english_learning_app.core.navigation.WelcomeSuccess
import com.nhom21.english_learning_app.data.repository.LevelRepository
import com.nhom21.english_learning_app.data.repository.ProfileRepository
import com.nhom21.english_learning_app.domain.model.Level
import com.nhom21.english_learning_app.domain.model.Profile
import com.nhom21.english_learning_app.domain.model.ProfileLevel
import com.nhom21.english_learning_app.domain.usecase.GetLevelsUseCase
import com.nhom21.english_learning_app.domain.usecase.SelectLevelUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SelectLevelViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val firstId = "10000000-0000-0000-0000-000000000001"
    private val secondId = "10000000-0000-0000-0000-000000000002"
    private val thirdId = "10000000-0000-0000-0000-000000000003"
    private val levels = listOf(
        Level(thirdId, "Server advanced", null, 2),
        Level(secondId, "Server intermediate", "Server description", 2),
        Level(firstId, "Server beginner", "First", 1)
    )
    private lateinit var levelRepository: FakeLevels
    private lateinit var profileRepository: FakeProfile

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        levelRepository = FakeLevels(ApiResult.Success(levels))
        profileRepository = FakeProfile(ApiResult.Success(profile(firstId)))
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun viewModel() = SelectLevelViewModel(GetLevelsUseCase(levelRepository), SelectLevelUseCase(profileRepository))

    @Test
    fun `initial load is guarded and renders sorted backend names descriptions and UUIDs`() = runTest {
        val vm = viewModel()
        assertTrue(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.canContinue)
        vm.loadLevels()
        vm.onContinueClick()
        advanceUntilIdle()
        assertEquals(1, levelRepository.calls)
        assertEquals(listOf(firstId, secondId, thirdId), vm.uiState.value.levels.map { it.id })
        assertEquals("Server intermediate", vm.uiState.value.levels[1].name)
        assertEquals("Server description", vm.uiState.value.levels[1].description)
        assertNull(vm.uiState.value.levels[2].description)
        assertNull(vm.uiState.value.selectedLevelId)
        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.canContinue)
        assertEquals(0, profileRepository.calls)
    }

    @Test
    fun `any number of levels is preserved without fixed indices`() = runTest {
        levelRepository.result = ApiResult.Success((1..8).map { Level("00000000-0000-0000-0000-%012d".format(it), "Level $it", null, it) })
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(8, vm.uiState.value.levels.size)
        vm.onLevelSelect(vm.uiState.value.levels.last().id)
        assertTrue(vm.uiState.value.canContinue)
    }

    @Test
    fun `empty response permits reload and never submits`() = runTest {
        levelRepository.result = ApiResult.Success(emptyList())
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.levels.isEmpty())
        assertFalse(vm.uiState.value.isLoading)
        vm.onContinueClick()
        assertEquals(0, profileRepository.calls)
        levelRepository.result = ApiResult.Success(levels)
        vm.onRetry()
        advanceUntilIdle()
        assertEquals(2, levelRepository.calls)
        assertEquals(3, vm.uiState.value.levels.size)
    }

    @Test
    fun `load failure shows safe retryable error and retry succeeds`() = runTest {
        levelRepository.result = failure(ApiErrorType.NETWORK)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(ApiErrorType.NETWORK, vm.uiState.value.errorType)
        assertNotNull(vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.errorMessage!!.contains("internal trace"))
        assertFalse(vm.uiState.value.isLoading)
        levelRepository.result = ApiResult.Success(levels)
        vm.onRetry()
        advanceUntilIdle()
        assertNull(vm.uiState.value.errorMessage)
        assertEquals(2, levelRepository.calls)
    }

    @Test
    fun `selecting stores actual ID without submitting and rejects unknown IDs`() = runTest {
        val vm = viewModel()
        vm.onLevelSelect(firstId)
        assertNull(vm.uiState.value.selectedLevelId)
        advanceUntilIdle()
        vm.onLevelSelect(secondId)
        vm.onLevelSelect("A1")
        assertEquals(secondId, vm.uiState.value.selectedLevelId)
        assertTrue(vm.uiState.value.canContinue)
        assertEquals(0, profileRepository.calls)
    }

    @Test
    fun `continue submits actual UUID once and freezes selection during request`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onLevelSelect(firstId)
        profileRepository.pending = CompletableDeferred()
        vm.onContinueClick()
        vm.onContinueClick()
        assertTrue(vm.uiState.value.isSubmitting)
        assertFalse(vm.uiState.value.canContinue)
        vm.onLevelSelect(secondId)
        vm.loadLevels()
        runCurrent()
        assertEquals(1, profileRepository.calls)
        assertEquals(firstId, profileRepository.lastId)
        assertEquals(firstId, vm.uiState.value.selectedLevelId)
        assertEquals(1, levelRepository.calls)
        profileRepository.pending!!.complete(ApiResult.Success(profile(firstId)))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isSelectionSaved)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun `confirmed response updates state and buffered event navigates only once across collectors`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onLevelSelect(secondId)
        profileRepository.result = ApiResult.Success(profile(secondId))
        vm.onContinueClick()
        advanceUntilIdle() // No collector yet, like a temporarily stopped route.
        val events = mutableListOf<SelectLevelEvent>()
        var collector = backgroundScope.launch(dispatcher) { vm.events.collect { events += it } }
        runCurrent()
        assertEquals(listOf(SelectLevelEvent.SelectionSaved(secondId)), events)
        collector.cancel()
        collector = backgroundScope.launch(dispatcher) { vm.events.collect { events += it } }
        vm.onContinueClick()
        vm.onRetry()
        runCurrent()
        assertEquals(1, events.size)
        assertEquals(1, profileRepository.calls)
        assertEquals(secondId, vm.uiState.value.selectedLevelId)
        assertTrue(vm.uiState.value.isSelectionSaved)
        assertFalse(vm.uiState.value.canContinue)
        val stack = AppNavTransitions.applyToBackStack(listOf(SelectLevel), AppNavTransitions.onSelectLevelSuccess())
        assertEquals(listOf(WelcomeSuccess), stack)
        collector.cancel()
    }

    @Test
    fun `HTTP success without matching profile confirmation cannot navigate`() = runTest {
        for (confirmedId in listOf(null, secondId)) {
            val vm = viewModel()
            advanceUntilIdle()
            val events = mutableListOf<SelectLevelEvent>()
            val collector = backgroundScope.launch(dispatcher) { vm.events.collect { events += it } }
            vm.onLevelSelect(firstId)
            profileRepository.result = ApiResult.Success(profile(confirmedId))
            vm.onContinueClick()
            runCurrent()
            assertTrue(events.isEmpty())
            assertFalse(vm.uiState.value.isSelectionSaved)
            assertNotNull(vm.uiState.value.errorMessage)
            collector.cancel()
        }
    }

    @Test
    fun `all submission errors stay on screen with safe message and retry enabled`() = runTest {
        for (type in listOf(ApiErrorType.UNAUTHORIZED, ApiErrorType.LEVEL_LOCKED, ApiErrorType.FORBIDDEN,
            ApiErrorType.NOT_FOUND, ApiErrorType.VALIDATION_ERROR, ApiErrorType.NETWORK,
            ApiErrorType.INTERNAL_ERROR, ApiErrorType.UNKNOWN, ApiErrorType.INVALID_CREDENTIALS)) {
            val vm = viewModel()
            advanceUntilIdle()
            val events = mutableListOf<SelectLevelEvent>()
            val collector = backgroundScope.launch(dispatcher) { vm.events.collect { events += it } }
            profileRepository.result = failure(type)
            vm.onLevelSelect(firstId)
            vm.onContinueClick()
            advanceUntilIdle()
            assertTrue(events.isEmpty())
            assertEquals(type, vm.uiState.value.errorType)
            assertNotNull(vm.uiState.value.errorMessage)
            assertFalse(vm.uiState.value.errorMessage!!.contains("internal trace"))
            assertFalse(vm.uiState.value.isSubmitting)
            assertFalse(vm.uiState.value.isSelectionSaved)
            assertTrue(vm.uiState.value.canContinue)
            vm.onLevelSelect(secondId)
            assertNull(vm.uiState.value.errorMessage)
            collector.cancel()
        }
    }

    @Test
    fun `retry failed submission succeeds with same UUID`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onLevelSelect(firstId)
        profileRepository.result = failure(ApiErrorType.NETWORK)
        vm.onContinueClick()
        advanceUntilIdle()
        profileRepository.result = ApiResult.Success(profile(firstId))
        vm.onRetry()
        advanceUntilIdle()
        assertEquals(2, profileRepository.calls)
        assertEquals(firstId, profileRepository.lastId)
        assertTrue(vm.uiState.value.isSelectionSaved)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `not found retry refreshes list and drops removed selection`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onLevelSelect(firstId)
        profileRepository.result = failure(ApiErrorType.NOT_FOUND)
        vm.onContinueClick()
        advanceUntilIdle()
        levelRepository.result = ApiResult.Success(levels.filterNot { it.id == firstId })
        vm.onRetry()
        advanceUntilIdle()
        assertEquals(2, levelRepository.calls)
        assertEquals(1, profileRepository.calls)
        assertNull(vm.uiState.value.selectedLevelId)
        assertFalse(vm.uiState.value.canContinue)
    }

    @Test
    fun `retry refresh network failure keeps retrying GET instead of resubmitting stale selection`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onLevelSelect(firstId)
        profileRepository.result = failure(ApiErrorType.NOT_FOUND)
        vm.onContinueClick()
        advanceUntilIdle()
        levelRepository.result = failure(ApiErrorType.NETWORK)
        vm.onRetry()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isLoadError)
        levelRepository.result = ApiResult.Success(levels)
        vm.onRetry()
        advanceUntilIdle()
        assertEquals(3, levelRepository.calls)
        assertEquals(1, profileRepository.calls)
        assertFalse(vm.uiState.value.isLoadError)
        assertNull(vm.uiState.value.errorMessage)
    }

    private fun failure(type: ApiErrorType) = ApiResult.Failure(ApiFailure(type, "internal trace must not be displayed"))
    private fun profile(levelId: String?) = Profile("user", "learner", "learner@example.test",
        levelId?.let { ProfileLevel(it, "Backend confirmed", 0, 45, 0.0) }, 0, 135, 0, "2026-11-13T00:00:00Z")

    private class FakeLevels(var result: ApiResult<List<Level>>) : LevelRepository {
        var calls = 0
        override suspend fun getLevels(): ApiResult<List<Level>> { calls++; return result }
    }

    private class FakeProfile(var result: ApiResult<Profile>) : ProfileRepository {
        var calls = 0
        var lastId: String? = null
        var pending: CompletableDeferred<ApiResult<Profile>>? = null
        override suspend fun selectLevel(levelId: String): ApiResult<Profile> {
            calls++
            lastId = levelId
            return pending?.await() ?: result
        }
    }
}
