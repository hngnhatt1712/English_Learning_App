package com.nhom21.english_learning_app.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhom21.english_learning_app.core.network.ApiResult
import com.nhom21.english_learning_app.data.repository.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel quản lý dữ liệu và hành động trên màn hình Home (HomeScreen).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        fetchHomeData()
    }

    /**
     * Tải dữ liệu dashboard tổng quan từ backend GET /api/v1/home
     */
    fun fetchHomeData() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = homeRepository.getHomeData()) {
                is ApiResult.Success -> {
                    val data = result.data
                    val currentTopic = data.currentTopic
                    val level = data.level
                    val streak = data.streak

                    val totalRoundsCount = currentTopic?.roundsTotal ?: 5
                    val currentRoundNum = currentTopic?.round ?: 3
                    val progressRatio = if (totalRoundsCount > 0) {
                        (currentRoundNum - 1).toFloat() / totalRoundsCount.toFloat()
                    } else {
                        0.6f
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            username = data.username.ifBlank { "User" },
                            levelName = level?.name ?: "Level 1",
                            totalStars = if (data.totalStars > 0) data.totalStars else (level?.stars ?: 18),
                            maxStars = if (data.maxStars > 0) data.maxStars else (level?.maxStars ?: 135),
                            currentStreak = streak?.current ?: 5,
                            learnedToday = streak?.learnedToday ?: true,
                            currentTopicName = currentTopic?.name ?: "Food and drinks",
                            topicOrder = currentTopic?.topicOrder ?: 2,
                            currentRound = currentRoundNum,
                            totalRounds = totalRoundsCount,
                            progressPercent = if ((currentTopic?.progressPercent != null) && (currentTopic.progressPercent > 0f)) {
                                currentTopic.progressPercent / 100f
                            } else {
                                progressRatio
                            },
                            flashcardCount = data.flashcardCount.coerceAtLeast(24),
                            reviewCount = data.reviewCount.coerceAtLeast(8),
                            hasPracticeNotification = (data.reviewCount) > 0,
                            errorMessage = null
                        )
                    }
                }

                is ApiResult.Failure -> {
                    // Trong trường hợp offline/chưa kết nối server, giữ dữ liệu mẫu khớp thiết kế UI
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.error.message
                        )
                    }
                }
            }
        }
    }

    /**
     * Chuyển tab trên thanh Bottom Navigation Bar
     */
    fun onTabSelected(tab: HomeTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /**
     * Làm mới dữ liệu
     */
    fun refresh() {
        fetchHomeData()
    }
}
