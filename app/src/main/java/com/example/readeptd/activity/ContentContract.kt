package com.example.readeptd.activity

import com.example.readeptd.data.FileInfo

/**
 * 内容页面 UI 状态密封类
 */
sealed interface ContentUiState {
    object Loading : ContentUiState
    data class Success(val fileInfo: FileInfo) : ContentUiState
    data class Error(val error: String) : ContentUiState
}

/**
 * 内容页面 UI 事件密封类
 */
sealed interface ContentUiEvent {
    data class Initialize(val fileInfo: FileInfo?) : ContentUiEvent
    data class OnClickProgressInfo(val progressText: String): ContentUiEvent
    object OnDoubleClickScreen : ContentUiEvent
    data  class OnScreenOrientationChanged(val orientation: Int): ContentUiEvent
}
