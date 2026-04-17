package com.example.readeptd.ui

import android.net.Uri

/**
 * 文件信息数据类
 */
data class FileInfo(
    val uri: Uri,
    val fileName: String,
    val fileSize: Long = 0,
    val mimeType: String = ""
)

/**
 * UI 状态密封类 - 代表屏幕的不同状态
 */
sealed interface MainUiState {
    object Loading : MainUiState
    data class Success(
        val message: String,
        val selectedFiles: List<FileInfo> = emptyList()
    ) : MainUiState
    data class Error(val error: String) : MainUiState
}

/**
 * UI 事件密封类 - 用户交互触发的事件
 */
sealed interface MainUiEvent {
    data class UpdateGreeting(val name: String) : MainUiEvent
    object Refresh : MainUiEvent
    object OnButtonClick : MainUiEvent
    data class OnFilesSelected(val files: List<FileInfo>) : MainUiEvent
}
