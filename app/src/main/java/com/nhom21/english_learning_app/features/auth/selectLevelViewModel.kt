package com.nhom21.english_learning_app.features.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel quản lý logic và trạng thái UI cho màn hình Chọn trình độ (SelectLevelScreen).
 */
@HiltViewModel
class SelectLevelViewModel @Inject constructor() : ViewModel() {

    var uiState by mutableStateOf(SelectLevelUiState())
        private set

    fun onLevelSelect(code: String) {
        uiState = uiState.copy(selectedLevel = code, errorMessage = null)
    }
}
