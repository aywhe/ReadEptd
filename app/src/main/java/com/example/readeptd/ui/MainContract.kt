package com.example.readeptd.ui

/**
 * UI 状态密封类 - 代表屏幕的不同状态
 */
sealed interface MainUiState {
    object Loading : MainUiState
    data class Success(val message: String) : MainUiState
    data class Error(val error: String) : MainUiState
}

/**
 * UI 事件密封类 - 用户交互触发的事件
 */
sealed interface MainUiEvent {
    data class UpdateGreeting(val name: String) : MainUiEvent
    object Refresh : MainUiEvent
}
