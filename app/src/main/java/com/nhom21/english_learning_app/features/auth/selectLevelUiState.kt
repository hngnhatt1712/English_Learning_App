package com.nhom21.english_learning_app.features.auth

import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.domain.model.Level

data class SelectLevelUiState(
    val levels: List<Level> = emptyList(),
    val selectedLevelId: String? = null,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val errorType: ApiErrorType? = null,
    val isLoadError: Boolean = false,
    val isSelectionSaved: Boolean = false
) {
    val canContinue: Boolean
        get() = !isLoading && !isSubmitting && !isSelectionSaved &&
            levels.any { it.id == selectedLevelId }
}
