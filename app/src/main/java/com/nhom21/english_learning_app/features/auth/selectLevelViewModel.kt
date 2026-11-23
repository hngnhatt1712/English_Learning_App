package com.nhom21.english_learning_app.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.domain.usecase.GetLevelsUseCase
import com.nhom21.english_learning_app.domain.usecase.SelectLevelUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SelectLevelEvent {
    data class SelectionSaved(val levelId: String) : SelectLevelEvent
}

@HiltViewModel
class SelectLevelViewModel @Inject constructor(
    private val getLevels: GetLevelsUseCase,
    private val selectLevel: SelectLevelUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(SelectLevelUiState())
    val uiState = _uiState.asStateFlow()

    private val eventChannel = Channel<SelectLevelEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        loadLevels()
    }

    fun loadLevels() {
        val current = _uiState.value
        if (current.isLoading || current.isSubmitting || current.isSelectionSaved) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null, errorType = null, isLoadError = false) }
        viewModelScope.launch {
            when (val result = getLevels()) {
                is ApiResult.Success -> _uiState.update { state ->
                    state.copy(
                        levels = result.data,
                        selectedLevelId = state.selectedLevelId?.takeIf { id -> result.data.any { it.id == id } },
                        isLoading = false
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorType = result.error.type, errorMessage = errorMessage(result.error.type), isLoadError = true)
                }
            }
        }
    }

    fun onLevelSelect(levelId: String) {
        val current = _uiState.value
        if (current.isLoading || current.isSubmitting || current.isSelectionSaved || current.levels.none { it.id == levelId }) return
        _uiState.update { it.copy(selectedLevelId = levelId, errorMessage = null, errorType = null, isLoadError = false) }
    }

    fun onContinueClick() {
        val current = _uiState.value
        if (!current.canContinue) return
        val levelId = current.selectedLevelId ?: return
        // Set synchronously before launching, including when the dispatcher is busy.
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null, errorType = null, isLoadError = false) }
        viewModelScope.launch {
            when (val result = selectLevel(levelId)) {
                is ApiResult.Success -> {
                    val confirmedId = result.data.level?.id
                    if (confirmedId == levelId) {
                        _uiState.update { it.copy(isSubmitting = false, isSelectionSaved = true, selectedLevelId = confirmedId) }
                        eventChannel.send(SelectLevelEvent.SelectionSaved(confirmedId))
                    } else {
                        _uiState.update { it.copy(isSubmitting = false, errorType = ApiErrorType.UNKNOWN, errorMessage = ERROR_CONFIRMATION) }
                    }
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isSubmitting = false, errorType = result.error.type, errorMessage = errorMessage(result.error.type))
                }
            }
        }
    }

    fun onRetry() {
        val current = _uiState.value
        if (current.isLoadError || current.levels.isEmpty() || current.errorType == ApiErrorType.NOT_FOUND) {
            loadLevels()
        } else {
            onContinueClick()
        }
    }

    private fun errorMessage(type: ApiErrorType): String = when (type) {
        ApiErrorType.UNAUTHORIZED -> "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        ApiErrorType.LEVEL_LOCKED -> "Trình độ này chưa được mở khóa. Vui lòng chọn trình độ khác."
        ApiErrorType.FORBIDDEN -> "Bạn chưa có quyền chọn trình độ này."
        ApiErrorType.NOT_FOUND -> "Không tìm thấy trình độ. Vui lòng tải lại danh sách."
        ApiErrorType.VALIDATION_ERROR -> "Lựa chọn trình độ không hợp lệ. Vui lòng chọn lại và thử lại."
        ApiErrorType.NETWORK -> "Không kết nối được máy chủ. Vui lòng thử lại."
        else -> "Không thể hoàn tất yêu cầu. Vui lòng thử lại."
    }

    private companion object {
        const val ERROR_CONFIRMATION = "Máy chủ chưa xác nhận trình độ đã chọn. Vui lòng thử lại."
    }
}
